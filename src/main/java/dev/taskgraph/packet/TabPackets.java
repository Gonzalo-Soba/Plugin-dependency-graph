package dev.taskgraph.packet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advancement tab packets: which tab a player opens, and forcing which tab is selected. Neither Paper nor
 * UAAPI exposes them, so they come from PacketEvents, an optional plugin.
 * <p>
 * Only {@link #load} touches the implementation, and only by name once PacketEvents is enabled. No other
 * class refers to it, so the plugin never fails with {@code NoClassDefFoundError} without PacketEvents.
 */
public interface TabPackets {

    String PLUGIN_NAME = "packetevents";

    /** What the client reports about its advancements screen. Called on a network thread. */
    interface Listener {

        /** The player opened a tab, by its id ({@code namespace:<root key>}). */
        void opened(UUID player, String tabId);

        void closed(UUID player);
    }

    void start(Listener listener);

    /** Makes the client select the tab with this id, as if the player had clicked it. */
    void select(Player player, String tabId);

    void stop();

    /** The PacketEvents implementation, or empty when the plugin isn't enabled. */
    static Optional<TabPackets> load(Logger logger) {
        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            return Optional.empty();
        }
        try {
            // By name, so this interface never links against the class that imports PacketEvents.
            Class<?> type = Class.forName("dev.taskgraph.packet.PacketEventsTabPackets");
            return Optional.of((TabPackets) type.getDeclaredConstructor(Logger.class).newInstance(logger));
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.log(Level.WARNING, "PacketEvents is enabled but could not be used; the \"+\" tabs are off", exception);
            return Optional.empty();
        }
    }
}
