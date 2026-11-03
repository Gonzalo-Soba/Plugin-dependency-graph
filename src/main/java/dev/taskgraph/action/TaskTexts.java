package dev.taskgraph.action;

import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.graph.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plain-text descriptions shared by {@code /tarea info} and the task dialogs.
 */
public final class TaskTexts {

    private TaskTexts() {
    }

    /** "✔ Madera, ✘ Piedra", or "nada" when empty. Ids that no longer exist are shown as is. */
    public static String taskList(TaskGraph graph, List<String> ids) {
        if (ids.isEmpty()) {
            return "nada";
        }
        return String.join(", ", ids.stream()
                .map(id -> graph.get(id)
                        .map(task -> (task.resolved() ? "✔ " : "✘ ") + task.title())
                        .orElse(id))
                .toList());
    }

    /**
     * "También requiere: Madera, Muralla (en Base)" for the dependencies other than the visual parent,
     * naming the project of those outside the task's own; empty when there are none.
     *
     * @param drawnIds tasks drawn in the task's tab
     */
    public static Optional<String> alsoRequires(TaskGraph graph, Task task, Set<String> drawnIds) {
        List<String> ids = task.extraRequirements(drawnIds);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = ids.stream()
                .map(id -> graph.get(id).map(dependency -> requirementName(graph, task, dependency)).orElse(id))
                .toList();
        return Optional.of("También requiere: " + String.join(", ", names));
    }

    private static String requirementName(TaskGraph graph, Task task, Task dependency) {
        if (dependency.project().equals(task.project())) {
            return dependency.title();
        }
        String project = graph.project(dependency.project()).map(Project::title).orElse(dependency.project());
        return dependency.title() + " (en " + project + ")";
    }

    /** "Chica", "Mediana" or "Grande". */
    public static String size(TaskSize size) {
        return Character.toUpperCase(size.word().charAt(0)) + size.word().substring(1);
    }

    public static String status(TaskStatus status) {
        return switch (status) {
            case AVAILABLE -> "Disponible";
            case BLOCKED -> "Bloqueada";
            case RESOLVED -> "Resuelta";
        };
    }

    /** Short marker used in the task list: ▶ available, ✘ blocked, ✔ resolved. */
    public static String marker(TaskStatus status) {
        return switch (status) {
            case AVAILABLE -> "▶";
            case BLOCKED -> "✘";
            case RESOLVED -> "✔";
        };
    }
}
