package dev.taskgraph;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import dev.taskgraph.action.TaskActions;
import dev.taskgraph.advancement.ProgressSynchronizer;
import dev.taskgraph.advancement.TaskTabPublisher;
import dev.taskgraph.advancement.TaskTabRenderer;
import dev.taskgraph.command.TaskCommand;
import dev.taskgraph.command.TasksCommand;
import dev.taskgraph.dialog.TaskDialogs;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.listener.PlayerSyncListener;
import dev.taskgraph.listener.QuickAccessListener;
import dev.taskgraph.service.TaskService;
import dev.taskgraph.storage.JsonTaskStore;
import dev.taskgraph.storage.TaskStoreException;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

public final class TaskGraphPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Must run before any UltimateAdvancementAPI class is touched: a 2.x install fails with NoClassDefFoundError.
        if (!hasSupportedUaapi()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        JsonTaskStore store = new JsonTaskStore(getDataPath().resolve("tasks.json"), getLogger());
        TaskGraph graph;
        try {
            graph = store.load();
        } catch (TaskStoreException exception) {
            // Disabling instead of starting empty keeps the broken file from being overwritten on the next save.
            getLogger().log(Level.SEVERE, "Could not load " + store.file() + ", disabling TaskGraph", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Loaded " + graph.size() + " tasks in " + graph.projects().size() + " projects");

        UltimateAdvancementAPI api = UltimateAdvancementAPI.getInstance(this);
        TaskTabPublisher publisher = new TaskTabPublisher(new TaskTabRenderer(api), new ProgressSynchronizer(getLogger()));
        TaskService service = new TaskService(store, graph, publisher::publish);
        service.start();
        getServer().getPluginManager().registerEvents(new PlayerSyncListener(publisher), this);

        TaskActions actions = new TaskActions(service,
                message -> getServer().broadcast(Component.text(message, NamedTextColor.GREEN)), getLogger());
        TaskCommand command = new TaskCommand(service, actions);
        TaskDialogs dialogs = new TaskDialogs(this, service, actions);
        TasksCommand tasksCommand = new TasksCommand(dialogs);
        getServer().getPluginManager().registerEvents(new QuickAccessListener(this, dialogs), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(command.build(), TaskCommand.DESCRIPTION, List.of());
            event.registrar().register(tasksCommand.build(), TasksCommand.DESCRIPTION, List.of());
        });
    }

    private boolean hasSupportedUaapi() {
        Plugin uaapi = getServer().getPluginManager().getPlugin(UaapiVersion.PLUGIN_NAME);
        // paper-plugin.yml marks it as required, so Paper already refuses to load without it; this guards the version.
        String installed = uaapi == null ? "no version" : uaapi.getPluginMeta().getVersion();
        if (UaapiVersion.isSupported(installed)) {
            return true;
        }
        getLogger().severe(UaapiVersion.unsupportedMessage(installed));
        return false;
    }

    @Override
    public void onDisable() {
        // UAAPI unregisters this plugin's tabs on PluginDisableEvent.
        getLogger().info("TaskGraph disabled");
    }
}
