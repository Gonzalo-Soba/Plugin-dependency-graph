package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskPermissions.Actor;

/**
 * The one action the task screen offers first, depending on the task and on who is looking. Everything else
 * stays in the submenus; the actions themselves still check every rule when clicked.
 */
public enum MainAction {
    REOPEN,
    RESOLVE,
    ASSIGN,
    /** Take a task that someone else has: only admins. */
    REASSIGN,
    NONE;

    /**
     * <ul>
     *   <li>Resolved: reopen.</li>
     *   <li>Mine and available: resolve.</li>
     *   <li>Mine and blocked: nothing; the screen shows what is missing.</li>
     *   <li>Free (available or blocked): assign to me.</li>
     *   <li>Someone else's: reassign for admins, nothing for everyone else.</li>
     * </ul>
     * Senders that aren't players can't be assigned, so for them only reopening is offered.
     */
    public static MainAction of(Task task, TaskStatus status, Actor actor) {
        if (task.resolved()) {
            return REOPEN;
        }
        if (actor.uuid() == null) {
            return NONE;
        }
        if (actor.is(task.assigneeOptional())) {
            return status == TaskStatus.AVAILABLE ? RESOLVE : NONE;
        }
        if (task.assigneeOptional().isEmpty()) {
            return ASSIGN;
        }
        return actor.admin() ? REASSIGN : NONE;
    }
}
