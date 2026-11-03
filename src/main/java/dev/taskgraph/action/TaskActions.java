package dev.taskgraph.action;

import dev.taskgraph.graph.IconKeys;
import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraphException;
import dev.taskgraph.graph.TaskPermissions;
import dev.taskgraph.graph.TaskPermissions.Actor;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.service.TaskService;
import dev.taskgraph.storage.TaskStoreException;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every task change a player can request, shared by commands and dialogs. Each action looks the task up
 * again by id and checks permissions at call time, so a stale view (an old dialog, a deleted task) can't
 * bypass the rules. Results are plain text; free of Paper types so it can be unit tested.
 */
public final class TaskActions {

    static final String FALLBACK_ICON_NOTE = "No tenías nada en la mano, así que el ícono es papel.";
    public static final String PROJECT_GONE = "Ese proyecto ya no existe.";
    static final String HIDE_RESOLVED_NOTE =
            "Una tarea resuelta se oculta de la pestaña cuando todo lo que depende de ella también está resuelto.";

    private final TaskService service;
    private final Consumer<String> announcer;
    private final Logger logger;

    /**
     * @param announcer sends a line to every online player (e.g. when a task is resolved)
     */
    public TaskActions(TaskService service, Consumer<String> announcer, Logger logger) {
        this.service = service;
        this.announcer = announcer;
        this.logger = logger;
    }

    public Optional<Task> findByTitle(String title) {
        return service.findByTitle(title);
    }

    public Optional<Project> findProjectByTitle(String title) {
        return service.findProjectByTitle(title);
    }

    public static String unknownTitle(String title) {
        return "No existe la tarea " + quoted(title) + ".";
    }

    public static String unknownProject(String title) {
        return "No existe el proyecto " + quoted(title) + ".";
    }

    /** For a size that isn't chica, mediana or grande. */
    public static String unknownSize(String input) {
        return "No hay ningún tamaño " + quoted(input.strip()) + ". Usá chica, mediana o grande.";
    }

    /** For an item id that doesn't name an item usable as an icon. */
    public static String unknownIcon(String input) {
        return "No hay ningún ítem " + quoted(input.strip()) + " que sirva de ícono. Usá su id, por ejemplo diamond o minecraft:oak_log.";
    }

    /** Creates the task in General. */
    public ActionResult create(Actor actor, String title, String icon, boolean iconIsFallback) {
        return create(actor, title, icon, iconIsFallback, Project.GENERAL_ID);
    }

    /**
     * @param iconIsFallback whether {@code icon} replaces an empty hand, so the actor is told
     */
    public ActionResult create(Actor actor, String title, String icon, boolean iconIsFallback, String projectId) {
        Optional<Project> project = service.project(projectId);
        if (project.isEmpty()) {
            return ActionResult.failure(null, PROJECT_GONE);
        }
        return run(null, () -> {
            Task task = service.create(title, icon, actor.playerRef().orElse(null), projectId);
            String message = project.get().isGeneral()
                    ? "Tarea " + quoted(task.title()) + " creada."
                    : "Tarea " + quoted(task.title()) + " creada en " + quoted(project.get().title()) + ".";
            return iconIsFallback
                    ? ActionResult.success(task, message, FALLBACK_ICON_NOTE)
                    : ActionResult.success(task, message);
        });
    }

    public ActionResult rename(Actor actor, String taskId, String newTitle) {
        return withTask(taskId, task -> {
            Task renamed = service.rename(task.id(), newTitle);
            return ActionResult.success(renamed, quoted(task.title()) + " ahora se llama " + quoted(renamed.title()) + ".");
        });
    }

    public ActionResult describe(Actor actor, String taskId, String description) {
        return withTask(taskId, task -> ActionResult.success(
                service.describe(task.id(), description),
                "Descripción de " + quoted(task.title()) + " actualizada."));
    }

    /** @param icon namespaced key of an item already checked to work as an icon */
    public ActionResult changeIcon(Actor actor, String taskId, String icon) {
        return withTask(taskId, task -> ActionResult.success(
                service.changeIcon(task.id(), icon),
                "El ícono de " + quoted(task.title()) + " ahora es " + IconKeys.display(icon) + "."));
    }

    /** Open to everyone, like editing the description. */
    public ActionResult changeSize(Actor actor, String taskId, TaskSize size) {
        return withTask(taskId, task -> {
            if (task.size() == size) {
                return ActionResult.success(task, quoted(task.title()) + " ya era " + size.word() + ".");
            }
            return ActionResult.success(service.changeSize(task.id(), size),
                    quoted(task.title()) + " ahora es " + size.word() + ".");
        });
    }

    public ActionResult addDependency(Actor actor, String taskId, String dependencyId) {
        return withTask(taskId, task -> withOther(task, dependencyId, dependency -> {
            Task updated = service.addDependency(task.id(), dependency.id());
            return ActionResult.success(updated, quoted(task.title()) + " ahora depende de " + quoted(dependency.title()) + ".");
        }));
    }

    public ActionResult removeDependency(Actor actor, String taskId, String dependencyId) {
        return withTask(taskId, task -> withOther(task, dependencyId, dependency -> {
            Task updated = service.removeDependency(task.id(), dependency.id());
            String message = quoted(task.title()) + " ya no depende de " + quoted(dependency.title()) + ".";
            if (!updated.dependencies().isEmpty()) {
                return ActionResult.success(updated, message);
            }
            return ActionResult.success(updated, message, "En la pestaña ahora cuelga directamente del raíz.");
        }));
    }

    public ActionResult assignToSelf(Actor actor, String taskId) {
        Optional<PlayerRef> self = actor.playerRef();
        if (self.isEmpty()) {
            return ActionResult.failure(null, "Solo un jugador puede asignarse una tarea.");
        }
        return withTask(taskId, task -> {
            if (!TaskPermissions.canAssignToSelf(task, actor)) {
                String current = task.assigneeOptional().map(PlayerRef::name).orElse("otra persona");
                return ActionResult.failure(task, quoted(task.title()) + " ya está asignada a " + current
                        + ": tiene que soltarla o un admin la reasigna.");
            }
            Task updated = service.assign(task.id(), self.get(), actor.admin());
            String message = "Te asignaste la tarea " + quoted(task.title()) + ".";
            // task is the state before the change, so this reports who had it.
            return task.assigneeOptional()
                    .filter(previous -> !previous.is(self.get().uuid()))
                    .map(previous -> ActionResult.success(updated, message, "Antes estaba asignada a " + previous.name() + "."))
                    .orElseGet(() -> ActionResult.success(updated, message));
        });
    }

    public ActionResult release(Actor actor, String taskId) {
        return withTask(taskId, task -> {
            if (task.assigneeOptional().isPresent() && !TaskPermissions.canRelease(task, actor)) {
                return ActionResult.failure(task, "Solo " + task.assignee().name() + " o un admin pueden soltar "
                        + quoted(task.title()) + ".");
            }
            Task updated = service.unassign(task.id());
            boolean own = actor.uuid() != null && task.assignee().is(actor.uuid());
            return ActionResult.success(updated, own
                    ? "Soltaste la tarea " + quoted(task.title()) + "."
                    : quoted(task.title()) + " ya no está asignada a " + task.assignee().name() + ".");
        });
    }

    /** The announcement to everyone replaces a personal message. */
    public ActionResult resolve(Actor actor, String taskId) {
        return withTask(taskId, task -> {
            Task resolved = service.resolve(task.id());
            announcer.accept("✔ " + actor.name() + " resolvió la tarea " + quoted(task.title()));
            return ActionResult.success(resolved, null);
        });
    }

    public ActionResult moveTask(Actor actor, String taskId, String projectId) {
        return withTask(taskId, task -> withProject(task, projectId, project -> ActionResult.success(
                service.moveTask(task.id(), project.id()),
                quoted(task.title()) + " ahora está en el proyecto " + quoted(project.title()) + ".")));
    }

    /** Project actions report no task: {@link ActionResult#task()} is always {@code null}. */
    public ActionResult createProject(Actor actor, String title) {
        return run(null, () -> {
            Project project = service.createProject(title);
            return ActionResult.success(null, "Proyecto " + quoted(project.title()) + " creado.",
                    "Tiene su propia pestaña en la pantalla de logros.");
        });
    }

    public ActionResult renameProject(Actor actor, String projectId, String newTitle) {
        return withProject(null, projectId, project -> {
            Project renamed = service.renameProject(project.id(), newTitle);
            return ActionResult.success(null, "El proyecto " + quoted(project.title()) + " ahora se llama " + quoted(renamed.title()) + ".");
        });
    }

    /** Open to everyone, like renaming: it only changes what the project's tab shows. */
    public ActionResult setHideResolved(Actor actor, String projectId, boolean hide) {
        return withProject(null, projectId, project -> {
            if (project.hideResolved() == hide) {
                return ActionResult.success(null, quoted(project.title()) + (hide
                        ? " ya ocultaba las ramas terminadas."
                        : " ya mostraba todas las tareas resueltas."));
            }
            service.setHideResolved(project.id(), hide);
            return hide
                    ? ActionResult.success(null, quoted(project.title()) + " ahora oculta las ramas terminadas.", HIDE_RESOLVED_NOTE)
                    : ActionResult.success(null, quoted(project.title()) + " ahora muestra todas las tareas resueltas.");
        });
    }

    public ActionResult removeProject(Actor actor, String projectId) {
        return withProject(null, projectId, project -> {
            if (!TaskPermissions.canRemoveProject(actor)) {
                return ActionResult.failure(null, "Solo un admin puede borrar proyectos.");
            }
            service.removeProject(project.id());
            return ActionResult.success(null, "Proyecto " + quoted(project.title()) + " borrado.");
        });
    }

    public ActionResult remove(Actor actor, String taskId) {
        return withTask(taskId, task -> {
            if (!TaskPermissions.canRemove(task, actor)) {
                return ActionResult.failure(task, task.creatorOptional()
                        .map(creator -> "Solo " + creator.name() + " (quien la creó) o un admin pueden borrar " + quoted(task.title()) + ".")
                        .orElse("Solo un admin puede borrar " + quoted(task.title()) + "."));
            }
            service.remove(task.id());
            return ActionResult.success(null, "Tarea " + quoted(task.title()) + " borrada.");
        });
    }

    private ActionResult withTask(String taskId, Function<Task, ActionResult> action) {
        Optional<Task> task = service.task(taskId);
        if (task.isEmpty()) {
            return ActionResult.failure(null, "Esa tarea ya no existe.");
        }
        return run(task.get(), () -> action.apply(task.get()));
    }

    /** @param task reported with the result when the project no longer exists, may be {@code null} */
    private ActionResult withProject(Task task, String projectId, Function<Project, ActionResult> action) {
        Optional<Project> project = service.project(projectId);
        if (project.isEmpty()) {
            return ActionResult.failure(task, PROJECT_GONE);
        }
        return run(task, () -> action.apply(project.get()));
    }

    private ActionResult withOther(Task task, String otherId, Function<Task, ActionResult> action) {
        return service.task(otherId)
                .map(action)
                .orElseGet(() -> ActionResult.failure(task, "Esa tarea ya no existe."));
    }

    /** Turns graph rejections and save failures into failed results; {@code task} is reported with them. */
    private ActionResult run(Task task, Supplier<ActionResult> action) {
        try {
            return action.get();
        } catch (TaskGraphException exception) {
            return ActionResult.failure(task, RejectionMessages.of(exception, this::titleOf, this::projectTitleOf));
        } catch (TaskStoreException exception) {
            logger.log(Level.SEVERE, "Could not save tasks", exception);
            return ActionResult.failure(task, "No se pudo guardar el cambio. Revisá el log del servidor.");
        }
    }

    private String titleOf(String id) {
        return service.task(id).map(Task::title).orElse(id);
    }

    private String projectTitleOf(String id) {
        return service.project(id).map(Project::title).orElse(id);
    }

    static String quoted(String title) {
        return "\"" + title + "\"";
    }
}
