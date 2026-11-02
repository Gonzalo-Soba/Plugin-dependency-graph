package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskGraphException.Reason;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Directed acyclic graph of tasks. An edge {@code a -> b} means "a depends on b".
 * Every mutating method validates first and leaves the graph untouched when it throws.
 * Iteration order is creation order, which keeps topological order and layout stable.
 */
public final class TaskGraph {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    private final Map<String, Task> tasks = new LinkedHashMap<>();
    // Ids of deleted tasks. Players who were offline at deletion may still hold progress for them.
    private final Set<String> removedIds = new LinkedHashSet<>();

    /**
     * Rebuilds a graph from stored tasks, rejecting data that breaks any graph rule.
     */
    public static TaskGraph of(Collection<Task> stored) {
        return of(stored, List.of());
    }

    /**
     * Rebuilds a graph from stored tasks and removed ids. Removed ids that belong to an existing task are dropped.
     */
    public static TaskGraph of(Collection<Task> stored, Collection<String> storedRemovedIds) {
        TaskGraph graph = new TaskGraph();
        Set<String> titleKeys = new HashSet<>();
        for (Task task : stored) {
            validateId(task.id());
            validateTitle(task.id(), task.title());
            // Data written before titles had to be unique may repeat one; disambiguate instead of refusing to load.
            Task unique = task;
            while (!titleKeys.add(Titles.key(unique.title()))) {
                unique = unique.withTitle(unique.title() + " (" + task.id() + ")");
            }
            if (graph.tasks.putIfAbsent(task.id(), unique) != null) {
                throw new TaskGraphException(Reason.DUPLICATE_ID, task.id());
            }
        }
        for (Task task : graph.tasks.values()) {
            Set<String> seen = new HashSet<>();
            for (String dependencyId : task.dependencies()) {
                if (dependencyId.equals(task.id())) {
                    throw new TaskGraphException(Reason.SELF_DEPENDENCY, task.id());
                }
                if (!seen.add(dependencyId)) {
                    throw new TaskGraphException(Reason.DUPLICATE_DEPENDENCY, task.id(), List.of(dependencyId));
                }
                Task dependency = graph.tasks.get(dependencyId);
                if (dependency == null) {
                    throw new TaskGraphException(Reason.NOT_FOUND, dependencyId);
                }
                if (task.resolved() && !dependency.resolved()) {
                    throw new TaskGraphException(Reason.RESOLVED_TASK_DEPENDENCY, task.id(), List.of(dependencyId));
                }
            }
        }
        graph.topologicalOrder();
        for (String removedId : storedRemovedIds) {
            if (!graph.tasks.containsKey(removedId)) {
                graph.removedIds.add(removedId);
            }
        }
        return graph;
    }

    /** Independent copy, so a change can be tried and discarded without touching this graph. */
    public TaskGraph copy() {
        TaskGraph copy = new TaskGraph();
        copy.tasks.putAll(tasks);
        copy.removedIds.addAll(removedIds);
        return copy;
    }

    /**
     * Creates a task with an id derived from its title. Ids of deleted tasks are never reused, so a new
     * task can't inherit progress that offline players still hold for an old one.
     */
    public Task create(String title, String icon) {
        return create(title, icon, null);
    }

    /**
     * @param creator player who creates the task, or {@code null} for the console
     */
    public Task create(String title, String icon, PlayerRef creator) {
        validateTitle(null, title);
        String cleanTitle = Titles.clean(title);
        requireUniqueTitle(cleanTitle, null);
        String id = Titles.generateId(cleanTitle, candidate -> tasks.containsKey(candidate) || removedIds.contains(candidate));
        Task task = Task.create(id, cleanTitle, icon, creator);
        tasks.put(id, task);
        return task;
    }

    public Task rename(String id, String newTitle) {
        Task task = require(id);
        validateTitle(id, newTitle);
        String cleanTitle = Titles.clean(newTitle);
        requireUniqueTitle(cleanTitle, id);
        return replace(task.withTitle(cleanTitle));
    }

    /** Finds a task by title, ignoring case, accents and spacing. */
    public Optional<Task> findByTitle(String title) {
        String key = Titles.key(title);
        return tasks.values().stream()
                .filter(task -> Titles.key(task.title()).equals(key))
                .findFirst();
    }

    public Task setDescription(String id, String description) {
        return replace(require(id).withDescription(description.strip()));
    }

    /** @param icon namespaced material key, e.g. {@code minecraft:diamond} */
    public Task setIcon(String id, String icon) {
        Objects.requireNonNull(icon, "icon");
        return replace(require(id).withIcon(icon));
    }

    public Task addDependency(String id, String dependencyId) {
        Task task = require(id);
        Task dependency = require(dependencyId);
        if (id.equals(dependencyId)) {
            throw new TaskGraphException(Reason.SELF_DEPENDENCY, id);
        }
        if (task.dependencies().contains(dependencyId)) {
            throw new TaskGraphException(Reason.DUPLICATE_DEPENDENCY, id, List.of(dependencyId));
        }
        List<String> cycle = pathBetween(dependencyId, id);
        if (!cycle.isEmpty()) {
            List<String> path = new ArrayList<>();
            path.add(id);
            path.addAll(cycle);
            throw new TaskGraphException(Reason.CYCLE, id, path);
        }
        if (task.resolved() && !dependency.resolved()) {
            throw new TaskGraphException(Reason.RESOLVED_TASK_DEPENDENCY, id, List.of(dependencyId));
        }
        List<String> dependencies = new ArrayList<>(task.dependencies());
        dependencies.add(dependencyId);
        return replace(task.withDependencies(dependencies));
    }

    /**
     * Removes a dependency, keeping the order of the rest. Removing the visual parent makes the next
     * dependency the new parent; with none left, the task hangs from the root again.
     */
    public Task removeDependency(String id, String dependencyId) {
        Task task = require(id);
        if (!task.dependencies().contains(dependencyId)) {
            throw new TaskGraphException(Reason.NOT_A_DEPENDENCY, id, List.of(dependencyId));
        }
        List<String> dependencies = new ArrayList<>(task.dependencies());
        dependencies.remove(dependencyId);
        return replace(task.withDependencies(dependencies));
    }

    /** Assigns a task that is free or already assigned to the same player. */
    public Task assign(String id, PlayerRef assignee) {
        return assign(id, assignee, false);
    }

    /**
     * @param replaceOther whether a task assigned to someone else may be taken over (admin reassignment)
     */
    public Task assign(String id, PlayerRef assignee, boolean replaceOther) {
        Task task = require(id);
        boolean takenByOther = task.assigneeOptional().filter(current -> !current.is(assignee.uuid())).isPresent();
        if (takenByOther && !replaceOther) {
            throw new TaskGraphException(Reason.ALREADY_ASSIGNED, id);
        }
        return replace(task.withAssignee(assignee));
    }

    public Task unassign(String id) {
        Task task = require(id);
        if (task.assigneeOptional().isEmpty()) {
            throw new TaskGraphException(Reason.NOT_ASSIGNED, id);
        }
        return replace(task.withAssignee(null));
    }

    public Task resolve(String id) {
        Task task = require(id);
        if (task.resolved()) {
            throw new TaskGraphException(Reason.ALREADY_RESOLVED, id);
        }
        List<String> unresolved = unresolvedDependencies(id);
        if (!unresolved.isEmpty()) {
            throw new TaskGraphException(Reason.UNRESOLVED_DEPENDENCIES, id, unresolved);
        }
        return replace(task.withResolved(true));
    }

    public Task remove(String id) {
        require(id);
        List<String> dependents = dependents(id);
        if (!dependents.isEmpty()) {
            throw new TaskGraphException(Reason.HAS_DEPENDENTS, id, dependents);
        }
        removedIds.add(id);
        return tasks.remove(id);
    }

    public Optional<Task> get(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public boolean contains(String id) {
        return tasks.containsKey(id);
    }

    public int size() {
        return tasks.size();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    /** All tasks in creation order. */
    public List<Task> tasks() {
        return List.copyOf(tasks.values());
    }

    /** Ids of the tasks that depend directly on {@code id}, in creation order. */
    public List<String> dependents(String id) {
        require(id);
        return tasks.values().stream()
                .filter(task -> task.dependencies().contains(id))
                .map(Task::id)
                .toList();
    }

    public List<String> unresolvedDependencies(String id) {
        return require(id).dependencies().stream()
                .filter(dependencyId -> !tasks.get(dependencyId).resolved())
                .toList();
    }

    public TaskStatus status(String id) {
        Task task = require(id);
        if (task.resolved()) {
            return TaskStatus.RESOLVED;
        }
        return unresolvedDependencies(id).isEmpty() ? TaskStatus.AVAILABLE : TaskStatus.BLOCKED;
    }

    /**
     * Tasks for the task list: available first, then blocked, then resolved; alphabetical by title inside
     * each group, so a task is easy to find in a long list.
     */
    public List<Task> listOrder() {
        Comparator<Task> byStatus = Comparator.comparing(task -> status(task.id()));
        return tasks.values().stream()
                .sorted(byStatus.thenComparing(task -> Titles.key(task.title())))
                .toList();
    }

    /**
     * Tasks that {@code id} could depend on right now: not itself, not already a dependency, not one that
     * already depends on it (directly or not, which would close a cycle), and only resolved ones when the
     * task itself is resolved. Alphabetical by title.
     */
    public List<Task> dependencyCandidates(String id) {
        Task task = require(id);
        Set<String> dependsOnTask = transitiveDependents(id);
        return tasks.values().stream()
                .filter(candidate -> !candidate.id().equals(id))
                .filter(candidate -> !task.dependencies().contains(candidate.id()))
                .filter(candidate -> !dependsOnTask.contains(candidate.id()))
                .filter(candidate -> !task.resolved() || candidate.resolved())
                .sorted(Comparator.comparing(candidate -> Titles.key(candidate.title())))
                .toList();
    }

    /** Ids of every task that depends on {@code id}, directly or through other tasks. */
    private Set<String> transitiveDependents(String id) {
        Set<String> found = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Task task : tasks.values()) {
                if (task.dependencies().contains(current) && found.add(task.id())) {
                    queue.add(task.id());
                }
            }
        }
        return found;
    }

    /** Ids of deleted tasks that have not been created again, in deletion order. */
    public Set<String> removedIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(removedIds));
    }

    public Set<String> resolvedIds() {
        Set<String> resolved = new LinkedHashSet<>();
        for (Task task : tasks.values()) {
            if (task.resolved()) {
                resolved.add(task.id());
            }
        }
        return resolved;
    }

    /**
     * Dependencies before dependents. Ties are broken by creation order, so the result is stable.
     */
    public List<Task> topologicalOrder() {
        Map<String, Integer> creationIndex = new HashMap<>();
        Map<String, Integer> pendingDependencies = new HashMap<>();
        Map<String, List<String>> dependentsById = new HashMap<>();
        for (Task task : tasks.values()) {
            creationIndex.put(task.id(), creationIndex.size());
            pendingDependencies.put(task.id(), task.dependencies().size());
            for (String dependencyId : task.dependencies()) {
                dependentsById.computeIfAbsent(dependencyId, key -> new ArrayList<>()).add(task.id());
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.comparingInt(creationIndex::get));
        pendingDependencies.forEach((id, pending) -> {
            if (pending == 0) {
                ready.add(id);
            }
        });

        List<Task> order = new ArrayList<>(tasks.size());
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(tasks.get(id));
            for (String dependentId : dependentsById.getOrDefault(id, List.of())) {
                if (pendingDependencies.merge(dependentId, -1, Integer::sum) == 0) {
                    ready.add(dependentId);
                }
            }
        }

        if (order.size() != tasks.size()) {
            List<String> inCycle = tasks.keySet().stream()
                    .filter(id -> pendingDependencies.get(id) > 0)
                    .toList();
            throw new TaskGraphException(Reason.CYCLE, inCycle.getFirst(), inCycle);
        }
        return order;
    }

    /**
     * Dependency path from {@code fromId} to {@code toId} (both included), or empty if {@code toId}
     * is not reachable by following dependencies.
     */
    private List<String> pathBetween(String fromId, String toId) {
        Map<String, String> reachedFrom = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromId);
        reachedFrom.put(fromId, null);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toId)) {
                List<String> path = new ArrayList<>();
                for (String step = current; step != null; step = reachedFrom.get(step)) {
                    path.addFirst(step);
                }
                return path;
            }
            for (String next : tasks.get(current).dependencies()) {
                if (!reachedFrom.containsKey(next)) {
                    reachedFrom.put(next, current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    private Task require(String id) {
        Task task = tasks.get(id);
        if (task == null) {
            throw new TaskGraphException(Reason.NOT_FOUND, id);
        }
        return task;
    }

    private Task replace(Task task) {
        tasks.put(task.id(), task);
        return task;
    }

    private static void validateId(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new TaskGraphException(Reason.INVALID_ID, String.valueOf(id));
        }
    }

    private static void validateTitle(String id, String title) {
        if (title == null || title.isBlank()) {
            throw new TaskGraphException(Reason.INVALID_TITLE, String.valueOf(id));
        }
    }

    /** Rejects a title already used by another task; {@code ownId} may keep its own title (e.g. case changes). */
    private void requireUniqueTitle(String title, String ownId) {
        findByTitle(title)
                .filter(existing -> !existing.id().equals(ownId))
                .ifPresent(existing -> {
                    throw new TaskGraphException(Reason.DUPLICATE_TITLE, existing.id());
                });
    }
}
