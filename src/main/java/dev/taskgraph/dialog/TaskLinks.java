package dev.taskgraph.dialog;

import dev.taskgraph.action.LinkedText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.BiConsumer;

/**
 * Turns a {@link LinkedText} into a chat message where each task opens its task screen when clicked. The
 * click looks the task up again at that moment, so a deleted task falls back to the project list with a
 * notice. The console gets the same text, without links.
 */
public final class TaskLinks {

    // Chat lines stay on screen long after they are sent: the links work many times and for a long while.
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(ClickCallback.DEFAULT_LIFETIME)
            .build();
    private static final Component HOVER = Component.text("Abrir ficha", NamedTextColor.GRAY);

    private final Plugin plugin;
    private final BiConsumer<Player, String> openTask;

    /** @param openTask opens the task screen of an internal id for a player, on the main thread */
    public TaskLinks(Plugin plugin, BiConsumer<Player, String> openTask) {
        this.plugin = plugin;
        this.openTask = openTask;
    }

    public Component component(LinkedText text, TextColor color) {
        TextComponent.Builder line = Component.text().color(color);
        for (LinkedText.Part part : text.parts()) {
            line.append(part.taskIdOptional()
                    .map(taskId -> link(part.text(), taskId))
                    .orElseGet(() -> Component.text(part.text())));
        }
        return line.build();
    }

    private Component link(String text, String taskId) {
        return Component.text(text)
                .decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(HOVER))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (!(audience instanceof Player player)) {
                        return;
                    }
                    Runnable open = () -> {
                        if (player.isOnline()) {
                            openTask.accept(player, taskId);
                        }
                    };
                    if (Bukkit.isPrimaryThread()) {
                        open.run();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, open);
                    }
                }, OPTIONS));
    }
}
