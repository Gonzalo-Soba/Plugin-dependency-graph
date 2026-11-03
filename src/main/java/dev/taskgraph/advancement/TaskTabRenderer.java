package dev.taskgraph.advancement;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplayBuilder;
import com.fren_gor.ultimateAdvancementAPI.advancement.multiParents.MultiParentsAdvancement;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;
import dev.taskgraph.action.TaskTexts;
import dev.taskgraph.graph.GraphLayout;
import dev.taskgraph.graph.NodePosition;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds one advancement tab per project. UAAPI tabs are immutable once registered, so every render
 * unregisters the previous tabs and registers new ones under the same namespaces.
 * <p>
 * Every render rebuilds all the tabs, not only the one that changed: the client puts each tab it
 * receives at the end of its list, so sending them again in project order keeps the order stable.
 */
public final class TaskTabRenderer {

    private static final String ROOT_KEY = "root";
    // Prefix keeps task keys apart from the root key, even for a task with id "root".
    private static final String TASK_KEY_PREFIX = "task/";
    private static final String BACKGROUND_TEXTURE = "textures/block/stone_bricks.png";
    private static final Material ROOT_ICON = Material.WRITABLE_BOOK;

    // UAAPI takes bungee-chat components; legacy strings let the rest of the plugin stay on Adventure.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final UltimateAdvancementAPI api;
    private final boolean newTabs;

    /** @param newTabs whether to add the "+ Tarea" and "+ Proyecto" tabs, which need PacketEvents */
    public TaskTabRenderer(UltimateAdvancementAPI api, boolean newTabs) {
        this.api = api;
        this.newTabs = newTabs;
    }

    /**
     * Id the client gives the tab with this namespace. UAAPI puts an internal root above ours, under its
     * reserved key prefix, and that one names the tab: {@code namespace:_-uaapi-internal-_root}.
     */
    public static String tabId(String namespace) {
        return namespace + ":" + AdvancementKey.RESERVED_KEY_PREFIX + ROOT_KEY;
    }

    public static AdvancementKey taskKey(TaskKey key) {
        return new AdvancementKey(TabNamespaces.of(key.project()), TASK_KEY_PREFIX + key.task());
    }

    /** @return the tab of every project, by project id, in tab order */
    public Map<String, AdvancementTab> render(TaskGraph graph) {
        api.unregisterPluginAdvancementTabs();
        Map<String, AdvancementTab> tabs = new LinkedHashMap<>();
        for (Project project : graph.projects()) {
            tabs.put(project.id(), renderProject(graph, project));
        }
        if (newTabs) {
            // Always after the projects, so they stay the last two tabs.
            renderNewTab(TabNamespaces.NEW_TASK, Material.LIME_DYE, "+ Tarea",
                    "Abrí esta pestaña para crear una tarea en el proyecto que estabas mirando.");
            renderNewTab(TabNamespaces.NEW_PROJECT, Material.LIME_BUNDLE, "+ Proyecto",
                    "Abrí esta pestaña para crear un proyecto nuevo.");
        }
        return tabs;
    }

    /** A tab with only its root: opening it is the action, handled by NewTabListener. */
    private void renderNewTab(String namespace, Material icon, String title, String description) {
        AdvancementTab tab = api.createAdvancementTab(namespace, BACKGROUND_TEXTURE);
        AdvancementDisplay display = new AdvancementDisplayBuilder(new ItemStack(icon), legacy(Component.text(title, NamedTextColor.GREEN)))
                .description(legacy(Component.text(description, NamedTextColor.GRAY)))
                .coords(0, 0)
                .build();
        tab.registerAdvancements(new RootAdvancement(tab, ROOT_KEY, display), new HashSet<>());
        tab.automaticallyShowToPlayers();
        tab.automaticallyGrantRootAdvancement();
    }

    private AdvancementTab renderProject(TaskGraph graph, Project project) {
        AdvancementTab tab = api.createAdvancementTab(TabNamespaces.of(project.id()), BACKGROUND_TEXTURE);

        List<Task> tasks = graph.tasksIn(project.id());
        // Hidden tasks aren't registered; the synchronizer keeps their progress straight in the database.
        Set<String> drawnIds = graph.visibleTasksIn(project.id()).stream().map(Task::id).collect(Collectors.toSet());
        Map<String, NodePosition> positions = GraphLayout.compute(graph, drawnIds);
        RootAdvancement root = new RootAdvancement(tab, ROOT_KEY, rootDisplay(project, tasks, tasks.size() - drawnIds.size(), newTabs));
        Map<String, BaseAdvancement> advancements = new HashMap<>();
        // Topological order guarantees every parent is built before its children.
        for (Task task : graph.topologicalOrder()) {
            if (!drawnIds.contains(task.id())) {
                continue;
            }
            List<BaseAdvancement> parents = task.lineParents(drawnIds).stream().map(advancements::get).toList();
            String key = TASK_KEY_PREFIX + task.id();
            AdvancementDisplay display = taskDisplay(graph, task, drawnIds, positions.get(task.id()));
            BaseAdvancement advancement = switch (parents.size()) {
                case 0 -> new BaseAdvancement(root, key, display);
                case 1 -> new BaseAdvancement(parents.getFirst(), key, display);
                // Draws a line to every parent through FakeAdvancements, which UAAPI neither saves nor registers.
                default -> new MultiParentsAdvancement(key, display, new LinkedHashSet<>(parents));
            };
            advancements.put(task.id(), advancement);
        }

        tab.registerAdvancements(root, new HashSet<>(advancements.values()));
        tab.automaticallyShowToPlayers();
        tab.automaticallyGrantRootAdvancement();
        return tab;
    }

    private static AdvancementDisplay rootDisplay(Project project, List<Task> tasks, int hidden, boolean newTabs) {
        long resolved = tasks.stream().filter(Task::resolved).count();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(resolved + " de " + tasks.size() + " tareas resueltas", NamedTextColor.GRAY));
        if (hidden > 0) {
            lines.add(Component.text(hidden == 1 ? "1 resuelta oculta" : hidden + " resueltas ocultas", NamedTextColor.DARK_GRAY));
        }
        lines.add(Component.text(newTabs
                ? "Nueva tarea acá: pestaña «+ Tarea»."
                : "Nueva tarea: /tareas o la tecla G.", NamedTextColor.DARK_GRAY));
        return new AdvancementDisplayBuilder(new ItemStack(ROOT_ICON), legacy(Component.text(project.title(), NamedTextColor.GOLD)))
                .description(lines.stream().map(TaskTabRenderer::legacy).toArray(String[]::new))
                .goalFrame()
                .coords(0, 0)
                .build();
    }

    private static AdvancementDisplay taskDisplay(TaskGraph graph, Task task, Set<String> drawnIds, NodePosition position) {
        List<Component> lines = new ArrayList<>();
        if (!task.description().isEmpty()) {
            lines.add(Component.text(task.description(), NamedTextColor.GRAY));
        }
        task.assigneeOptional().ifPresent(assignee ->
                lines.add(Component.text("Asignada a: " + assignee.name(), NamedTextColor.AQUA)));
        TaskTexts.alsoRequires(graph, task, drawnIds)
                .ifPresent(line -> lines.add(Component.text(line, NamedTextColor.YELLOW)));

        // Column 0 is taken by the root, so tasks start one column to the right.
        AdvancementDisplayBuilder builder = new AdvancementDisplayBuilder(TaskIcons.of(task.icon()), legacy(Component.text(task.title(), NamedTextColor.WHITE)))
                .description(lines.stream().map(TaskTabRenderer::legacy).toArray(String[]::new))
                .coords(position.column() + 1, position.row());
        AdvancementDisplayBuilder framed = switch (task.size()) {
            case SMALL -> builder.taskFrame();
            case MEDIUM -> builder.goalFrame();
            case LARGE -> builder.challengeFrame();
        };
        return framed.build();
    }

    private static String legacy(Component component) {
        return LEGACY.serialize(component);
    }
}
