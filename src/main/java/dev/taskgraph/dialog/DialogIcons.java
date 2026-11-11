package dev.taskgraph.dialog;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

/**
 * Vanilla sprites shown inside dialog labels, next to the text. Item textures live in the {@code items}
 * atlas (in {@code blocks} they render as the missing texture). All of these were checked in game on 26.3.
 */
final class DialogIcons {

    private static final Key ITEMS = Key.key("minecraft", "items");

    static final Component NEW = item("lime_dye");
    static final Component RENAME = item("writable_book");
    static final Component VISIBILITY = item("ender_eye");
    static final Component DELETE = item("barrier");

    private DialogIcons() {
    }

    /** The icon, a space and the text: "[icon] Nueva tarea". */
    static Component label(Component icon, String text, TextColor color) {
        return Component.empty().append(icon).append(Component.text(" " + text, color));
    }

    private static Component item(String name) {
        return Component.object(ObjectContents.sprite(ITEMS, Key.key("minecraft", "item/" + name)));
    }
}
