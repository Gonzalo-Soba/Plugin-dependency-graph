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
        return taskLinks(graph, ids).text();
    }

    /** {@link #taskList} with each existing task as a link. */
    public static LinkedText taskLinks(TaskGraph graph, List<String> ids) {
        LinkedText.Builder text = LinkedText.builder();
        if (ids.isEmpty()) {
            return text.text("nada").build();
        }
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                text.text(", ");
            }
            String id = ids.get(i);
            Optional<Task> task = graph.get(id);
            if (task.isPresent()) {
                text.link(id, (task.get().resolved() ? "✔ " : "✘ ") + task.get().title());
            } else {
                text.text(id);
            }
        }
        return text.build();
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
    public static LinkedText unlocked(TaskGraph graph, List<Task> tasks) {
        LinkedText.Builder text = LinkedText.builder()
                .text(marker(TaskStatus.AVAILABLE) + (tasks.size() == 1 ? " Ahora se puede hacer: " : " Ahora se pueden hacer: "));
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            text.text(i > 0 ? ", " : "").quoted(task);
            if (!Project.GENERAL_ID.equals(task.project())) {
                text.text(" (en " + graph.project(task.project()).map(Project::title).orElse(task.project()) + ")");
            }
        }
        return text.text(".").build();
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
