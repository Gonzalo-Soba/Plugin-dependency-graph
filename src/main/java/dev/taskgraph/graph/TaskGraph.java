package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskGraphException.Reason;

import java.time.Instant;
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
import java.util.stream.Collectors;

/**
 * Directed acyclic graph of tasks. An edge {@code a -> b} means "a depends on b".
 * Every mutating method validates first and leaves the graph untouched when it throws.
 * Iteration order is creation order, which keeps topological order and layout stable.
 * <p>
 * Every task belongs to a project. General always exists and comes first; dependencies may cross projects.
 */
public final class TaskGraph {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");
    private static final String PROJECT_FALLBACK_ID = "proyecto";

    private final Map<String, Project> projects = new LinkedHashMap<>();
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    // Ids of deleted tasks, never handed out again so a new task can't inherit their progress.
    private final Set<String> removedIds = new LinkedHashSet<>();
    // Keys that no task uses anymore (deleted or moved to another project). Players who were offline
    // when it happened may still hold progress under them.
    private final Set<TaskKey> retiredKeys = new LinkedHashSet<>();

    public TaskGraph() {
        projects.put(Project.GENERAL_ID, Project.general());
    }

    /**
     * Rebuilds a graph from stored tasks, rejecting data that breaks any graph rule.
     */
    public static TaskGraph of(Collection<Task> stored) {
        return of(stored, List.of());
    }

    /**
     * Rebuilds a graph stored before projects existed: every task is in General, and each removed id is
     * also a retired General key. Removed ids that belong to an existing task are dropped.
     */
    public static TaskGraph of(Collection<Task> stored, Collection<String> storedRemovedIds) {
        List<TaskKey> retired = storedRemovedIds.stream().map(id -> new TaskKey(Project.GENERAL_ID, id)).toList();
        return of(List.of(), stored, storedRemovedIds, retired);
    }

    /**
     * Rebuilds a graph from stored projects, tasks, removed ids and retired keys, rejecting data that breaks
     * any graph rule. General is added when missing and always goes first. Removed ids and retired keys
     * still used by an existing task are dropped.
     */
    public static TaskGraph of(Collection<Project> storedProjects, Collection<Task> stored,
                               Collection<String> storedRemovedIds, Collection<TaskKey> storedRetiredKeys) {
        TaskGraph graph = new TaskGraph();
        List<Project> projects = new ArrayList<>();
        projects.add(storedProjects.stream().filter(Project::isGeneral).findFirst().orElseGet(Project::general));
        storedProjects.stream().filter(project -> !project.isGeneral()).forEach(projects::add);
        graph.projects.clear();
        Set<String> projectTitleKeys = new HashSet<>();
        for (Project project : projects) {
            validateId(project.id());
            validateTitle(project.id(), project.title());
            if (graph.projects.putIfAbsent(project.id(), project) != null) {
                throw new TaskGraphException(Reason.DUPLICATE_ID, project.id());
            }
            if (!projectTitleKeys.add(Titles.key(project.title()))) {
                throw new TaskGraphException(Reason.DUPLICATE_PROJECT_TITLE, project.id());
            }
        }

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
            graph.requireProject(task.project());
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
        Set<TaskKey> currentKeys = graph.tasks.values().stream().map(Task::key).collect(Collectors.toSet());
        for (TaskKey retired : storedRetiredKeys) {
            if (!currentKeys.contains(retired)) {
                graph.retiredKeys.add(retired);
            }
        }
        return graph;
    }

    /** Independent copy, so a change can be tried and discarded without touching this graph. */
    public TaskGraph copy() {
        TaskGraph copy = new TaskGraph();
        copy.projects.putAll(projects);
        copy.tasks.putAll(tasks);
        copy.removedIds.addAll(removedIds);
        copy.retiredKeys.addAll(retiredKeys);
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
     * Creates a task in General.
     *
     * @param creator player who creates the task, or {@code null} for the console
     */
    public Task create(String title, String icon, PlayerRef creator) {
        return create(title, icon, creator, Project.GENERAL_ID);
    }

    /**
     * @param creator   player who creates the task, or {@code null} for the console
     * @param projectId project the task goes in
     */
    public Task create(String title, String icon, PlayerRef creator, String projectId) {
        requireProject(projectId);
        validateTitle(null, title);
        String cleanTitle = Titles.clean(title);
        requireUniqueTitle(cleanTitle, null);
        String id = Titles.generateId(cleanTitle, candidate -> tasks.containsKey(candidate) || removedIds.contains(candidate));
        Task task = Task.create(id, cleanTitle, icon, creator, projectId);
        tasks.put(id, task);
        return task;
    }

    /**
     * Moves a task to another project, keeping its dependencies (which may now cross projects). Its old
     * key is retired, since offline players may still hold progress under it.
     */
    public Task moveTask(String id, String projectId) {
        Task task = require(id);
        requireProject(projectId);
        if (task.project().equals(projectId)) {
            throw new TaskGraphException(Reason.ALREADY_IN_PROJECT, id, List.of(projectId));
        }
        retiredKeys.add(task.key());
        Task moved = replace(task.withProject(projectId));
        // Moving back to a project it was in before reuses that key.
        retiredKeys.remove(moved.key());
        return moved;
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
    public Task setSize(String id, TaskSize size) {
        Objects.requireNonNull(size, "size");
        return replace(require(id).withSize(size));
    }

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

    /** Resolves without recording who or when; for tests and data built by hand. */
    public Task resolve(String id) {
        return resolve(id, null, null);
    }

    /**
     * @param by player who resolves it, or {@code null} for the console
     * @param at when it is resolved
     */
    public Task resolve(String id, PlayerRef by, Instant at) {
        Task task = require(id);
        if (task.resolved()) {
            throw new TaskGraphException(Reason.ALREADY_RESOLVED, id);
        }
        List<String> unresolved = unresolvedDependencies(id);
        if (!unresolved.isEmpty()) {
            throw new TaskGraphException(Reason.UNRESOLVED_DEPENDENCIES, id, unresolved);
        }
        return replace(task.withResolution(by, at));
    }

    /**
     * Undoes a resolution. Rejected while a resolved task depends on it, so a resolved task never has a
     * pending dependency.
     */
    public Task reopen(String id) {
        Task task = require(id);
        if (!task.resolved()) {
            throw new TaskGraphException(Reason.NOT_RESOLVED, id);
        }
        List<String> resolvedDependents = dependents(id).stream().filter(dependent -> tasks.get(dependent).resolved()).toList();
        if (!resolvedDependents.isEmpty()) {
            throw new TaskGraphException(Reason.RESOLVED_DEPENDENTS, id, resolvedDependents);
        }
        return replace(task.reopened());
    }

    public Task remove(String id) {
        require(id);
        List<String> dependents = dependents(id);
        if (!dependents.isEmpty()) {
            throw new TaskGraphException(Reason.HAS_DEPENDENTS, id, dependents);
        }
        removedIds.add(id);
        Task removed = tasks.remove(id);
        retiredKeys.add(removed.key());
        return removed;
    }

    public Project createProject(String title) {
        validateTitle(null, title);
        String cleanTitle = Titles.clean(title);
        requireUniqueProjectTitle(cleanTitle, null);
        String id = Titles.generateId(cleanTitle, PROJECT_FALLBACK_ID, projects::containsKey);
        Project project = new Project(id, cleanTitle, false);
        projects.put(id, project);
        return project;
    }

    public Project renameProject(String id, String newTitle) {
        Project project = requireProject(id);
        validateTitle(id, newTitle);
        String cleanTitle = Titles.clean(newTitle);
        requireUniqueProjectTitle(cleanTitle, id);
        Project renamed = project.withTitle(cleanTitle);
        projects.put(id, renamed);
        return renamed;
    }

    /** Removes an empty project. General can't be removed. */
    public Project removeProject(String id) {
        Project project = requireProject(id);
        if (project.isGeneral()) {
            throw new TaskGraphException(Reason.GENERAL_PROJECT, id);
        }
        List<String> inside = tasksIn(id).stream().map(Task::id).toList();
        if (!inside.isEmpty()) {
            throw new TaskGraphException(Reason.PROJECT_NOT_EMPTY, id, inside);
        }
        return projects.remove(id);
    }

    public Project setHideResolved(String id, boolean hide) {
        Project updated = requireProject(id).withHideResolved(hide);
        projects.put(id, updated);
        return updated;
    }

    /**
     * Whether the task is left out of its tab: its project hides resolved tasks, and it is a finished
     * branch, i.e. resolved and with every task that depends on it (in any project) resolved too.
     * A resolved task that still unlocks something pending stays visible.
     */
    public boolean isHidden(String id) {
        Task task = require(id);
        if (!task.resolved() || !requireProject(task.project()).hideResolved()) {
            return false;
        }
        return tasks.values().stream()
                .filter(other -> other.dependencies().contains(id))
                .allMatch(Task::resolved);
    }

    /** Tasks of the project drawn in its tab, in creation order. */
    public List<Task> visibleTasksIn(String projectId) {
        return tasksIn(projectId).stream().filter(task -> !isHidden(task.id())).toList();
    }

    /** Tasks of the project left out of its tab, in creation order. */
    public List<Task> hiddenTasksIn(String projectId) {
        return tasksIn(projectId).stream().filter(task -> isHidden(task.id())).toList();
    }

    /** Projects in creation order, General first. */
    public List<Project> projects() {
        return List.copyOf(projects.values());
    }

    public Optional<Project> project(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    /** Finds a project by title, ignoring case, accents and spacing. */
    public Optional<Project> findProjectByTitle(String title) {
        String key = Titles.key(title);
        return projects.values().stream()
                .filter(project -> Titles.key(project.title()).equals(key))
                .findFirst();
    }

    /** Tasks of a project, in creation order. */
    public List<Task> tasksIn(String projectId) {
        requireProject(projectId);
        return tasks.values().stream()
                .filter(task -> task.project().equals(projectId))
                .toList();
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
     * Tasks that were blocked in {@code before} and are available now, in creation order: what a change
     * unlocked. New tasks don't count, even if they are available.
     */
    public List<Task> unlockedSince(TaskGraph before) {
        return tasks.values().stream()
                .filter(task -> status(task.id()) == TaskStatus.AVAILABLE)
                .filter(task -> before.get(task.id()).isPresent() && before.status(task.id()) == TaskStatus.BLOCKED)
                .toList();
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

    /** Keys no task uses anymore, from deleted tasks and tasks moved to another project, oldest first. */
    public Set<TaskKey> retiredKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(retiredKeys));
    }

    /** Keys of the tasks that exist now, in creation order. */
    public Set<TaskKey> currentKeys() {
        Set<TaskKey> keys = new LinkedHashSet<>();
        for (Task task : tasks.values()) {
            keys.add(task.key());
        }
        return keys;
    }

    /** Keys of the resolved tasks, in creation order. */
    public Set<TaskKey> resolvedKeys() {
        Set<TaskKey> keys = new LinkedHashSet<>();
        for (Task task : tasks.values()) {
            if (task.resolved()) {
                keys.add(task.key());
            }
        }
        return keys;
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

    private Project requireProject(String id) {
        Project project = projects.get(id);
        if (project == null) {
            throw new TaskGraphException(Reason.PROJECT_NOT_FOUND, id);
        }
        return project;
    }

    /** Rejects a title already used by another project; {@code ownId} may keep its own title. */
    private void requireUniqueProjectTitle(String title, String ownId) {
        findProjectByTitle(title)
                .filter(existing -> !existing.id().equals(ownId))
                .ifPresent(existing -> {
                    throw new TaskGraphException(Reason.DUPLICATE_PROJECT_TITLE, existing.id());
                });
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
