package dev.taskgraph.dialog;

import dev.taskgraph.action.ActionResult;
import dev.taskgraph.action.TaskActions;
import dev.taskgraph.action.TaskTexts;
import dev.taskgraph.advancement.TaskIcons;
import dev.taskgraph.command.Senders;
import dev.taskgraph.graph.IconKeys;
import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskPermissions;
import dev.taskgraph.graph.TaskPermissions.Actor;
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
import java.util.ArrayList;
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
    private static final String ICON_INPUT = "icono";
    private static final int TITLE_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 512;
    private static final int ICON_MAX_LENGTH = 64;
    private static final String ICON_HINT = "Escribí el id de un ítem, por ejemplo diamond o minecraft:oak_log.";
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

    // ---- Projects ----

    private void openProjects(Player player, ActionResult feedback) {
        TaskGraph graph = service.snapshot();
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("Elegí un proyecto. Cada uno es una pestaña en la pantalla de logros.", NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
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
        buttons.add(button(Component.text("+ Nuevo proyecto", NamedTextColor.GOLD), null, LIST_BUTTON_WIDTH,
                onClick(viewer -> openCreateProject(viewer, null, ""))));

        show(player, Component.text("Tareas del servidor"), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        // closeDialog() doesn't dismiss the client's "waiting for server" screen that follows the
                        // click (it only closes dialog screens), leaving it up until it times out. closeInventory()
                        // closes whatever screen is open.
                        .exitAction(button(Component.text("Cerrar"), null, ACTION_BUTTON_WIDTH, onClick(Player::closeInventory)))
                        .build());
    }

    private void openCreateProject(Player player, ActionResult feedback, String title) {
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(text(Component.text("El proyecto tiene su propia pestaña en la pantalla de logros.", NamedTextColor.GRAY)));

        ActionButton create = actionButton("Crear", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String newTitle = textOf(response, TITLE_INPUT);
            ActionResult result = actions.createProject(Senders.actor(viewer), newTitle);
            Optional<Project> created = result.success() ? service.findProjectByTitle(newTitle) : Optional.empty();
            if (created.isEmpty()) {
                openCreateProject(viewer, result, newTitle);
                return;
            }
            openTasks(viewer, created.get().id(), result);
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openProjects(viewer, null)));

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
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openTasks(viewer, projectId, null)));

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
                openTasks(viewer, projectId, result);
            }
        }));
        ActionButton no = actionButton("No", NamedTextColor.WHITE, onClick(viewer -> openTasks(viewer, projectId, null)));

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
        int hidden = graph.hiddenTasksIn(projectId).size();
        if (hidden > 0) {
            body.add(text(Component.text((hidden == 1 ? "1 resuelta oculta" : hidden + " resueltas ocultas")
                    + " en la pestaña de logros. Acá se listan igual.", NamedTextColor.DARK_GRAY)));
        }
        if (tasks.isEmpty()) {
            body.add(text(Component.text("Todavía no hay tareas en este proyecto. Creá la primera con «Nueva tarea».", NamedTextColor.GRAY)));
        } else {
            body.add(text(Component.text(TaskTexts.marker(TaskStatus.AVAILABLE) + " disponible   "
                    + TaskTexts.marker(TaskStatus.BLOCKED) + " bloqueada   "
                    + TaskTexts.marker(TaskStatus.RESOLVED) + " resuelta", NamedTextColor.GRAY)));
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (Task task : tasks) {
            TaskStatus status = graph.status(task.id());
            Component tooltip = Component.join(JoinConfiguration.newlines(),
                    Component.text(TaskTexts.status(status), color(status)),
                    Component.text("Asignada a: " + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA));
            buttons.add(button(Component.text(TaskTexts.marker(status) + " " + task.title(), color(status)),
                    tooltip, LIST_BUTTON_WIDTH, onClick(viewer -> openDetail(viewer, task.id(), null))));
        }
        buttons.add(button(Component.text("+ Nueva tarea", NamedTextColor.GOLD), null, LIST_BUTTON_WIDTH,
                onClick(viewer -> openCreate(viewer, projectId, null, "", "", ""))));
        buttons.add(button(Component.text("Renombrar proyecto", NamedTextColor.WHITE), null, LIST_BUTTON_WIDTH,
                onClick(viewer -> openRenameProject(viewer, projectId, null, project.title()))));
        boolean hide = !project.hideResolved();
        buttons.add(button(Component.text(hide ? "Ocultar resueltas" : "Mostrar resueltas", NamedTextColor.WHITE),
                Component.text(hide
                        ? "Saca de la pestaña las tareas resueltas de las que ya no depende nada pendiente."
                        : "Vuelve a mostrar en la pestaña todas las tareas resueltas.", NamedTextColor.GRAY),
                LIST_BUTTON_WIDTH,
                onClick(viewer -> openTasks(viewer, projectId, actions.setHideResolved(Senders.actor(viewer), projectId, hide)))));
        if (!project.isGeneral() && TaskPermissions.canRemoveProject(Senders.actor(player))) {
            buttons.add(button(Component.text("Borrar proyecto", NamedTextColor.RED), null, LIST_BUTTON_WIDTH,
                    onClick(viewer -> openConfirmRemoveProject(viewer, projectId))));
        }

        show(player, Component.text(project.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH, onClick(viewer -> openProjects(viewer, null))))
                        .build());
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
        body.add(text(Component.join(JoinConfiguration.newlines(),
                Component.text("Estado: " + TaskTexts.status(status), color(status)),
                Component.text("Proyecto: " + projectTitle(graph, task.project()), NamedTextColor.GRAY),
                Component.text("Creada por: " + task.creatorOptional().map(PlayerRef::name).orElse("desconocido"), NamedTextColor.GRAY),
                Component.text("Asignada a: " + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA),
                Component.text("Depende de: " + TaskTexts.taskList(graph, task.dependencies()), NamedTextColor.GRAY),
                Component.text("La necesitan: " + TaskTexts.taskList(graph, graph.dependents(taskId)), NamedTextColor.GRAY))));

        List<ActionButton> buttons = new ArrayList<>();
        boolean assignedToActor = actor.uuid() != null && task.assigneeOptional().filter(ref -> ref.is(actor.uuid())).isPresent();
        if (actor.uuid() != null && !assignedToActor && TaskPermissions.canAssignToSelf(task, actor)) {
            String label = task.assigneeOptional().isPresent() ? "Reasignarme" : "Asignarme";
            buttons.add(actionButton(label, NamedTextColor.AQUA,
                    onClick(viewer -> openDetail(viewer, taskId, actions.assignToSelf(Senders.actor(viewer), taskId)))));
        }
        if (task.assigneeOptional().isPresent() && TaskPermissions.canRelease(task, actor)) {
            buttons.add(actionButton("Soltar", NamedTextColor.AQUA,
                    onClick(viewer -> openDetail(viewer, taskId, actions.release(Senders.actor(viewer), taskId)))));
        }
        if (status == TaskStatus.AVAILABLE) {
            buttons.add(actionButton("Resolver", NamedTextColor.GREEN, onClick(viewer -> {
                ActionResult result = actions.resolve(Senders.actor(viewer), taskId);
                // Resolving announces to everyone instead of replying, so the dialog says it explicitly.
                openDetail(viewer, taskId, result.success()
                        ? new ActionResult(true, result.task(), "Resolviste " + quoted(task.title()) + ".", List.of())
                        : result);
            })));
        }
        if (!graph.dependencyCandidates(taskId).isEmpty()) {
            buttons.add(actionButton("Agregar dependencia", NamedTextColor.WHITE,
                    onClick(viewer -> openAddDependency(viewer, taskId, null))));
        }
        if (!task.dependencies().isEmpty()) {
            buttons.add(actionButton("Quitar dependencia", NamedTextColor.WHITE,
                    onClick(viewer -> openRemoveDependency(viewer, taskId))));
        }
        buttons.add(actionButton("Editar", NamedTextColor.WHITE,
                onClick(viewer -> openEdit(viewer, taskId, null, task.title(), task.description()))));
        buttons.add(actionButton("Cambiar ícono", NamedTextColor.WHITE,
                onClick(viewer -> openIcon(viewer, taskId, null, IconKeys.display(task.icon())))));
        if (graph.projects().size() > 1) {
            buttons.add(actionButton("Mover a otro proyecto", NamedTextColor.WHITE, onClick(viewer -> openMove(viewer, taskId))));
        }
        if (TaskPermissions.canRemove(task, actor)) {
            buttons.add(actionButton("Borrar", NamedTextColor.RED, onClick(viewer -> openConfirmRemove(viewer, taskId))));
        }

        show(player, Component.text(task.title()), body, List.of(),
                DialogType.multiAction(buttons)
                        .columns(2)
                        .exitAction(button(Component.text("Volver"), null, ACTION_BUTTON_WIDTH,
                                onClick(viewer -> openTasks(viewer, task.project(), null))))
                        .build());
    }

    // ---- Create and edit ----

    private void openCreate(Player player, String projectId, ActionResult feedback, String title, String description, String icon) {
        Optional<Project> project = service.project(projectId);
        if (project.isEmpty()) {
            openProjects(player, failure(TaskActions.PROJECT_GONE));
            return;
        }
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        chosenIcon(icon).ifPresent(body::add);
        body.add(text(Component.text("«Elegir ícono» abre un menú con todos los ítems. Si dejás el ícono vacío, se usa el ítem que tengas en la mano al tocar «Crear».",
                NamedTextColor.GRAY)));

        ActionButton create = actionButton("Crear", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String newTitle = textOf(response, TITLE_INPUT);
            String newDescription = textOf(response, DESCRIPTION_INPUT);
            String typedIcon = textOf(response, ICON_INPUT);
            Optional<String> chosenIcon = typedIcon.isBlank() ? Optional.of(Senders.iconInHand(viewer)) : TaskIcons.parse(typedIcon);
            if (chosenIcon.isEmpty()) {
                openCreate(viewer, projectId, failure(TaskActions.unknownIcon(typedIcon)), newTitle, newDescription, typedIcon);
                return;
            }
            Actor actor = Senders.actor(viewer);
            boolean iconIsFallback = typedIcon.isBlank() && Senders.handIsEmpty(viewer);
            ActionResult created = actions.create(actor, newTitle, chosenIcon.get(), iconIsFallback, projectId);
            if (!created.success()) {
                openCreate(viewer, projectId, created, newTitle, newDescription, typedIcon);
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
            String typedIcon = textOf(response, ICON_INPUT);
            iconPicker.open(viewer,
                    key -> openCreate(viewer, projectId, null, typedTitle, typedDescription, IconKeys.display(key)),
                    () -> openCreate(viewer, projectId, null, typedTitle, typedDescription, typedIcon));
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openTasks(viewer, projectId, null)));

        List<DialogInput> inputs = new ArrayList<>(formInputs(title, description));
        inputs.add(iconInput(Component.text("Ícono (opcional)"), icon));
        show(player, Component.text("Nueva tarea en " + project.get().title()), body, inputs, formWithPicker(create, pick, cancel));
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
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

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

    private static TextDialogInput iconInput(Component label, String icon) {
        return DialogInput.text(ICON_INPUT, label)
                .initial(icon)
                .maxLength(ICON_MAX_LENGTH)
                .width(300)
                .build();
    }

    // ---- Icon ----

    private void openIcon(Player player, String taskId, ActionResult feedback, String typed) {
        Optional<Task> task = service.task(taskId);
        if (task.isEmpty()) {
            openProjects(player, failure(TASK_GONE));
            return;
        }
        List<DialogBody> body = new ArrayList<>(feedback(feedback));
        body.add(DialogBody.item(TaskIcons.of(task.get().icon()))
                .description(DialogBody.plainMessage(Component.text("Ícono actual: " + IconKeys.display(task.get().icon()), NamedTextColor.GRAY)))
                .build());
        if (!typed.equals(IconKeys.display(task.get().icon()))) {
            chosenIcon(typed).ifPresent(body::add);
        }
        body.add(text(Component.text("«Elegir ícono» abre un menú con todos los ítems. " + ICON_HINT
                + " Si lo dejás vacío, se usa el ítem que tengas en la mano.", NamedTextColor.GRAY)));

        ActionButton save = actionButton("Guardar", NamedTextColor.GREEN, onSubmit((viewer, response) -> {
            String typedIcon = textOf(response, ICON_INPUT);
            if (typedIcon.isBlank() && Senders.handIsEmpty(viewer)) {
                openIcon(viewer, taskId, failure("No tenés nada en la mano. " + ICON_HINT), "");
                return;
            }
            Optional<String> chosenIcon = typedIcon.isBlank() ? Optional.of(Senders.iconInHand(viewer)) : TaskIcons.parse(typedIcon);
            if (chosenIcon.isEmpty()) {
                openIcon(viewer, taskId, failure(TaskActions.unknownIcon(typedIcon)), typedIcon);
                return;
            }
            openDetail(viewer, taskId, actions.changeIcon(Senders.actor(viewer), taskId, chosenIcon.get()));
        }));
        ActionButton pick = actionButton("Elegir ícono", NamedTextColor.AQUA, onSubmit((viewer, response) -> {
            String typedIcon = textOf(response, ICON_INPUT);
            iconPicker.open(viewer,
                    key -> openIcon(viewer, taskId, null, IconKeys.display(key)),
                    () -> openIcon(viewer, taskId, null, typedIcon));
        }));
        ActionButton cancel = actionButton("Cancelar", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

        show(player, Component.text("Ícono de " + task.get().title()), body,
                List.of(iconInput(Component.text("Ítem"), typed)), formWithPicker(save, pick, cancel));
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
                openDetail(viewer, taskId, actions.addDependency(Senders.actor(viewer), taskId, textOf(response, TASK_INPUT)))));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

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
                openDetail(viewer, taskId, actions.removeDependency(Senders.actor(viewer), taskId, textOf(response, TASK_INPUT)))));
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

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
        ActionButton back = actionButton("Volver", NamedTextColor.WHITE, onClick(viewer -> openDetail(viewer, taskId, null)));

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

    /** Preview of the icon typed or picked so far, when it names a usable item. */
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
