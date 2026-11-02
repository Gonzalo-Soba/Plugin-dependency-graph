package dev.taskgraph.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/**
 * Grid layout for the advancements screen. The column is the longest dependency path, so every
 * prerequisite is drawn to the left of the tasks that need it.
 * <p>
 * Tasks that are not connected to each other go in separate blocks, stacked top to bottom in the
 * creation order of their first task, with an empty row between blocks. Inside a block, each task
 * aims for the average row of its dependencies (its barycenter), and a few sweeps back and forth also
 * pull dependencies towards the tasks that need them, which keeps lines short and reduces crossings.
 * Rows may skip numbers to keep a task next to its dependencies, but two tasks never share a cell.
 */
public final class GraphLayout {

    // Fixed number of up and down passes: enough to settle small graphs and keeps the result deterministic.
    private static final int SWEEP_ROUNDS = 4;
    private static final int ROWS_BETWEEN_BLOCKS = 1;

    private GraphLayout() {
    }

    /** Positions of every task, keyed by task id and iterated in topological order. */
    public static Map<String, NodePosition> compute(TaskGraph graph) {
        Set<String> all = new HashSet<>();
        for (Task task : graph.tasks()) {
            all.add(task.id());
        }
        return compute(graph, all);
    }

    /**
     * Positions of the given tasks only, as if the rest of the graph did not exist: dependencies on
     * tasks outside {@code taskIds} are ignored for columns, rows and blocks.
     *
     * @return positions keyed by task id, iterated in topological order
     */
    public static Map<String, NodePosition> compute(TaskGraph graph, Set<String> taskIds) {
        List<Task> order = graph.topologicalOrder().stream()
                .filter(task -> taskIds.contains(task.id()))
                .toList();
        Subgraph subgraph = new Subgraph(order, taskIds);

        Map<String, NodePosition> positions = new HashMap<>();
        int firstRow = 0;
        for (List<String> block : subgraph.blocks(graph)) {
            Map<String, Integer> rows = layoutBlock(block, subgraph);
            int height = 0;
            for (String id : block) {
                int row = rows.get(id);
                positions.put(id, new NodePosition(subgraph.columns.get(id), firstRow + row));
                height = Math.max(height, row + 1);
            }
            firstRow += height + ROWS_BETWEEN_BLOCKS;
        }

        Map<String, NodePosition> ordered = new LinkedHashMap<>();
        for (Task task : order) {
            ordered.put(task.id(), positions.get(task.id()));
        }
        return Collections.unmodifiableMap(ordered);
    }

    /** Rows of one block, starting at 0. {@code block} is in topological order. */
    private static Map<String, Integer> layoutBlock(List<String> block, Subgraph subgraph) {
        Map<Integer, List<String>> byColumn = new TreeMap<>();
        for (String id : block) {
            byColumn.computeIfAbsent(subgraph.columns.get(id), key -> new ArrayList<>()).add(id);
        }
        List<List<String>> columns = new ArrayList<>(byColumn.values());
        List<List<String>> reversed = new ArrayList<>(columns);
        Collections.reverse(reversed);

        Map<String, Integer> rows = new HashMap<>();
        // First column starts in topological order; every later column follows its dependencies.
        List<String> first = columns.getFirst();
        for (int index = 0; index < first.size(); index++) {
            rows.put(first.get(index), index);
        }
        sweep(columns, rows, subgraph, subgraph.dependencies);
        for (int round = 0; round < SWEEP_ROUNDS; round++) {
            sweep(reversed, rows, subgraph, subgraph.dependents);
            sweep(columns, rows, subgraph, subgraph.dependencies);
        }

        int minRow = Collections.min(rows.values());
        rows.replaceAll((id, row) -> row - minRow);
        return rows;
    }

    /**
     * Places each column, in the given order, at the average row of the neighbours in {@code links}.
     * A task without such neighbours keeps its current row.
     */
    private static void sweep(List<List<String>> columns, Map<String, Integer> rows, Subgraph subgraph,
                              Map<String, List<String>> links) {
        for (List<String> column : columns) {
            place(column, rows, subgraph, id -> {
                List<String> neighbours = links.get(id);
                if (neighbours.isEmpty()) {
                    return rows.get(id);
                }
                return neighbours.stream().mapToInt(rows::get).average().orElseThrow();
            });
        }
    }

    /**
     * Sorts a column by desired row and gives each task that row, or the next free one below the task
     * placed before it. Ties keep the current order and then topological order, so results are stable.
     */
    private static void place(List<String> column, Map<String, Integer> rows, Subgraph subgraph,
                              ToDoubleFunction<String> desiredRow) {
        // Desired rows are read before moving anything in this column.
        Map<String, Double> desired = new HashMap<>();
        for (String id : column) {
            desired.put(id, desiredRow.applyAsDouble(id));
        }
        column.sort(Comparator
                .comparingDouble((String id) -> desired.get(id))
                .thenComparingInt(id -> rows.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparingInt(subgraph.topologicalIndex::get));
        int previous = -1;
        for (String id : column) {
            // Halves round up the screen, so a task between two others sits next to the upper one.
            int row = Math.max((int) Math.ceil(desired.get(id) - 0.5), previous + 1);
            rows.put(id, row);
            previous = row;
        }
    }

    /** The laid-out tasks with only the dependencies among themselves. */
    private static final class Subgraph {

        private final Map<String, Integer> topologicalIndex = new HashMap<>();
        private final Map<String, Integer> columns = new HashMap<>();
        private final Map<String, List<String>> dependencies = new HashMap<>();
        private final Map<String, List<String>> dependents = new HashMap<>();

        private Subgraph(List<Task> order, Set<String> taskIds) {
            for (Task task : order) {
                topologicalIndex.put(task.id(), topologicalIndex.size());
                dependents.put(task.id(), new ArrayList<>());
                List<String> inside = task.dependencies().stream().filter(taskIds::contains).toList();
                dependencies.put(task.id(), inside);
                // Topological order: every dependency already has its column and dependents list.
                int column = 0;
                for (String dependencyId : inside) {
                    column = Math.max(column, columns.get(dependencyId) + 1);
                    dependents.get(dependencyId).add(task.id());
                }
                columns.put(task.id(), column);
            }
        }

        /**
         * Connected groups, ignoring edge direction, ordered by the creation of their first task.
         * Each block lists its tasks in topological order.
         */
        private List<List<String>> blocks(TaskGraph graph) {
            List<List<String>> blocks = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Task start : graph.tasks()) {
                if (!topologicalIndex.containsKey(start.id()) || !seen.add(start.id())) {
                    continue;
                }
                List<String> block = new ArrayList<>();
                Deque<String> queue = new ArrayDeque<>(List.of(start.id()));
                while (!queue.isEmpty()) {
                    String id = queue.poll();
                    block.add(id);
                    for (String neighbour : neighbours(id)) {
                        if (seen.add(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
                block.sort(Comparator.comparingInt(topologicalIndex::get));
                blocks.add(block);
            }
            return blocks;
        }

        private List<String> neighbours(String id) {
            List<String> neighbours = new ArrayList<>(dependencies.get(id));
            neighbours.addAll(dependents.get(id));
            return neighbours;
        }
    }
}
