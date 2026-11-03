package dev.taskgraph.action;

import dev.taskgraph.graph.TaskGraphException;

import java.util.List;
import java.util.function.Function;

/**
 * Player-facing text for rejected graph operations. Exceptions carry internal ids, which players never
 * see, so every id is shown through {@code titleOf}. Kept free of Paper types so it can be unit tested.
 */
public final class RejectionMessages {

    private RejectionMessages() {
    }

    public static String of(TaskGraphException exception, Function<String, String> titleOf) {
        return of(exception, titleOf, Function.identity());
    }

    /** @param projectTitleOf title of a project id, for the project reasons */
    public static String of(TaskGraphException exception, Function<String, String> titleOf,
                            Function<String, String> projectTitleOf) {
        String task = quoted(titleOf.apply(exception.taskId()));
        String project = quoted(projectTitleOf.apply(exception.taskId()));
        List<String> related = exception.relatedIds().stream().map(titleOf).toList();
        return switch (exception.reason()) {
            case INVALID_ID -> "Id interno inválido: " + task + ".";
            case INVALID_TITLE -> "El título no puede estar vacío.";
            case DUPLICATE_ID -> "Id interno repetido: " + task + ".";
            case DUPLICATE_TITLE -> "Ya existe una tarea llamada " + task + ".";
            case NOT_FOUND -> "No existe la tarea " + task + ".";
            case SELF_DEPENDENCY -> "Una tarea no puede depender de sí misma.";
            case DUPLICATE_DEPENDENCY -> task + " ya depende de " + list(related) + ".";
            case NOT_A_DEPENDENCY -> task + " no depende de " + list(related) + ".";
            case CYCLE -> "Esa dependencia crea un ciclo: "
                    + String.join(" → ", related.stream().map(RejectionMessages::quoted).toList()) + ".";
            case RESOLVED_TASK_DEPENDENCY -> task + " ya está resuelta y no puede depender de una tarea pendiente ("
                    + list(related) + ").";
            case ALREADY_ASSIGNED -> task + " ya está asignada a otra persona: tiene que soltarla o un admin la reasigna.";
            case NOT_ASSIGNED -> task + " no está asignada a nadie.";
            case ALREADY_RESOLVED -> task + " ya está resuelta.";
            case UNRESOLVED_DEPENDENCIES -> "No se puede resolver " + task + ": faltan " + list(related) + ".";
            case HAS_DEPENDENTS -> "No se puede borrar " + task + ": dependen de ella " + list(related) + ".";
            case NOT_RESOLVED -> task + " no está resuelta.";
            case RESOLVED_DEPENDENTS -> "No se puede reabrir " + task + ": dependen de ella " + list(related)
                    + ", que ya están resueltas. Reabrilas primero.";
            case PROJECT_NOT_FOUND -> "No existe el proyecto " + project + ".";
            case DUPLICATE_PROJECT_TITLE -> "Ya existe un proyecto llamado " + project + ".";
            case GENERAL_PROJECT -> "El proyecto " + project + " no se puede borrar.";
            case PROJECT_NOT_EMPTY -> "No se puede borrar el proyecto " + project + ": todavía tiene tareas ("
                    + list(related) + "). Movelas o borralas primero.";
            case ALREADY_IN_PROJECT -> task + " ya está en el proyecto "
                    + quoted(projectTitleOf.apply(exception.relatedIds().getFirst())) + ".";
        };
    }

    private static String list(List<String> titles) {
        return String.join(", ", titles.stream().map(RejectionMessages::quoted).toList());
    }

    private static String quoted(String title) {
        return "\"" + title + "\"";
    }
}
