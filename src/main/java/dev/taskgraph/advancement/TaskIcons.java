package dev.taskgraph.advancement;

import dev.taskgraph.graph.IconKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Item shown for a task, both in the advancement tab and in dialogs.
 */
public final class TaskIcons {

    private static final Material FALLBACK = Material.PAPER;

    private TaskIcons() {
    }

    /** The stored material key, or paper when it is not a valid item (e.g. removed in a game update). */
    public static ItemStack of(String materialKey) {
        Material material = Material.matchMaterial(materialKey);
        return new ItemStack(usable(material) ? material : FALLBACK);
    }

    /**
     * Key of the item a player typed ({@code diamond}, {@code minecraft:diamond}, {@code DIAMOND}),
     * or empty when it names no item that can be shown as an icon.
     */
    public static Optional<String> parse(String input) {
        return IconKeys.normalize(input)
                .map(Material::matchMaterial)
                .filter(TaskIcons::usable)
                .map(material -> material.getKey().asString());
    }

    /** Whether the material can be shown as an icon: an item, and not air. */
    public static boolean usable(Material material) {
        return material != null && material.isItem() && !material.isAir();
    }
}
