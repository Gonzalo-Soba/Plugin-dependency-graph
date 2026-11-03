package dev.taskgraph.listener;

import dev.taskgraph.advancement.TabMemory;
import dev.taskgraph.dialog.QuickAccess;
import dev.taskgraph.dialog.TaskDialogs;
import dev.taskgraph.service.TaskService;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Reacts to the buttons of the quick access dialog (quick actions key or pause menu): "Abrir lista" opens
 * the same list as {@code /tareas}, and "Nueva tarea" starts a task in the project of the last tab the
 * player opened (General without PacketEvents, which is what tells which tab that was).
 */
public final class QuickAccessListener implements Listener {

    private final Plugin plugin;
    private final TaskDialogs dialogs;
    private final TaskService service;
    private final TabMemory tabs;

    public QuickAccessListener(Plugin plugin, TaskDialogs dialogs, TaskService service, TabMemory tabs) {
        this.plugin = plugin;
        this.dialogs = dialogs;
        this.service = service;
        this.tabs = tabs;
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        boolean openList = event.getIdentifier().equals(QuickAccess.OPEN_LIST);
        boolean newTask = event.getIdentifier().equals(QuickAccess.NEW_TASK);
        if (!(openList || newTask) || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        Runnable open = () -> {
            if (openList) {
                dialogs.openList(player);
            } else {
                dialogs.openNewTask(player, tabs.origin(player.getUniqueId(), id -> service.project(id).isPresent()));
            }
        };
        // The event's thread isn't documented; dialogs and task changes belong on the main thread.
        if (Bukkit.isPrimaryThread()) {
            open.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    open.run();
                }
            });
        }
    }
}
