package dev.taskgraph.graph;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable snapshot of a task. {@link TaskGraph} replaces instances on every change.
 *
 * @param id           internal id generated from the title at creation; never shown to players
 * @param title        what players see and type; unique ignoring case, accents and spacing
 * @param icon         namespaced material key, e.g. {@code minecraft:diamond}
 * @param dependencies ids this task depends on, in insertion order; the first one is the visual parent
 * @param assignee     assigned player, or {@code null} when nobody is assigned
 * @param creator      player who created the task, or {@code null} for the console and tasks created before
 *                     creators were recorded
 * @param project      id of the project (advancement tab) the task belongs to; {@code null} means General,
 *                     which is how tasks stored before projects existed are read
 */
public record Task(
        String id,
        String title,
        String description,
        String icon,
        List<String> dependencies,
        PlayerRef assignee,
        boolean resolved,
        PlayerRef creator,
        String project
) {

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        description = description == null ? "" : description;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        project = project == null ? Project.GENERAL_ID : project;
    }

    /** Task in General. */
    public Task(String id, String title, String description, String icon, List<String> dependencies,
                PlayerRef assignee, boolean resolved, PlayerRef creator) {
        this(id, title, description, icon, dependencies, assignee, resolved, creator, Project.GENERAL_ID);
    }

    /** Task in General without a recorded creator. */
    public Task(String id, String title, String description, String icon, List<String> dependencies,
                PlayerRef assignee, boolean resolved) {
        this(id, title, description, icon, dependencies, assignee, resolved, null);
    }

    public static Task create(String id, String title, String icon, PlayerRef creator, String project) {
        return new Task(id, title, "", icon, List.of(), null, false, creator, project);
    }

    public Optional<PlayerRef> creatorOptional() {
        return Optional.ofNullable(creator);
    }

    public Optional<PlayerRef> assigneeOptional() {
        return Optional.ofNullable(assignee);
    }

    /** Where this task's advancement progress is stored: its project and its id. */
    public TaskKey key() {
        return new TaskKey(project, id);
    }

    /**
     * The first dependency among {@code drawnIds}, the tasks drawn in this task's tab. Dependencies in
     * other projects are never drawn there, so they cannot be the parent.
     */
    public Optional<String> visualParent(Set<String> drawnIds) {
        return dependencies.stream().filter(drawnIds::contains).findFirst();
    }

    /**
     * Dependencies joined to this task by a line: those among {@code drawnIds}, in dependency order.
     * Dependencies that are not drawn have no line to point at.
     */
    public List<String> lineParents(Set<String> drawnIds) {
        return dependencies.stream().filter(drawnIds::contains).toList();
    }

    /** Dependencies other than the visual parent, listed in the advancement description. */
    public List<String> extraRequirements(Set<String> drawnIds) {
        Optional<String> parent = visualParent(drawnIds);
        return dependencies.stream().filter(id -> !parent.equals(Optional.of(id))).toList();
    }

    Task withTitle(String newTitle) {
        return new Task(id, newTitle, description, icon, dependencies, assignee, resolved, creator, project);
    }

    Task withIcon(String newIcon) {
        return new Task(id, title, description, newIcon, dependencies, assignee, resolved, creator, project);
    }

    Task withDescription(String newDescription) {
        return new Task(id, title, newDescription, icon, dependencies, assignee, resolved, creator, project);
    }

    Task withDependencies(List<String> newDependencies) {
        return new Task(id, title, description, icon, newDependencies, assignee, resolved, creator, project);
    }

    Task withAssignee(PlayerRef newAssignee) {
        return new Task(id, title, description, icon, dependencies, newAssignee, resolved, creator, project);
    }

    Task withResolved(boolean newResolved) {
        return new Task(id, title, description, icon, dependencies, assignee, newResolved, creator, project);
    }

    Task withProject(String newProject) {
        return new Task(id, title, description, icon, dependencies, assignee, resolved, creator, newProject);
    }
}
