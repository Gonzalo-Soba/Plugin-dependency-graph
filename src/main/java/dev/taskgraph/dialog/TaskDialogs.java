package dev.taskgraph.dialog;

import dev.taskgraph.action.ActionResult;
import dev.taskgraph.action.TaskActions;
import dev.taskgraph.action.TaskTexts;
import dev.taskgraph.advancement.TaskIcons;
import dev.taskgraph.command.Senders;
import dev.taskgraph.graph.IconKeys;
import dev.taskgraph.graph.MainAction;
import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskPermissions;
import dev.taskgraph.graph.TaskPermissions.Actor;
import dev.taskgraph.graph.TaskSearch;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.graph.TaskStatus;
import dev.taskgraph.graph.Titles;
import dev.taskgraph.service.TaskService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The task screens: projects, the task list of a project, task detail, create, edit, add/remove dependency,
 * move to another project, delete confirmation, and creating, renaming and deleting projects.
 * <p>
 * Buttons only carry internal ids. Every click goes through {@link TaskActions}, which looks the task or
 * project up again and re-checks permissions, so a dialog left open while someone else changes things can't
 * break any rule. Each callback works once and expires after a few minutes. Dialogs wait for the server after
 * a click, and every callback ends by opening the next screen or closing the dialog.
 */
public final class TaskDialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private static final String TASK_GONE = "Esa tarea ya no existe.";
    private static final String TITLE_INPUT = "titulo";
    private static final String DESCRIPTION_INPUT = "descripcion";
    private static final String TASK_INPUT = "tarea";
    private static final String PROJECT_INPUT = "proyecto";
    private static final String SIZE_INPUT = "tamano";
    private static final String SEARCH_INPUT = "buscar";
    private static final int TITLE_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 512;
    private static final int LIST_COLUMNS = 2;
    private static final int LIST_BUTTON_WIDTH = 150;
    private static final int ACTION_BUTTON_WIDTH = 200;

    private final Plugin plugin;
    private final TaskService service;
    private final TaskActions actions;
    private final IconPicker iconPicker;

    public TaskDialogs(Plugin plugin, TaskService service, TaskActions actions, IconPicker iconPicker) {
        this.plugin = plugin;
        this.service = service;
        this.actions = actions;
        this.iconPicker = iconPicker;
    }

    /** Entry point of the interface: the project list. */
    public void openList(Player player) {
        openProjects(player, null);
    }

    /**
     * "Nueva tarea" in a project, from a shortcut (the "+ Tarea" tab or the quick access dialog). Cancel goes
     * back to the game, where the player came from; the server can't reopen the advancements screen.
     */
    public void openNewTask(Player player, String projectId) {
        openCreate(player, projectId, Player::closeInventory, null, "", "", "");
    }

    /** "Nuevo proyecto" from the "+ Proyecto" tab; cancel goes back to the game, as with {@link #openNewTask}. */
    public void openNewProject(Player player) {
        openCreateProject(player, Player::closeInventory, null, "");
    }

    // ---- Projects ----

    private void openProjects(Player player, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("Elegí un proyecto. Cada uno es una pestaña en la pantalla de logros.", NamedTextColor.GRAY)));

        // Fixed first row, as in a project's list: both buttons as wide as the projects.
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(DialogIcons.label(DialogIcons.SEARCH, "Buscar tarea", NamedTextColor.AQUA),
                Component.text("Por parte del título, en todos los proyectos", NamedTextColor.GRAY), LIST_BUTTON_WIDTH,
                onClick(viewer -> openSearch(viewer, null, ""))));
        buttons.add(button(DialogIcons.label(DialogIcons.NEW, "Nuevo proyecto", NamedTextColor.GOLD), null, LIST_BUTTON_WIDTH,
                onClick(viewer -> openCreateProject(viewer, back -> openProjects(back, null), null, ""))));
        for (Project project : graph.projects()) {
            List<Task> tasks = graph.tasksIn(project.id());
            long resolved = tasks.stream().filter(Task::resolved).count();
            long available = tasks.stream().filter(task -> graph.status(task.id()) == TaskStatus.AVAILABLE).count();
            Component tooltip = Component.join(JoinConfiguration.newlines(),
                    Component.text(resolved + " de " + tasks.size() + " resueltas", NamedTextColor.GRAY),
                    Component.text(available + " disponibles", NamedTextColor.GREEN));
            buttons.add(button(Component.text(project.title() + " (" + resolved + "/" + tasks.size() + ")", NamedTextColor.WHITE),
                    tooltip, LIST_BUTTON_WIDTH, onClick(viewer -> openTasks(viewer, project.id(), null))));
        }

        show(player, Component.text("Tareas del servidor"), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        // closeDialog() doesn't dismiss the client's "waiting for server" screen that follows the
                        // click (it only closes dialog screens), leaving it up until it times out. closeInventory()
                        // closes whatever screen is open.
                        .exitAction(button(Component.text("Cerrar"), null, ACTION_BUTTON_WIDTH, onClick(Player::closeInventory)))
                        .build());
    }

    // ---- Search ----

    private void openSearch(Player player, ActionResult feedback, String typed) {
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("Escribí parte del título. No importan mayúsculas ni tildes.", NamedTextColor.GRAY)));
        TextDialogInput input = DialogInput.text(SEARCH_INPUT, Component.text("Buscar"))
                .initial(typed)
                .maxLength(TITLE_MAX_LENGTH)
                .width(300)
                .build();

        ActionButton search = actionButton("Buscar", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String text = textOf(response, SEARCH_INPUT);
            if (text.isBlank()) {
                openSearch(viewer, failure("Escribí algo para buscar."), text);
                return;
            }
            List<Task> found = TaskSearch.find(service.snapshot(), text);
            if (found.isEmpty()) {
                openSearch(viewer, failure("Ninguna tarea tiene " + quoted(Titles.clean(text)) + " en el título."), text);
            } else if (found.size() == 1) {
                openDetail(viewer, found.getFirst().id(), null);
            } else {
                openSearchResults(viewer, text);
            }
        }));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openProjects(viewer, null)));

        show(player, Component.text("Buscar tarea"), body, List.of(input), DialogType.confirmation(search, back));
    }

    /** Searches again when shown, so the results are never stale. */
    private void openSearchResults(Player player, String text) {
        TaskGraph graph = service.snapshot();
        List<Task> found = TaskSearch.find(graph, text);
        if (found.size() < 2) {
            // Something changed since the search: fall back to the single result or the search screen.
            if (found.size() == 1) {
                openDetail(player, found.getFirst().id(), null);
            } else {
                openSearch(player, failure("Ninguna tarea tiene " + quoted(Titles.clean(text)) + " en el título."), text);
            }
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (Task task : found) {
            TaskStatus status = graph.status(task.id());
            Component tooltip = Component.join(JoinConfiguration.newlines(),
                    Component.text(TaskTexts.status(status), color(status)),
                    Component.text("Proyecto: " + projectTitle(graph, task.project()), NamedTextColor.GRAY));
            buttons.add(button(Component.text(TaskTexts.marker(status) + " " + task.title(), color(status)),
                    tooltip, LIST_BUTTON_WIDTH, onClick(viewer -> openDetail(viewer, task.id(), null))));
        }
        show(player, Component.text("Buscar: " + Titles.clean(text)),
                List.of(text(Component.text(found.size() + " tareas. El proyecto de cada una está en su tooltip.", NamedTextColor.GRAY))),
                List.of(),
                DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH, onClick(viewer -> openSearch(viewer, null, text))))
                        .build());
    }

    /** @param back where «Cancelar» leads */
    private void openCreateProject(Player player, Consumer<Player> back, ActionResult feedback, String title) {
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("El proyecto tiene su propia pestaña en la pantalla de logros.", NamedTextColor.GRAY)));

        ActionButton create = actionButton("Crear", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String newTitle = textOf(response, TITLE_INPUT);
            ActionResult result = actions.createProject(Senders.actor(viewer), newTitle);
            Optional<Project> created = result.success() ? service.findProjectByTitle(newTitle) : Optional.empty();
            if (created.isEmpty()) {
                openCreateProject(viewer, back, result, newTitle);
                return;
            }
            openTasks(viewer, created.get().id(), result);
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(back));

        show(player, Component.text("Nuevo proyecto"), body, List.of(titleInput(title)), DialogType.confirmation(create, cancel));
    }

    private void openRenameProject(Player player, String projectId, ActionResult feedback, String title) {
        ActionButton save = actionButton("Guardar", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            Optional<Project> current = service.project(projectId);
            if (current.isEmpty()) {
                openProjects(viewer, failure(TaskActions.PROJECT_GONE));
                return;
            }
            String newTitle = textOf(response, TITLE_INPUT);
            if (Titles.clean(newTitle).equals(current.get().title())) {
                openTasks(viewer, projectId, new ActionResult(true, null, "No había cambios.", List.of()));
                return;
            }
            ActionResult result = actions.renameProject(Senders.actor(viewer), projectId, newTitle);
            if (result.success()) {
                openTasks(viewer, projectId, result);
            } else {
                openRenameProject(viewer, projectId, result, newTitle);
            }
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openProjectOptions(viewer, projectId, null)));

        show(player, Component.text("Renombrar proyecto"), feedback(feedback), List.of(titleInput(title)),
                DialogType.confirmation(save, cancel));
    }

    private void openConfirmRemoveProject(Player player, String projectId) {
        Optional<Project> project = service.project(projectId);
        if (project.isEmpty()) {
            openProjects(player, failure(TaskActions.PROJECT_GONE));
            return;
        }
        ActionButton yes = actionButton("Sí, borrar", NamedTextColor.RED, onClick(viewer -> {
            ActionResult result = actions.removeProject(Senders.actor(viewer), projectId);
            if (result.success()) {
                openProjects(viewer, result);
            } else {
                openProjectOptions(viewer, projectId, result);
            }
        }));
        ActionButton no = actionButton("No", NamedTextColor.WHITE, onClick(viewer -> openProjectOptions(viewer, projectId, null)));

        show(player, Component.text("Borrar proyecto"),
                List.of(text(Component.text("¿Borrar el proyecto " + quoted(project.get().title())
                        + "? Solo se puede si ya no tiene tareas.", NamedTextColor.YELLOW))),
                List.of(), DialogType.confirmation(yes, no));
    }

    // ---- Task list of a project ----

    private void openTasks(Player player, String projectId, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        Optional<Project> found = graph.project(projectId);
        if (found.isEmpty()) {
            openProjects(player, failure(TaskActions.PROJECT_GONE));
            return;
        }
        Project project = found.get();
        List<Task> tasks = graph.listOrder().stream().filter(task -> task.project().equals(projectId)).toList();

        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        if (tasks.isEmpty()) {
            body.add(text(Component.text("Todavía no hay tareas en este proyecto. Creá la primera con «Nueva tarea».", NamedTextColor.GRAY)));
        } else {
            long resolved = tasks.stream().filter(Task::resolved).count();
            body.add(text(Component.text(resolved + " de " + tasks.size() + " resueltas   ·   "
                    + TaskTexts.marker(TaskStatus.AVAILABLE) + " disponible   "
                    + TaskTexts.marker(TaskStatus.BLOCKED) + " bloqueada   "
                    + TaskTexts.marker(TaskStatus.RESOLVED) + " resuelta", NamedTextColor.GRAY)));
        }
        int hidden = graph.hiddenTasksIn(projectId).size();
        if (hidden > 0) {
            body.add(text(Component.text((hidden == 1 ? "1 resuelta oculta" : hidden + " resueltas ocultas")
                    + " en la pestaña de logros. Acá se listan igual.", NamedTextColor.DARK_GRAY)));
        }

        // Fixed first row, as wide as the tasks: the dialog grid can't fit a row of narrower buttons.
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(DialogIcons.label(DialogIcons.NEW, "Nueva tarea", NamedTextColor.GOLD), null, LIST_BUTTON_WIDTH,
                onClick(viewer -> openCreate(viewer, projectId, back -> openTasks(back, projectId, null), null, "", "", ""))));
        buttons.add(button(Component.text("⚙ Opciones", NamedTextColor.WHITE),
                Component.text("Renombrar el proyecto, ocultar resueltas" + (canRemove(project, player) ? ", borrar" : ""),
                        NamedTextColor.GRAY),
                LIST_BUTTON_WIDTH, onClick(viewer -> openProjectOptions(viewer, projectId, null))));
        for (Task task : tasks) {
            TaskStatus status = graph.status(task.id());
            Component tooltip = Component.join(JoinConfiguration.newlines(),
                    Component.text(TaskTexts.status(status), color(status)),
                    Component.text("Asignada a: " + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA));
            buttons.add(button(Component.text(TaskTexts.marker(status) + " " + task.title(), color(status)),
                    tooltip, LIST_BUTTON_WIDTH, onClick(viewer -> openDetail(viewer, task.id(), null))));
        }

        show(player, Component.text(project.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH, onClick(viewer -> openProjects(viewer, null))))
                        .build());
    }

    /** The project's settings, on their own screen so the task list only lists tasks. */
    private void openProjectOptions(Player player, String projectId, ActionResult feedback) {
        Optional<Project> found = service.project(projectId);
        if (found.isEmpty()) {
            openProjects(player, failure(TaskActions.PROJECT_GONE));
            return;
        }
        Project project = found.get();

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(DialogIcons.label(DialogIcons.RENAME, "Renombrar proyecto", NamedTextColor.WHITE), null,
                ACTION_BUTTON_WIDTH, onClick(viewer -> openRenameProject(viewer, projectId, null, project.title()))));
        boolean hide = !project.hideResolved();
        buttons.add(button(DialogIcons.label(DialogIcons.VISIBILITY, hide ? "Ocultar resueltas" : "Mostrar resueltas", NamedTextColor.WHITE),
                Component.text(hide
                        ? "Saca de la pestaña las tareas resueltas de las que ya no depende nada pendiente."
                        : "Vuelve a mostrar en la pestaña todas las tareas resueltas.", NamedTextColor.GRAY),
                ACTION_BUTTON_WIDTH,
                onClick(viewer -> openProjectOptions(viewer, projectId, actions.setHideResolved(Senders.actor(viewer), projectId, hide)))));
        if (canRemove(project, player)) {
            buttons.add(button(DialogIcons.label(DialogIcons.DELETE, "Borrar proyecto", NamedTextColor.RED),
                    Component.text("Solo se puede si ya no tiene tareas.", NamedTextColor.GRAY),
                    ACTION_BUTTON_WIDTH, onClick(viewer -> openConfirmRemoveProject(viewer, projectId))));
        }

        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text(project.hideResolved()
                ? "La pestaña oculta las ramas terminadas."
                : "La pestaña muestra todas las tareas.", NamedTextColor.GRAY)));
        show(player, Component.text("Opciones de " + project.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH, onClick(viewer -> openTasks(viewer, projectId, null))))
                        .build());
    }

    /** Only a hint for what to show; {@link TaskActions#removeProject} checks again when clicked. */
    private static boolean canRemove(Project project, Player player) {
        return !project.isGeneral() && TaskPermissions.canRemoveProject(Senders.actor(player));
    }

    // ---- Detail ----

    private void openDetail(Player player, String taskId, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        Optional<Task> found = graph.get(taskId);
        if (found.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        Task task = found.get();
        TaskStatus status = graph.status(taskId);
        Actor actor = Senders.actor(player);

        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        Component description = task.description().isEmpty()
                ? Component.text("Sin descripción.", NamedTextColor.DARK_GRAY)
                : Component.text(task.description(), NamedTextColor.GRAY);
        body.add(DialogBody.item(TaskIcons.of(task.icon())).description(DialogBody.plainMessage(description)).build());
        // Only what decides the next step; the rest is under «Detalles».
        List<Component> info = new ArrayList<>();
        info.add(Component.text(TaskTexts.marker(status) + " " + TaskTexts.status(status), color(status)));
        TaskTexts.resolution(task, ZoneId.systemDefault()).ifPresent(line -> info.add(Component.text(line, NamedTextColor.GREEN)));
        if (status == TaskStatus.BLOCKED) {
            info.add(Component.text("Falta: " + TaskTexts.taskList(graph, pendingDependencies(graph, task)), NamedTextColor.YELLOW));
        }
        if (!task.resolved()) {
            info.add(Component.text("Asignada a: " + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA));
        }
        body.add(text(Component.join(JoinConfiguration.newlines(), info)));

        List<ActionButton> buttons = new ArrayList<>();
        mainAction(task, MainAction.of(task, status, actor)).ifPresent(buttons::add);
        if (task.assigneeOptional().isPresent() && !task.resolved() && TaskPermissions.canRelease(task, actor)) {
            buttons.add(actionButton("Soltar", NamedTextColor.AQUA,
                    onClick(viewer -> openDetail(viewer, taskId, actions.release(Senders.actor(viewer), taskId)))));
        }
        buttons.add(button(DialogIcons.label(DialogIcons.RENAME, "Editar ▸", NamedTextColor.WHITE),
                Component.text("Título, descripción, ícono, tamaño" + (graph.projects().size() > 1 ? ", proyecto" : ""), NamedTextColor.GRAY),
                ACTION_BUTTON_WIDTH, onClick(viewer -> openEditMenu(viewer, taskId))));
        if (!task.dependencies().isEmpty() || !graph.dependencyCandidates(taskId).isEmpty()) {
            buttons.add(button(Component.text("Dependencias ▸", NamedTextColor.WHITE), dependencySummary(graph, task),
                    ACTION_BUTTON_WIDTH, onClick(viewer -> openDependencies(viewer, taskId, null))));
        }
        buttons.add(button(Component.text("Detalles ▸", NamedTextColor.WHITE),
                Component.text("Proyecto, tamaño, quién la creó y qué tareas la necesitan", NamedTextColor.GRAY),
                ACTION_BUTTON_WIDTH, onClick(viewer -> openDetails(viewer, taskId))));
        if (TaskPermissions.canRemove(task, actor)) {
            buttons.add(button(DialogIcons.label(DialogIcons.DELETE, "Borrar", NamedTextColor.RED), null,
                    ACTION_BUTTON_WIDTH, onClick(viewer -> openConfirmRemove(viewer, taskId))));
        }

        show(player, Component.text(task.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH,
                                onClick(viewer -> openTasks(viewer, task.project(), null))))
                        .build());
    }

    private Optional<ActionButton> mainAction(Task task, MainAction action) {
        String taskId = task.id();
        return switch (action) {
            case REOPEN -> Optional.of(actionButton("↺ Reabrir", NamedTextColor.YELLOW, onClick(viewer -> {
                ActionResult result = actions.reopen(Senders.actor(viewer), taskId);
                // Reopening and resolving announce to everyone instead of replying, so the dialog says it.
                openDetail(viewer, taskId, result.success()
                        ? new ActionResult(true, result.task(), "Reabriste " + quoted(task.title()) + ".", List.of())
                        : result);
            })));
            case RESOLVE -> Optional.of(actionButton("✔ Resolver", NamedTextColor.GREEN, onClick(viewer -> {
                ActionResult result = actions.resolve(Senders.actor(viewer), taskId);
                openDetail(viewer, taskId, result.success()
                        ? new ActionResult(true, result.task(), "Resolviste " + quoted(task.title()) + ".", List.of())
                        : result);
            })));
            case ASSIGN, REASSIGN -> Optional.of(actionButton(action == MainAction.ASSIGN ? "Asignarme" : "Reasignarme",
                    NamedTextColor.AQUA,
                    onClick(viewer -> openDetail(viewer, taskId, actions.assignToSelf(Senders.actor(viewer), taskId)))));
            case NONE -> Optional.empty();
        };
    }

    private static List<String> pendingDependencies(TaskGraph graph, Task task) {
        return task.dependencies().stream()
                .filter(id -> graph.get(id).filter(Task::resolved).isEmpty())
                .toList();
    }

    private static Component dependencySummary(TaskGraph graph, Task task) {
        return Component.join(JoinConfiguration.newlines(),
                Component.text("Depende de: " + TaskTexts.taskList(graph, task.dependencies()), NamedTextColor.GRAY),
                Component.text("La necesitan: " + TaskTexts.taskList(graph, graph.dependents(task.id())), NamedTextColor.GRAY));
    }

    private void openEditMenu(Player player, String taskId) {
        TaskGraph graph = service.snapshot();
        Optional<Task> found = graph.get(taskId);
        if (found.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        Task task = found.get();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(actionButton("Título y descripción", NamedTextColor.WHITE,
                onClick(viewer -> openEdit(viewer, taskId, null, task.title(), task.description()))));
        buttons.add(button(Component.text("Ícono"), Component.text("Ahora: " + IconKeys.display(task.icon()), NamedTextColor.GRAY),
                ACTION_BUTTON_WIDTH, onClick(viewer -> openIcon(viewer, taskId))));
        buttons.add(button(Component.text("Tamaño"), Component.text("Ahora: " + TaskTexts.size(task.size()), NamedTextColor.GRAY),
                ACTION_BUTTON_WIDTH, onClick(viewer -> openSize(viewer, taskId))));
        if (graph.projects().size() > 1) {
            buttons.add(button(Component.text("Mover a otro proyecto"),
                    Component.text("Ahora en " + projectTitle(graph, task.project()), NamedTextColor.GRAY),
                    ACTION_BUTTON_WIDTH, onClick(viewer -> openMove(viewer, taskId))));
        }
        show(player, Component.text("Editar " + task.title()), List.of(), List.of(),
                DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null))))
                        .build());
    }

    private void openDependencies(Player player, String taskId, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        Optional<Task> found = graph.get(taskId);
        if (found.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        Task task = found.get();
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(dependencySummary(graph, task)));

        List<ActionButton> buttons = new ArrayList<>();
        if (!graph.dependencyCandidates(taskId).isEmpty()) {
            buttons.add(actionButton("Agregar dependencia", NamedTextColor.WHITE,
                    onClick(viewer -> openAddDependency(viewer, taskId, null))));
        }
        if (!task.dependencies().isEmpty()) {
            buttons.add(actionButton("Quitar dependencia", NamedTextColor.WHITE,
                    onClick(viewer -> openRemoveDependency(viewer, taskId))));
        }
        if (buttons.isEmpty()) {
            openDetail(player, taskId, feedback);
            return;
        }
        show(player, Component.text("Dependencias de " + task.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null))))
                        .build());
    }

    private void openDetails(Player player, String taskId) {
        TaskGraph graph = service.snapshot();
        Optional<Task> found = graph.get(taskId);
        if (found.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        Task task = found.get();
        TaskStatus status = graph.status(taskId);
        List<Component> info = new ArrayList<>(List.of(
                Component.text("Estado: " + TaskTexts.status(status), color(status)),
                Component.text("Proyecto: " + projectTitle(graph, task.project()), NamedTextColor.GRAY),
                Component.text("Tamaño: " + TaskTexts.size(task.size()), NamedTextColor.GRAY),
                Component.text("Creada por: " + task.creatorOptional().map(PlayerRef::name).orElse("desconocido"), NamedTextColor.GRAY),
                Component.text("Asignada a: " + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA),
                Component.text("Depende de: " + TaskTexts.taskList(graph, task.dependencies()), NamedTextColor.GRAY),
                Component.text("La necesitan: " + TaskTexts.taskList(graph, graph.dependents(taskId)), NamedTextColor.GRAY)));
        TaskTexts.resolution(task, ZoneId.systemDefault())
                .ifPresent(line -> info.add(1, Component.text(line, NamedTextColor.GREEN)));
        show(player, Component.text("Detalles de " + task.title()), List.of(text(Component.join(JoinConfiguration.newlines(), info))),
                List.of(), DialogType.notice(actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)))));
    }

    // ---- Create and edit ----

    /**
     * @param back where «Cancelar» leads
     * @param icon the icon picked in the chest menu so far, or empty to use the item in hand
     */
    private void openCreate(Player player, String projectId, Consumer<Player> back, ActionResult feedback,
                            String title, String description, String icon) {
        Optional<Project> project = service.project(projectId);
        if (project.isEmpty()) {
            openProjects(player, failure(TaskActions.PROJECT_GONE));
            return;
        }
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        chosenIcon(icon).ifPresent(body::add);
        body.add(text(Component.text(icon.isBlank()
                ? "«Elegir ícono» abre un menú con todos los ítems. Si no elegís ninguno, se usa el ítem que tengas en la mano al tocar «Crear»."
                : "«Elegir ícono» lo cambia por otro.", NamedTextColor.GRAY)));

        ActionButton create = actionButton("Crear", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String newTitle = textOf(response, TITLE_INPUT);
            String newDescription = textOf(response, DESCRIPTION_INPUT);
            Optional<String> chosenIcon = icon.isBlank() ? Optional.of(Senders.iconInHand(viewer)) : TaskIcons.parse(icon);
            if (chosenIcon.isEmpty()) {
                openCreate(viewer, projectId, back, failure(TaskActions.unknownIcon(icon)), newTitle, newDescription, "");
                return;
            }
            Actor actor = Senders.actor(viewer);
            boolean iconIsFallback = icon.isBlank() && Senders.handIsEmpty(viewer);
            ActionResult created = actions.create(actor, newTitle, chosenIcon.get(), iconIsFallback, projectId);
            if (!created.success()) {
                openCreate(viewer, projectId, back, created, newTitle, newDescription, icon);
                return;
            }
            ActionResult result = created;
            if (!newDescription.isBlank()) {
                ActionResult described = actions.describe(actor, created.task().id(), newDescription);
                result = described.success() ? created : withDetail(created, described.message());
            }
            openDetail(viewer, created.task().id(), result);
        }));
        // Submits the form so what was typed comes back when the picker returns.
        ActionButton pick = actionButton("Elegir ícono", NamedTextColor.AQUA, onSubmit((viewer, response) -> {
            String typedTitle = textOf(response, TITLE_INPUT);
            String typedDescription = textOf(response, DESCRIPTION_INPUT);
            iconPicker.open(viewer,
                    key -> openCreate(viewer, projectId, back, null, typedTitle, typedDescription, IconKeys.display(key)),
                    () -> openCreate(viewer, projectId, back, null, typedTitle, typedDescription, icon));
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(back));

        show(player, Component.text("Nueva tarea en " + project.get().title()), body, formInputs(title, description),
                formWithPicker(create, pick, cancel));
    }

    private void openEdit(Player player, String taskId, ActionResult feedback, String title, String description) {
        ActionButton save = actionButton("Guardar", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            Optional<Task> current = service.task(taskId);
            if (current.isEmpty()) {
                openProjects(viewer, failure(TASK_GONE));
                return;
            }
            String newTitle = textOf(response, TITLE_INPUT);
            String newDescription = textOf(response, DESCRIPTION_INPUT);
            Actor actor = Senders.actor(viewer);
            List<String> messages = new ArrayList<>();
            if (!Titles.clean(newTitle).equals(current.get().title())) {
                ActionResult renamed = actions.rename(actor, taskId, newTitle);
                if (!renamed.success()) {
                    openEdit(viewer, taskId, renamed, newTitle, newDescription);
                    return;
                }
                messages.add(renamed.message());
            }
            if (!newDescription.strip().equals(current.get().description())) {
                ActionResult described = actions.describe(actor, taskId, newDescription);
                if (!described.success()) {
                    openEdit(viewer, taskId, described, newTitle, newDescription);
                    return;
                }
                messages.add(described.message());
            }
            ActionResult result = messages.isEmpty()
                    ? new ActionResult(true, null, "No había cambios.", List.of())
                    : new ActionResult(true, null, messages.getFirst(), messages.subList(1, messages.size()));
            openDetail(viewer, taskId, result);
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openEditMenu(viewer, taskId)));

        show(player, Component.text("Editar tarea"), feedback(feedback), formInputs(title, description),
                DialogType.confirmation(save, cancel));
    }

    private static List<DialogInput> formInputs(String title, String description) {
        TextDialogInput descriptionInput = DialogInput.text(DESCRIPTION_INPUT, Component.text("Descripción (opcional)"))
                .initial(description)
                .maxLength(DESCRIPTION_MAX_LENGTH)
                .multiline(TextDialogInput.MultilineOptions.create(5, 80))
                .width(300)
                .build();
        return List.of(titleInput(title), descriptionInput);
    }

    private static TextDialogInput titleInput(String title) {
        return DialogInput.text(TITLE_INPUT, Component.text("Título"))
                .initial(title)
                .maxLength(TITLE_MAX_LENGTH)
                .width(300)
                .build();
    }

    // ---- Icon ----

    /** Straight to the chest menu: picking applies the icon; closing it goes back to «Editar». */
    private void openIcon(Player player, String taskId) {
        if (service.task(taskId).isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        iconPicker.open(player,
                key -> openDetail(player, taskId, actions.changeIcon(Senders.actor(player), taskId, key)),
                () -> openEditMenu(player, taskId));
    }

    // ---- Size ----

    private void openSize(Player player, String taskId) {
        Optional<Task> task = service.task(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (TaskSize size : TaskSize.values()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(size.name(), Component.text(TaskTexts.size(size)),
                    size == task.get().size()));
        }
        List<DialogBody> body = List.of(text(Component.text(
                "En la pestaña de logros, cada tamaño tiene su marco: chica, el común; mediana, redondeado; grande, con puntas.",
                NamedTextColor.GRAY)));

        ActionButton save = actionButton("Guardar", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String chosen = textOf(response, SIZE_INPUT);
            Optional<TaskSize> size = Arrays.stream(TaskSize.values()).filter(value -> value.name().equals(chosen)).findFirst();
            openDetail(viewer, taskId, size.isEmpty()
                    ? failure(TaskActions.unknownSize(chosen))
                    : actions.changeSize(Senders.actor(viewer), taskId, size.get()));
        }));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openEditMenu(viewer, taskId)));

        show(player, Component.text("Tamaño de " + task.get().title()), body,
                List.of(DialogInput.singleOption(SIZE_INPUT, Component.text("Tamaño"), entries).width(300).build()),
                DialogType.confirmation(save, back));
    }

    // ---- Dependencies ----

    private void openAddDependency(Player player, String taskId, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        Optional<Task> task = graph.get(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        List<Task> candidates = graph.dependencyCandidates(taskId);
        if (candidates.isEmpty()) {
            openDetail(player, taskId, failure("No hay tareas que se puedan agregar como dependencia."));
            return;
        }
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("Solo aparecen las tareas que no crearían un ciclo. Las de otro proyecto no tienen línea en la pestaña.",
                NamedTextColor.GRAY)));

        ActionButton add = actionButton("Agregar", NamedTextColor.GREEN, onSubmit((viewer, response) ->
                openDependencies(viewer, taskId, actions.addDependency(Senders.actor(viewer), taskId, textOf(response, TASK_INPUT)))));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDependencies(viewer, taskId, null)));

        show(player, Component.text(task.get().title() + " depende de..."), body,
                List.of(taskPicker(graph, task.get(), Component.text("Tarea"), candidates)), DialogType.confirmation(add, back));
    }

    private void openRemoveDependency(Player player, String taskId) {
        TaskGraph graph = service.snapshot();
        Optional<Task> task = graph.get(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        List<Task> dependencies = task.get().dependencies().stream()
                .map(graph::get)
                .flatMap(Optional::stream)
                .toList();
        if (dependencies.isEmpty()) {
            openDetail(player, taskId, failure(quoted(task.get().title()) + " no tiene dependencias."));
            return;
        }

        ActionButton remove = actionButton("Quitar", NamedTextColor.RED, onSubmit((viewer, response) ->
                openDependencies(viewer, taskId, actions.removeDependency(Senders.actor(viewer), taskId, textOf(response, TASK_INPUT)))));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDependencies(viewer, taskId, null)));

        show(player, Component.text("Quitar dependencia de " + task.get().title()), List.of(),
                List.of(taskPicker(graph, task.get(), Component.text("Dependencia"), dependencies)), DialogType.confirmation(remove, back));
    }

    /** Dropdown of tasks by internal id; those outside {@code task}'s project name theirs. */
    private static SingleOptionDialogInput taskPicker(TaskGraph graph, Task task, Component label, List<Task> tasks) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (Task option : tasks) {
            String name = option.project().equals(task.project())
                    ? option.title()
                    : option.title() + " (en " + projectTitle(graph, option.project()) + ")";
            entries.add(SingleOptionDialogInput.OptionEntry.create(option.id(), Component.text(name), entries.isEmpty()));
        }
        return DialogInput.singleOption(TASK_INPUT, label, entries).width(300).build();
    }

    // ---- Move ----

    private void openMove(Player player, String taskId) {
        TaskGraph graph = service.snapshot();
        Optional<Task> task = graph.get(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        List<Project> targets = graph.projects().stream().filter(project -> !project.id().equals(task.get().project())).toList();
        if (targets.isEmpty()) {
            openDetail(player, taskId, failure("No hay otro proyecto. Creá uno desde la lista de proyectos."));
            return;
        }
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (Project project : targets) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(project.id(), Component.text(project.title()), entries.isEmpty()));
        }
        List<DialogBody> body = List.of(text(Component.text("Ahora está en " + quoted(projectTitle(graph, task.get().project()))
                + ". Conserva sus dependencias y su estado.", NamedTextColor.GRAY)));

        ActionButton move = actionButton("Mover", NamedTextColor.GREEN, onSubmit((viewer, response) ->
                openDetail(viewer, taskId, actions.moveTask(Senders.actor(viewer), taskId, textOf(response, PROJECT_INPUT)))));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openEditMenu(viewer, taskId)));

        show(player, Component.text("Mover " + task.get().title()), body,
                List.of(DialogInput.singleOption(PROJECT_INPUT, Component.text("Proyecto"), entries).width(300).build()),
                DialogType.confirmation(move, back));
    }

    // ---- Remove ----

    private void openConfirmRemove(Player player, String taskId) {
        Optional<Task> task = service.task(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        String projectId = task.get().project();
        ActionButton yes = actionButton("Sí, borrar", NamedTextColor.RED, onClick(viewer -> {
            ActionResult result = actions.remove(Senders.actor(viewer), taskId);
            if (result.success()) {
                openTasks(viewer, projectId, result);
            } else {
                openDetail(viewer, taskId, result);
            }
        }));
        ActionButton no = actionButton("No", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

        show(player, Component.text("Borrar tarea"),
                List.of(text(Component.text("¿Borrar " + quoted(task.get().title()) + "? No se puede deshacer.", NamedTextColor.YELLOW))),
                List.of(), DialogType.confirmation(yes, no));
    }

    // ---- Building blocks ----

    /** Confirm and «Elegir ícono» side by side; cancel is the exit button, also used by Escape. */
    private static DialogType formWithPicker(ActionButton confirm, ActionButton pick, ActionButton cancel) {
        return DialogType.multiAction(List.of(confirm, pick)).columns(2).exitAction(cancel).build();
    }

    /** Preview of the icon picked so far, when it names a usable item. */
    private static Optional<DialogBody> chosenIcon(String typed) {
        return (typed == null || typed.isBlank() ? Optional.<String>empty() : TaskIcons.parse(typed))
                .map(key -> DialogBody.item(TaskIcons.of(key))
                        .description(DialogBody.plainMessage(Component.text("Ícono elegido: " + IconKeys.display(key), NamedTextColor.GRAY)))
                        .build());
    }

    private static void show(Player player, Component title, List<? extends DialogBody> body,
                             List<? extends DialogInput> inputs, DialogType type) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        // The client waits for the server; every callback opens the next screen or closes.
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(type));
        player.showDialog(dialog);
    }

    private DialogAction onClick(Consumer<Player> action) {
        return onSubmit((player, response) -> action.accept(player));
    }

    /** Runs the action on the main thread, where task changes and the advancement tab must happen. */
    private DialogAction onSubmit(BiConsumer<Player, DialogResponseView> action) {
        return DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            Runnable run = () -> {
                if (player.isOnline()) {
                    action.accept(player, response);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                run.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, run);
            }
        }, ONCE);
    }

    private static ActionButton actionButton(String label, NamedTextColor color, DialogAction action) {
        return button(Component.text(label, color), null, ACTION_BUTTON_WIDTH, action);
    }

    private static ActionButton button(Component label, Component tooltip, int width, DialogAction action) {
        return ActionButton.builder(label).tooltip(tooltip).width(width).action(action).build();
    }

    private static DialogBody text(Component contents) {
        return DialogBody.plainMessage(contents, 300);
    }

    /** Result of the previous action, shown on top of the next screen. */
    private static List<DialogBody> feedback(ActionResult result) {
        if (result == null || result.message() == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (String detail : result.details()) {
            lines.add(Component.text(detail, NamedTextColor.GRAY));
        }
        return List.of(text(Component.join(JoinConfiguration.newlines(), lines)));
    }

    private static ActionResult failure(String message) {
        return new ActionResult(false, null, message, List.of());
    }

    private static ActionResult withDetail(ActionResult result, String detail) {
        List<String> details = new ArrayList<>(result.details());
        details.add(detail);
        return new ActionResult(result.success(), result.task(), result.message(), details);
    }

    private static String textOf(DialogResponseView response, String key) {
        return Objects.requireNonNullElse(response.getText(key), "");
    }

    private static String projectTitle(TaskGraph graph, String projectId) {
        return graph.project(projectId).map(Project::title).orElse(projectId);
    }

    private static TextColor color(TaskStatus status) {
        return switch (status) {
            case AVAILABLE -> NamedTextColor.GREEN;
            case BLOCKED -> NamedTextColor.GRAY;
            case RESOLVED -> NamedTextColor.DARK_GREEN;
        };
    }

    private static String quoted(String title) {
        return "\"" + title + "\"";
    }
}
