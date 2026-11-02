package dev.taskgraph.graph;

import dev.taskgraph.storage.DevServerSamples;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphLayoutTest {

    private TaskGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TaskGraph();
    }

    private void createAll(String... ids) {
        for (String id : ids) {
            graph.create(id, "minecraft:stone");
        }
    }

    @Test
    void emptyGraphHasNoPositions() {
        assertTrue(GraphLayout.compute(graph).isEmpty());
    }

    @Test
    void unconnectedTasksAreSeparateBlocksInCreationOrder() {
        createAll("a", "b", "c");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(new NodePosition(0, 0), positions.get("a"));
        assertEquals(new NodePosition(0, 2), positions.get("b"));
        assertEquals(new NodePosition(0, 4), positions.get("c"));
    }

    @Test
    void blocksDoNotMixAndLeaveOneEmptyRowBetweenThem() {
        // Created interleaved on purpose: the two chains must still end up apart.
        createAll("a", "x", "b", "y", "c");
        graph.addDependency("b", "a");
        graph.addDependency("y", "x");
        graph.addDependency("c", "b");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(new NodePosition(0, 0), positions.get("a"));
        assertEquals(new NodePosition(1, 0), positions.get("b"));
        assertEquals(new NodePosition(2, 0), positions.get("c"));
        assertEquals(new NodePosition(0, 2), positions.get("x"));
        assertEquals(new NodePosition(1, 2), positions.get("y"));
    }

    @Test
    void columnIsLongestDependencyPath() {
        createAll("base", "middle", "top");
        graph.addDependency("middle", "base");
        // Visual parent is base, but middle forces top into column 2.
        graph.addDependency("top", "base");
        graph.addDependency("top", "middle");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(0, positions.get("base").column());
        assertEquals(1, positions.get("middle").column());
        assertEquals(2, positions.get("top").column());
    }

    @Test
    void taskSitsAtTheRowOfItsDependencyInsteadOfTheTop() {
        createAll("hub", "t1", "t2", "t3", "t4", "late");
        for (String id : List.of("t1", "t2", "t3", "t4")) {
            graph.addDependency(id, "hub");
        }
        graph.addDependency("late", "t4");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(positions.get("t4").row(), positions.get("late").row());
    }

    @Test
    void taskWithSeveralDependenciesSitsBetweenThem() {
        createAll("a", "middle", "c", "p", "q", "r", "joined");
        graph.addDependency("p", "a");
        graph.addDependency("q", "middle");
        graph.addDependency("r", "c");
        graph.addDependency("q", "a");
        graph.addDependency("r", "middle");
        graph.addDependency("joined", "a");
        graph.addDependency("joined", "c");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        int row = positions.get("joined").row();
        int top = Math.min(positions.get("a").row(), positions.get("c").row());
        int bottom = Math.max(positions.get("a").row(), positions.get("c").row());
        assertTrue(top <= row && row <= bottom, "joined at row " + row + ", dependencies between " + top + " and " + bottom);
    }

    @Test
    void secondDependencyCountsToAvoidCrossings() {
        // Ordering by first dependency only would put "shared" above "single" and cross b->shared with a->single.
        createAll("a", "b", "shared", "single");
        graph.addDependency("shared", "a");
        graph.addDependency("shared", "b");
        graph.addDependency("single", "a");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(0, crossings(positions));
    }

    @Test
    void noTwoTasksShareAPosition() {
        createAll("a", "b", "c", "d", "e", "f");
        graph.addDependency("c", "a");
        graph.addDependency("d", "a");
        graph.addDependency("d", "b");
        graph.addDependency("e", "c");
        graph.addDependency("f", "d");
        graph.addDependency("f", "e");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        assertEquals(graph.size(), positions.size());
        assertEquals(graph.size(), new HashSet<>(positions.values()).size());
        assertTrue(positions.values().stream().allMatch(position -> position.row() >= 0));
    }

    @Test
    void everyDependencyIsInAnEarlierColumn() {
        createAll("a", "b", "c", "d");
        graph.addDependency("b", "a");
        graph.addDependency("c", "b");
        graph.addDependency("d", "a");
        graph.addDependency("d", "c");

        Map<String, NodePosition> positions = GraphLayout.compute(graph);

        for (Task task : graph.tasks()) {
            for (String dependencyId : task.dependencies()) {
                assertTrue(positions.get(dependencyId).column() < positions.get(task.id()).column(),
                        dependencyId + " should be left of " + task.id());
            }
        }
    }

    @Test
    void iteratesInTopologicalOrderAndIsStable() {
        createAll("b", "a");
        graph.addDependency("b", "a");

        Map<String, NodePosition> first = GraphLayout.compute(graph);

        assertEquals(List.of("a", "b"), List.copyOf(first.keySet()));
        assertEquals(first, GraphLayout.compute(graph));
        assertEquals(Set.of(new NodePosition(0, 0), new NodePosition(1, 0)), Set.copyOf(first.values()));
    }

    @Test
    void subsetIgnoresTasksOutsideIt() {
        createAll("a", "b", "c");
        graph.addDependency("b", "a");
        graph.addDependency("c", "b");

        Map<String, NodePosition> positions = GraphLayout.compute(graph, Set.of("b", "c"));

        assertEquals(List.of("b", "c"), List.copyOf(positions.keySet()));
        assertEquals(new NodePosition(0, 0), positions.get("b"));
        assertEquals(new NodePosition(1, 0), positions.get("c"));
    }

    @Test
    void sampleWithThirtyTasksIsValidAndBarelyTallerThanBefore(@TempDir Path directory) throws IOException {
        TaskGraph sample = DevServerSamples.load("tasks-30.json", directory);

        Map<String, NodePosition> positions = GraphLayout.compute(sample);

        assertEquals(sample.size(), new HashSet<>(positions.values()).size());
        // The 0.2.x layout packed every column from row 0, so its height was the fullest column.
        Map<Integer, Integer> tasksPerColumn = new HashMap<>();
        positions.values().forEach(position -> tasksPerColumn.merge(position.column(), 1, Integer::sum));
        int packedHeight = tasksPerColumn.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        int height = positions.values().stream().mapToInt(NodePosition::row).max().orElseThrow() + 1;
        assertTrue(height <= packedHeight * 3 / 2, "height " + height + ", packed height " + packedHeight);
    }

    /** Crossing pairs among edges that join the same two adjacent columns. */
    private int crossings(Map<String, NodePosition> positions) {
        List<NodePosition[]> edges = new ArrayList<>();
        for (Task task : graph.tasks()) {
            for (String dependencyId : task.dependencies()) {
                edges.add(new NodePosition[]{positions.get(dependencyId), positions.get(task.id())});
            }
        }
        int crossings = 0;
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                NodePosition[] first = edges.get(i);
                NodePosition[] second = edges.get(j);
                boolean sameColumns = first[0].column() == second[0].column() && first[1].column() == second[1].column()
                        && first[1].column() == first[0].column() + 1;
                if (sameColumns && (first[0].row() - second[0].row()) * (first[1].row() - second[1].row()) < 0) {
                    crossings++;
                }
            }
        }
        return crossings;
    }
}
