package dev.taskgraph.action;

import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.graph.TaskStatus;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plain-text descriptions shared by {@code /tarea info} and the task dialogs.
 */
public final class TaskTexts {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TaskTexts() {
    }

    /**
     * "Resuelta por Steve el 03/11/2026", leaving out whatever wasn't recorded ("Resuelta el 03/11/2026" for
     * the console); empty for unresolved tasks and for those resolved before this was recorded.
     *
     * @param zone time zone the date is shown in
     */
    public static Optional<String> resolution(Task task, ZoneId zone) {
        if (task.resolvedBy() == null && task.resolvedAt() == null) {
            return Optional.empty();
        }
        return Optional.of("Resuelta"
                + task.resolvedByOptional().map(by -> " por " + by.name()).orElse("")
                + task.resolvedAtOptional().map(at -> " el " + DATE.format(at.atZone(zone))).orElse(""));
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

    /**
     * Announcement for tasks a change unlocked: "▶ Ahora se puede hacer: "Horno"." or, for several,
     * "▶ Ahora se pueden hacer: ...". Tasks outside General name their project.
     */
    public static String unlocked(TaskGraph graph, List<Task> tasks) {
        List<String> names = tasks.stream()
                .map(task -> "\"" + task.title() + "\"" + (Project.GENERAL_ID.equals(task.project())
                        ? ""
                        : " (en " + graph.project(task.project()).map(Project::title).orElse(task.project()) + ")"))
                .toList();
        return marker(TaskStatus.AVAILABLE) + (names.size() == 1 ? " Ahora se puede hacer: " : " Ahora se pueden hacer: ")
                + String.join(", ", names) + ".";
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
