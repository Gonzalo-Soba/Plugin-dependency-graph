package dev.taskgraph.graph;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may do what with a task. Everything not listed here is open to every player.
 */
public final class TaskPermissions {

    private TaskPermissions() {
    }

    /**
     * Whoever runs an action.
     *
     * @param uuid  the player's id, or {@code null} for the console and other non-player senders
     * @param name  shown in announcements, e.g. who resolved a task
     * @param admin whether the sender has the admin permission (the console always does)
     */
    public record Actor(UUID uuid, String name, boolean admin) {

        public static Actor player(UUID uuid, String name, boolean admin) {
            return new Actor(uuid, name, admin);
        }

        public static Actor console() {
            return new Actor(null, "CONSOLE", true);
        }

        /** The actor as a player reference, empty for non-player senders. */
        public Optional<PlayerRef> playerRef() {
            return uuid == null ? Optional.empty() : Optional.of(new PlayerRef(uuid, name));
        }

        boolean is(Optional<PlayerRef> player) {
            return uuid != null && player.filter(ref -> ref.is(uuid)).isPresent();
        }
    }

    /** Only admins remove projects; creating and renaming them, and moving tasks between them, is open. */
    public static boolean canRemoveProject(Actor actor) {
        return actor.admin();
    }

    /** The creator or an admin. Tasks without a recorded creator can only be removed by admins. */
    public static boolean canRemove(Task task, Actor actor) {
        return actor.admin() || actor.is(task.creatorOptional());
    }

    /** The assignee or an admin. */
    public static boolean canRelease(Task task, Actor actor) {
        return actor.admin() || actor.is(task.assigneeOptional());
    }

    /** Free tasks and your own can be taken by anyone; a task assigned to someone else only by an admin. */
    public static boolean canAssignToSelf(Task task, Actor actor) {
        return actor.admin() || task.assigneeOptional().isEmpty() || actor.is(task.assigneeOptional());
    }
}
