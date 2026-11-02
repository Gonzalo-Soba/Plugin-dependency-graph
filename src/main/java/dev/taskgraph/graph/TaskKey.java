package dev.taskgraph.graph;

import java.util.Objects;

/**
 * Where a task's advancement progress lives: each project is its own tab, so a task that moves to
 * another project gets a new key and the old one has to be cleared.
 *
 * @param project project id
 * @param task    task id
 */
public record TaskKey(String project, String task) {

    public TaskKey {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(task, "task");
    }
}
