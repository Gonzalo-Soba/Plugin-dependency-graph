package dev.taskgraph.graph;

import java.util.List;

/**
 * Rejected graph operation. {@link #relatedIds()} carries the tasks that explain the rejection
 * (the cycle path, the unresolved dependencies, the dependents...), so callers can build a message.
 * For the project reasons, {@link #taskId()} is a project id instead; see each reason.
 */
public final class TaskGraphException extends RuntimeException {

    public enum Reason {
        INVALID_ID,
        INVALID_TITLE,
        DUPLICATE_ID,
        DUPLICATE_TITLE,
        NOT_FOUND,
        SELF_DEPENDENCY,
        DUPLICATE_DEPENDENCY,
        NOT_A_DEPENDENCY,
        CYCLE,
        RESOLVED_TASK_DEPENDENCY,
        ALREADY_ASSIGNED,
        NOT_ASSIGNED,
        ALREADY_RESOLVED,
        UNRESOLVED_DEPENDENCIES,
        HAS_DEPENDENTS,
        NOT_RESOLVED,
        /** Related ids are the resolved tasks that depend on it. */
        RESOLVED_DEPENDENTS,
        /** Subject is the project id. */
        PROJECT_NOT_FOUND,
        /** Subject is the id of the project that already has the title. */
        DUPLICATE_PROJECT_TITLE,
        /** Subject is the General project id. */
        GENERAL_PROJECT,
        /** Subject is the project id; related ids are its tasks. */
        PROJECT_NOT_EMPTY,
        /** Subject is the task id; the related id is the project it is already in. */
        ALREADY_IN_PROJECT
    }

    private final Reason reason;
    private final String taskId;
    private final List<String> relatedIds;

    public TaskGraphException(Reason reason, String taskId) {
        this(reason, taskId, List.of());
    }

    public TaskGraphException(Reason reason, String taskId, List<String> relatedIds) {
        super(reason + " for task '" + taskId + "'" + (relatedIds.isEmpty() ? "" : ": " + relatedIds));
        this.reason = reason;
        this.taskId = taskId;
        this.relatedIds = List.copyOf(relatedIds);
    }

    public Reason reason() {
        return reason;
    }

    public String taskId() {
        return taskId;
    }

    public List<String> relatedIds() {
        return relatedIds;
    }
}
