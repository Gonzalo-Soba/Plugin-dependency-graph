package dev.taskgraph.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAdvancementTab;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSelectAdvancementsTab;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * The only class that uses PacketEvents. Loaded by name from {@link TabPackets#load} once the plugin is
 * enabled; nothing else refers to it.
 */
final class PacketEventsTabPackets implements TabPackets {

    // -Dtaskgraph.debugTabs=true logs every tab packet, to check what the client sends when the screen opens.
    private static final boolean DEBUG = Boolean.getBoolean("taskgraph.debugTabs");

    private final Logger logger;
    private PacketListenerCommon listener;

    PacketEventsTabPackets(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void start(Listener tabListener) {
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.ADVANCEMENT_TAB) {
                    return;
                }
                WrapperPlayClientAdvancementTab packet = new WrapperPlayClientAdvancementTab(event);
                UUID player = event.getUser().getUUID();
                if (DEBUG) {
                    logger.info("[debug] " + player + " " + packet.getAction() + " " + packet.getTabId().orElse("-"));
                }
                if (player == null) {
                    return;
                }
                if (packet.getAction() == WrapperPlayClientAdvancementTab.Action.OPENED_TAB) {
                    packet.getTabId().ifPresent(tabId -> tabListener.opened(player, tabId));
                } else {
                    tabListener.closed(player);
                }
            }
        }, PacketListenerPriority.NORMAL);
    }

    @Override
    public void select(Player player, String tabId) {
        if (DEBUG) {
            logger.info("[debug] select " + tabId + " for " + player.getName());
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerSelectAdvancementsTab(new ResourceLocation(tabId)));
    }

    @Override
    public void stop() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
    }
}
