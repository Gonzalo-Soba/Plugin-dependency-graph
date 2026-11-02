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
 */
public record Task(
        String id,
        String title,
        String description,
        String icon,
        List<String> dependencies,
        PlayerRef assignee,
        boolean resolved,
        PlayerRef creator
) {

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        description = description == null ? "" : description;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /** Task without a recorded creator. */
    public Task(String id, String title, String description, String icon, List<String> dependencies,
                PlayerRef assignee, boolean resolved) {
        this(id, title, description, icon, dependencies, assignee, resolved, null);
    }

    public static Task create(String id, String title, String icon, PlayerRef creator) {
        return new Task(id, title, "", icon, List.of(), null, false, creator);
    }

    public Optional<PlayerRef> creatorOptional() {
        return Optional.ofNullable(creator);
    }

    public Optional<PlayerRef> assigneeOptional() {
        return Optional.ofNullable(assignee);
    }

    public Optional<String> visualParent() {
        return dependencies.isEmpty() ? Optional.empty() : Optional.of(dependencies.getFirst());
    }

    /**
     * Dependencies joined to this task by a line: those among {@code drawnIds}, in dependency order.
     * Dependencies that are not drawn have no line to point at.
     */
    public List<String> lineParents(Set<String> drawnIds) {
        return dependencies.stream().filter(drawnIds::contains).toList();
    }

    /** Dependencies other than the visual parent, listed in the advancement description. */
    public List<String> extraRequirements() {
        return dependencies.isEmpty() ? List.of() : dependencies.subList(1, dependencies.size());
    }

    Task withTitle(String newTitle) {
        return new Task(id, newTitle, description, icon, dependencies, assignee, resolved, creator);
    }

    Task withDescription(String newDescription) {
        return new Task(id, title, newDescription, icon, dependencies, assignee, resolved, creator);
    }

    Task withDependencies(List<String> newDependencies) {
        return new Task(id, title, description, icon, newDependencies, assignee, resolved, creator);
    }

    Task withAssignee(PlayerRef newAssignee) {
        return new Task(id, title, description, icon, dependencies, newAssignee, resolved, creator);
    }

    Task withResolved(boolean newResolved) {
        return new Task(id, title, description, icon, dependencies, assignee, newResolved, creator);
    }
}
