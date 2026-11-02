package dev.taskgraph.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.taskgraph.action.ActionResult;
import dev.taskgraph.action.TaskActions;
import dev.taskgraph.action.TaskTexts;
import dev.taskgraph.advancement.TaskIcons;
import dev.taskgraph.graph.IconKeys;
import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskPermissions.Actor;
import dev.taskgraph.graph.Titles;
import dev.taskgraph.service.TaskService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@code /tarea} and its subcommands. Players refer to tasks and projects by title; internal ids never show up here.
 * The last argument of every subcommand is greedy, so it needs no quotes. Earlier arguments use
 * Brigadier quoted strings, which suggestions quote automatically when the title has spaces or accents.
 * This class only parses arguments and styles results: rules, permissions and messages live in {@link TaskActions}.
 */
public final class TaskCommand {

    public static final String NAME = "tarea";
    public static final String DESCRIPTION = "Tareas comunales del servidor";

    private static final String TASK = "tarea";
    private static final String DEPENDENCY = "dependencia";
    private static final String TITLE = "titulo";
    private static final String NEW_TITLE = "nuevoTitulo";
    private static final String TEXT = "texto";
    private static final String ITEM = "item";
    private static final String PROJECT = "proyecto";

    private final TaskService service;
    private final TaskActions actions;

    /** @param service read-only queries (suggestions, info); changes go through {@code actions} */
    public TaskCommand(TaskService service, TaskActions actions) {
        this.service = service;
        this.actions = actions;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(NAME)
                .executes(this::help)
                .then(Commands.literal("crear")
                        .then(Commands.argument(TITLE, StringArgumentType.greedyString())
                                .executes(this::create)))
                .then(Commands.literal("renombrar")
                        .then(quotedTask(TASK)
                                .then(Commands.argument(NEW_TITLE, StringArgumentType.greedyString())
                                        .executes(this::rename))))
                .then(Commands.literal("desc")
                        .then(quotedTask(TASK)
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(this::describe))))
                .then(Commands.literal("icono")
                        .then(quotedTask(TASK)
                                .then(Commands.argument(ITEM, ArgumentTypes.itemStack())
                                        .executes(this::changeIcon))))
                .then(Commands.literal("depende")
                        .then(quotedTask(TASK)
                                .then(lastTask(DEPENDENCY).executes(this::addDependency))))
                .then(Commands.literal("quitardep")
                        .then(quotedTask(TASK)
                                .then(Commands.argument(DEPENDENCY, StringArgumentType.greedyString())
                                        .suggests(dependenciesOfTask())
                                        .executes(this::removeDependency))))
                .then(Commands.literal("asignar")
                        .then(lastTask(TASK).executes(this::assign)))
                .then(Commands.literal("soltar")
                        .then(lastTask(TASK).executes(this::release)))
                .then(Commands.literal("resolver")
                        .then(lastTask(TASK).executes(this::resolve)))
                .then(Commands.literal("info")
                        .then(lastTask(TASK).executes(this::info)))
                .then(Commands.literal("borrar")
                        .then(lastTask(TASK).executes(this::remove)))
                .then(Commands.literal("mover")
                        .then(quotedTask(TASK)
                                .then(lastProject(PROJECT).executes(this::move))))
                .then(Commands.literal("proyecto")
                        .executes(this::listProjects)
                        .then(Commands.literal("lista").executes(this::listProjects))
                        .then(Commands.literal("crear")
                                .then(Commands.argument(TITLE, StringArgumentType.greedyString())
                                        .executes(this::createProject)))
                        .then(Commands.literal("renombrar")
                                .then(Commands.argument(PROJECT, StringArgumentType.string())
                                        .suggests(projectTitles(StringArgumentType::escapeIfRequired))
                                        .then(Commands.argument(NEW_TITLE, StringArgumentType.greedyString())
                                                .executes(this::renameProject))))
                        .then(Commands.literal("ocultar")
                                .then(lastProject(PROJECT).executes(context -> setHideResolved(context, true))))
                        .then(Commands.literal("mostrar")
                                .then(lastProject(PROJECT).executes(context -> setHideResolved(context, false))))
                        .then(Commands.literal("borrar")
                                .then(lastProject(PROJECT).executes(this::removeProject))))
                .build();
    }

    /** Task title that is followed by more arguments, so titles with spaces or accents need quotes. */
    private RequiredArgumentBuilder<CommandSourceStack, String> quotedTask(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests(taskTitles(StringArgumentType::escapeIfRequired));
    }

    /** Task title as the last argument: takes the rest of the line, no quotes needed. */
    private RequiredArgumentBuilder<CommandSourceStack, String> lastTask(String name) {
        return Commands.argument(name, StringArgumentType.greedyString()).suggests(taskTitles(Function.identity()));
    }

    /** Project title as the last argument: takes the rest of the line, no quotes needed. */
    private RequiredArgumentBuilder<CommandSourceStack, String> lastProject(String name) {
        return Commands.argument(name, StringArgumentType.greedyString()).suggests(projectTitles(Function.identity()));
    }

    private SuggestionProvider<CommandSourceStack> projectTitles(Function<String, String> format) {
        return (context, builder) -> {
            String typed = Titles.key(unquote(builder.getRemaining()));
            for (Project project : service.projects()) {
                if (Titles.key(project.title()).startsWith(typed)) {
                    builder.suggest(format.apply(project.title()));
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> taskTitles(Function<String, String> format) {
        return (context, builder) -> {
            String typed = Titles.key(unquote(builder.getRemaining()));
            for (Task task : service.tasks()) {
                if (Titles.key(task.title()).startsWith(typed)) {
                    builder.suggest(format.apply(task.title()));
                }
            }
            return builder.buildFuture();
        };
    }

    /** Suggests only the dependencies of the task typed in the previous argument. */
    private SuggestionProvider<CommandSourceStack> dependenciesOfTask() {
        return (context, builder) -> {
            Optional<Task> task;
            try {
                task = actions.findByTitle(unquote(context.getArgument(TASK, String.class)));
            } catch (IllegalArgumentException exception) {
                task = Optional.empty();
            }
            String typed = Titles.key(unquote(builder.getRemaining()));
            task.ifPresent(found -> found.dependencies().stream()
                    .flatMap(id -> service.task(id).stream())
                    .map(Task::title)
                    .filter(title -> Titles.key(title).startsWith(typed))
                    .forEach(builder::suggest));
            return builder.buildFuture();
        };
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Comandos de tareas:", NamedTextColor.GOLD));
        for (String usage : List.of(
                "crear <título> — el ícono es el ítem en tu mano; se crea en General",
                "renombrar \"<título>\" <nuevo título>",
                "desc \"<título>\" <texto>",
                "icono \"<título>\" <ítem> — Tab sugiere los ítems",
                "depende \"<título>\" <dependencia>",
                "quitardep \"<título>\" <dependencia>",
                "asignar <título>",
                "soltar <título> — deja de estar asignada a vos",
                "resolver <título>",
                "info <título>",
                "borrar <título>",
                "mover \"<título>\" <proyecto>",
                "proyecto lista",
                "proyecto crear <título>",
                "proyecto renombrar \"<proyecto>\" <nuevo título>",
                "proyecto ocultar <proyecto> — oculta las ramas terminadas de su pestaña",
                "proyecto mostrar <proyecto> — vuelve a mostrar todas las resueltas",
                "proyecto borrar <proyecto> — solo admins, y solo si está vacío")) {
            sender.sendMessage(Component.text("/" + NAME + " " + usage, NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Usá comillas cuando el título tenga espacios o tildes y no sea lo último del comando. Tab las pone por vos.",
                NamedTextColor.DARK_GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return fail(sender, "Solo un jugador puede crear tareas: el ícono es el ítem que tiene en la mano.");
        }
        String title = unquote(StringArgumentType.getString(context, TITLE));
        return report(sender, actions.create(Senders.actor(sender), title, Senders.iconInHand(player), Senders.handIsEmpty(player)));
    }

    private int rename(CommandContext<CommandSourceStack> context) {
        String newTitle = unquote(StringArgumentType.getString(context, NEW_TITLE));
        return withTask(context, TASK, (actor, task) -> actions.rename(actor, task.id(), newTitle));
    }

    private int describe(CommandContext<CommandSourceStack> context) {
        String text = StringArgumentType.getString(context, TEXT);
        return withTask(context, TASK, (actor, task) -> actions.describe(actor, task.id(), text));
    }

    private int changeIcon(CommandContext<CommandSourceStack> context) {
        // Only the item type is kept; components typed after it (e.g. diamond[...]) are ignored.
        ItemStack item = context.getArgument(ITEM, ItemStack.class);
        String key = item.getType().getKey().asString();
        if (!TaskIcons.usable(item.getType())) {
            return fail(context.getSource().getSender(), TaskActions.unknownIcon(IconKeys.display(key)));
        }
        return withTask(context, TASK, (actor, task) -> actions.changeIcon(actor, task.id(), key));
    }

    private int addDependency(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> {
            Optional<Task> dependency = lookup(context, DEPENDENCY);
            return dependency.isEmpty()
                    ? unknown(context, DEPENDENCY)
                    : actions.addDependency(actor, task.id(), dependency.get().id());
        });
    }

    private int removeDependency(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> {
            Optional<Task> dependency = lookup(context, DEPENDENCY);
            return dependency.isEmpty()
                    ? unknown(context, DEPENDENCY)
                    : actions.removeDependency(actor, task.id(), dependency.get().id());
        });
    }

    private int assign(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            return fail(sender, "Solo un jugador puede asignarse una tarea.");
        }
        return withTask(context, TASK, (actor, task) -> actions.assignToSelf(actor, task.id()));
    }

    private int release(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> actions.release(actor, task.id()));
    }

    private int resolve(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> actions.resolve(actor, task.id()));
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Task> found = lookup(context, TASK);
        if (found.isEmpty()) {
            return report(sender, unknown(context, TASK));
        }
        Task task = found.get();
        TaskGraph graph = service.snapshot();
        sender.sendMessage(Component.text(task.title(), NamedTextColor.GOLD));
        if (!task.description().isEmpty()) {
            sender.sendMessage(Component.text(task.description(), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Proyecto: "
                + graph.project(task.project()).map(Project::title).orElse(task.project()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Estado: " + (task.resolved() ? "resuelta" : "pendiente"),
                task.resolved() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Creada por: "
                + task.creatorOptional().map(PlayerRef::name).orElse("desconocido"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Asignada a: "
                + task.assigneeOptional().map(PlayerRef::name).orElse("nadie"), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Depende de: " + TaskTexts.taskList(graph, task.dependencies()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("La necesitan: " + TaskTexts.taskList(graph, graph.dependents(task.id())), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Ícono: " + IconKeys.display(task.icon()), NamedTextColor.DARK_GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> actions.remove(actor, task.id()));
    }

    private int move(CommandContext<CommandSourceStack> context) {
        return withTask(context, TASK, (actor, task) -> {
            Optional<Project> project = lookupProject(context);
            return project.isEmpty()
                    ? unknownProject(context)
                    : actions.moveTask(actor, task.id(), project.get().id());
        });
    }

    private int listProjects(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        TaskGraph graph = service.snapshot();
        sender.sendMessage(Component.text("Proyectos:", NamedTextColor.GOLD));
        for (Project project : graph.projects()) {
            List<Task> tasks = graph.tasksIn(project.id());
            long resolved = tasks.stream().filter(Task::resolved).count();
            String hiding = project.hideResolved() ? " (oculta ramas terminadas)" : "";
            sender.sendMessage(Component.text(project.title() + " — " + resolved + " de " + tasks.size() + " resueltas" + hiding,
                    NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int createProject(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String title = unquote(StringArgumentType.getString(context, TITLE));
        return report(sender, actions.createProject(Senders.actor(sender), title));
    }

    private int renameProject(CommandContext<CommandSourceStack> context) {
        String newTitle = unquote(StringArgumentType.getString(context, NEW_TITLE));
        return withProject(context, (actor, project) -> actions.renameProject(actor, project.id(), newTitle));
    }

    private int setHideResolved(CommandContext<CommandSourceStack> context, boolean hide) {
        return withProject(context, (actor, project) -> actions.setHideResolved(actor, project.id(), hide));
    }

    private int removeProject(CommandContext<CommandSourceStack> context) {
        return withProject(context, (actor, project) -> actions.removeProject(actor, project.id()));
    }

    private int withProject(CommandContext<CommandSourceStack> context, BiFunction<Actor, Project, ActionResult> action) {
        CommandSender sender = context.getSource().getSender();
        Optional<Project> project = lookupProject(context);
        if (project.isEmpty()) {
            return report(sender, unknownProject(context));
        }
        return report(sender, action.apply(Senders.actor(sender), project.get()));
    }

    private Optional<Project> lookupProject(CommandContext<CommandSourceStack> context) {
        return actions.findProjectByTitle(unquote(StringArgumentType.getString(context, PROJECT)));
    }

    private static ActionResult unknownProject(CommandContext<CommandSourceStack> context) {
        String title = unquote(StringArgumentType.getString(context, PROJECT));
        return new ActionResult(false, null, TaskActions.unknownProject(title), List.of());
    }

    /** Looks up the task named by the argument, runs the action and reports its result to the sender. */
    private int withTask(CommandContext<CommandSourceStack> context, String argument,
                         BiFunction<Actor, Task, ActionResult> action) {
        CommandSender sender = context.getSource().getSender();
        Optional<Task> task = lookup(context, argument);
        if (task.isEmpty()) {
            return report(sender, unknown(context, argument));
        }
        return report(sender, action.apply(Senders.actor(sender), task.get()));
    }

    private Optional<Task> lookup(CommandContext<CommandSourceStack> context, String argument) {
        return actions.findByTitle(unquote(StringArgumentType.getString(context, argument)));
    }

    private static ActionResult unknown(CommandContext<CommandSourceStack> context, String argument) {
        String title = unquote(StringArgumentType.getString(context, argument));
        return new ActionResult(false, null, TaskActions.unknownTitle(title), List.of());
    }

    /** Errors in red, the main message in green and details in gray, as the commands always showed them. */
    private static int report(CommandSender sender, ActionResult result) {
        if (!result.success()) {
            return fail(sender, result.message());
        }
        result.messageOptional().ifPresent(message -> succeed(sender, message));
        for (String detail : result.details()) {
            sender.sendMessage(Component.text(detail, NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Greedy arguments keep quotes literally; drop them when the player typed a quoted title anyway. */
    private static String unquote(String value) {
        String stripped = value.strip();
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped.startsWith("\"") ? stripped.substring(1) : stripped;
    }

    private static String quoted(String title) {
        return "\"" + title + "\"";
    }

    private static void succeed(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static int fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return 0;
    }
}
