package dev.taskgraph.listener;

import dev.taskgraph.advancement.TabMemory;
import dev.taskgraph.advancement.TabNamespaces;
import dev.taskgraph.advancement.TaskTabRenderer;
import dev.taskgraph.dialog.TaskDialogs;
import dev.taskgraph.packet.TabPackets;
import dev.taskgraph.service.TaskService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Reacts to the advancement tabs a player opens. Opening "+ Tarea" or "+ Proyecto" opens the matching
 * dialog and selects the origin tab again, so the client never keeps a "+" tab selected and the dialog
 * doesn't open by itself the next time. Opening the screen on a tab other than the last one the player
 * looked at selects that one again (see {@link TabMemory}).
 * <p>
 * Packets arrive on a network thread; each one is handled on the main thread, in arrival order.
 */
public final class NewTabListener implements Listener, TabPackets.Listener {

    private final Plugin plugin;
    private final TaskService service;
    private final TaskDialogs dialogs;
    private final TabPackets packets;
    private final TabMemory tabs;

    public NewTabListener(Plugin plugin, TaskService service, TaskDialogs dialogs, TabPackets packets, TabMemory tabs) {
        this.plugin = plugin;
        this.service = service;
        this.dialogs = dialogs;
        this.packets = packets;
        this.tabs = tabs;
    }

    @Override
    public void opened(UUID playerId, String tabId) {
        long now = System.currentTimeMillis();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            tabs.opened(playerId, tabId, now, this::projectExists).ifPresent(lastTab -> packets.select(player, lastTab));

            String namespace = TabNamespaces.ofTabId(tabId);
            boolean newTask = namespace.equals(TabNamespaces.NEW_TASK);
            if (!newTask && !namespace.equals(TabNamespaces.NEW_PROJECT)) {
                return;
            }
            String origin = tabs.origin(playerId, this::projectExists);
            if (newTask) {
                dialogs.openNewTask(player, origin);
            } else {
                dialogs.openNewProject(player);
            }
            packets.select(player, TaskTabRenderer.tabId(TabNamespaces.of(origin)));
        });
    }

    @Override
    public void closed(UUID playerId) {
        Bukkit.getScheduler().runTask(plugin, () -> tabs.closed(playerId));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tabs.forget(event.getPlayer().getUniqueId());
    }

    private boolean projectExists(String projectId) {
        return service.project(projectId).isPresent();
    }
}
