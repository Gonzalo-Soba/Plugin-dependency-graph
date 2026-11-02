package dev.taskgraph.dialog;

import dev.taskgraph.advancement.TaskIcons;
import dev.taskgraph.graph.IconCategory;
import dev.taskgraph.graph.IconKeys;
import dev.taskgraph.graph.Page;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Chest menu to pick a task icon: a screen of categories, then the items of one category in pages.
 * Nothing in it can be taken out; every click is cancelled and only runs the slot's action.
 * <p>
 * Each opening is a session that ends exactly once: with the picked item, or with a cancel when the player
 * presses «Cancelar» or closes the chest. Moving between screens opens a new chest, which closes the old one
 * with reason {@code OPEN_NEW}; that close doesn't end the session.
 */
public final class IconPicker implements Listener {

    private static final int SIZE = 54;
    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};
    private static final int ALL_ITEMS_SLOT = 31;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;

    public IconPicker(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the categories screen.
     *
     * @param onPick   receives the key of the chosen item
     * @param onCancel runs when the player leaves without choosing
     */
    public void open(Player player, Consumer<String> onPick, Runnable onCancel) {
        showCategories(player, new Session(onPick, onCancel));
    }

    /** Catalog keys that name no usable item in the running version, to report at startup. */
    public static List<String> unknownCatalogKeys() {
        return Arrays.stream(IconCategory.values())
                .flatMap(category -> category.items().stream())
                .filter(key -> TaskIcons.parse(key).isEmpty())
                .toList();
    }

    // ---- Screens ----

    private void showCategories(Player player, Session session) {
        Menu menu = new Menu(session, Component.text("Elegir ícono"));
        IconCategory[] categories = IconCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            IconCategory category = categories[i];
            List<String> items = available(player, category.items());
            menu.set(CATEGORY_SLOTS[i], button(TaskIcons.of(category.icon()), category.title(), items.size() + " ítems"),
                    viewer -> showItems(viewer, session, category.title(), items, 0));
        }
        menu.set(ALL_ITEMS_SLOT, button(new ItemStack(Material.CHEST), "Todos los ítems", "En orden alfabético"),
                viewer -> showItems(viewer, session, "Todos los ítems", allItems(viewer), 0));
        menu.set(BACK_SLOT, button(new ItemStack(Material.BARRIER), "Cancelar", null), viewer -> finish(viewer, session, null));
        player.openInventory(menu.getInventory());
    }

    private void showItems(Player player, Session session, String title, List<String> keys, int requestedPage) {
        Page<String> page = Page.of(keys, requestedPage, IconCategory.PAGE_SIZE);
        String pageNote = page.count() > 1 ? " (" + (page.index() + 1) + "/" + page.count() + ")" : "";
        Menu menu = new Menu(session, Component.text(title + pageNote));
        for (int slot = 0; slot < page.items().size(); slot++) {
            String key = page.items().get(slot);
            ItemStack item = TaskIcons.of(key);
            item.editMeta(meta -> meta.lore(List.of(plain(IconKeys.display(key), NamedTextColor.DARK_GRAY),
                    plain("Clic para elegir", NamedTextColor.GRAY))));
            menu.set(slot, item, viewer -> finish(viewer, session, key));
        }
        if (page.hasPrevious()) {
            menu.set(PREVIOUS_SLOT, button(new ItemStack(Material.ARROW), "◀ Anterior", null),
                    viewer -> showItems(viewer, session, title, keys, page.index() - 1));
        }
        menu.set(BACK_SLOT, button(new ItemStack(Material.OAK_DOOR), "Volver", "A las categorías"),
                viewer -> showCategories(viewer, session));
        if (page.hasNext()) {
            menu.set(NEXT_SLOT, button(new ItemStack(Material.ARROW), "Siguiente ▶", null),
                    viewer -> showItems(viewer, session, title, keys, page.index() + 1));
        }
        player.openInventory(menu.getInventory());
    }

    /** Ends the session with the chosen key, or with a cancel when {@code key} is null. */
    private static void finish(Player player, Session session, String key) {
        if (session.finished) {
            return;
        }
        session.finished = true;
        // The close this causes is ignored because the session is already finished.
        player.closeInventory();
        if (key == null) {
            session.onCancel.run();
        } else {
            session.onPick.accept(key);
        }
    }

    // ---- Events ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        Consumer<Player> action = event.getRawSlot() < SIZE ? menu.actions.get(event.getRawSlot()) : null;
        if (action == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Opening or closing inventories inside a click event is unsafe, so it waits for the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !menu.session.finished) {
                action.accept(player);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)
                || event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Session session = menu.session;
        if (session.finished) {
            return;
        }
        session.finished = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                session.onCancel.run();
            }
        });
    }

    // ---- Items ----

    /** The keys that name a usable item, skipping those behind a feature flag the player's world lacks. */
    private static List<String> available(Player player, List<String> keys) {
        return keys.stream()
                .map(TaskIcons::parse)
                .flatMap(Optional::stream)
                .filter(key -> enabled(player, key))
                .toList();
    }

    private static List<String> allItems(Player player) {
        return Registry.ITEM.stream()
                .filter(type -> player.getWorld().isEnabled(type))
                .map(type -> type.getKey().asString())
                .filter(key -> TaskIcons.parse(key).isPresent())
                .sorted(Comparator.comparing(IconKeys::display))
                .toList();
    }

    private static boolean enabled(Player player, String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        ItemType type = namespacedKey == null ? null : Registry.ITEM.get(namespacedKey);
        return type != null && player.getWorld().isEnabled(type);
    }

    private static ItemStack button(ItemStack item, String name, String note) {
        item.editMeta(meta -> {
            meta.displayName(plain(name, NamedTextColor.WHITE));
            meta.lore(note == null ? null : List.of(plain(note, NamedTextColor.GRAY)));
        });
        return item;
    }

    /** Item names and lore are italic unless told otherwise. */
    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Session {

        private final Consumer<String> onPick;
        private final Runnable onCancel;
        private boolean finished;

        private Session(Consumer<String> onPick, Runnable onCancel) {
            this.onPick = onPick;
            this.onCancel = onCancel;
        }
    }

    /** Holder that marks a chest as one of these menus and keeps what each slot does. */
    private static final class Menu implements InventoryHolder {

        private final Session session;
        private final Inventory inventory;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        private Menu(Session session, Component title) {
            this.session = session;
            this.inventory = Bukkit.createInventory(this, SIZE, title);
        }

        private void set(int slot, ItemStack item, Consumer<Player> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
