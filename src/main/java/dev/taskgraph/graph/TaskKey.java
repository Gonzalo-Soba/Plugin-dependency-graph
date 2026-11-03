package dev.taskgraph.graph;

import java.util.Comparator;
import java.util.Objects;

/**
 * Where a task's advancement progress lives: each project is its own tab, so a task that moves to
 * another project gets a new key and the old one has to be cleared.
 *
 * @param project project id
 * @param task    task id
 */
public record TaskKey(String project, String task) implements Comparable<TaskKey> {

    private static final Comparator<TaskKey> ORDER = Comparator.comparing(TaskKey::project).thenComparing(TaskKey::task);

    public TaskKey {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(task, "task");
    }

    @Override
    public int compareTo(TaskKey other) {
        return ORDER.compare(this, other);
    }
}
