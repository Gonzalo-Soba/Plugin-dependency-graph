package dev.taskgraph.graph;

import java.util.List;

/**
 * Hand-picked item groups for the icon picker. Paper doesn't expose the creative tabs
 * ({@code getCreativeCategory()} always answers building blocks), so the lists live here. Each fits on
 * one page of the picker. Whether every item exists in the running version is checked by the Paper layer.
 */
public enum IconCategory {

    BUILDING("Bloques", "bricks", List.of(
            "stone", "cobblestone", "stone_bricks", "deepslate_bricks", "bricks", "oak_planks", "spruce_planks",
            "birch_planks", "dark_oak_planks", "cherry_planks", "bamboo_planks", "oak_log", "spruce_log", "birch_log",
            "glass", "white_wool", "terracotta", "white_concrete", "sandstone", "red_sandstone", "quartz_block",
            "prismarine", "purpur_block", "end_stone_bricks", "nether_bricks", "blackstone", "polished_andesite",
            "polished_diorite", "polished_granite", "tuff_bricks", "mud_bricks", "copper_block", "iron_block",
            "gold_block", "diamond_block", "emerald_block", "netherite_block", "obsidian", "oak_stairs",
            "stone_brick_stairs", "oak_slab", "smooth_stone", "calcite", "amethyst_block", "glowstone")),
    NATURE("Naturaleza", "grass_block", List.of(
            "grass_block", "dirt", "sand", "gravel", "clay", "snow_block", "ice", "oak_sapling", "cherry_sapling",
            "oak_leaves", "poppy", "dandelion", "sunflower", "cornflower", "lily_pad", "cactus", "sugar_cane", "bamboo",
            "kelp", "moss_block", "red_mushroom", "brown_mushroom", "pumpkin", "melon", "wheat_seeds", "coal_ore",
            "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore", "copper_ore",
            "ancient_debris", "netherrack", "soul_sand", "end_stone", "egg", "bone", "feather", "leather", "string",
            "honeycomb", "cobweb")),
    TOOLS("Herramientas", "iron_pickaxe", List.of(
            "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
            "iron_axe", "diamond_axe", "iron_shovel", "diamond_shovel", "iron_hoe", "diamond_hoe", "shears",
            "flint_and_steel", "fishing_rod", "bucket", "water_bucket", "lava_bucket", "compass", "recovery_compass",
            "clock", "spyglass", "map", "lead", "name_tag", "brush", "saddle", "elytra", "firework_rocket",
            "ender_pearl", "ender_eye", "bundle", "writable_book", "book", "torch", "lantern", "ladder")),
    COMBAT("Combate", "iron_sword", List.of(
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword", "mace",
            "trident", "bow", "crossbow", "arrow", "spectral_arrow", "shield", "iron_helmet", "iron_chestplate",
            "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "netherite_helmet", "netherite_chestplate", "turtle_helmet", "chainmail_chestplate", "leather_chestplate",
            "golden_apple", "enchanted_golden_apple", "totem_of_undying", "tnt", "end_crystal", "wind_charge",
            "potion", "splash_potion", "experience_bottle", "wither_skeleton_skull", "zombie_head", "creeper_head")),
    FOOD("Comida", "bread", List.of(
            "apple", "bread", "wheat", "carrot", "potato", "baked_potato", "beetroot", "beetroot_soup", "mushroom_stew",
            "rabbit_stew", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_cod",
            "cooked_salmon", "beef", "porkchop", "chicken", "cod", "salmon", "tropical_fish", "cookie", "cake",
            "pumpkin_pie", "melon_slice", "sweet_berries", "glow_berries", "honey_bottle", "milk_bucket",
            "golden_carrot", "dried_kelp", "suspicious_stew", "cocoa_beans", "sugar")),
    REDSTONE("Redstone", "redstone", List.of(
            "redstone", "redstone_torch", "redstone_block", "repeater", "comparator", "lever", "stone_button",
            "oak_button", "stone_pressure_plate", "tripwire_hook", "piston", "sticky_piston", "observer", "dispenser",
            "dropper", "hopper", "chest", "trapped_chest", "barrel", "target", "daylight_detector", "note_block",
            "jukebox", "redstone_lamp", "rail", "powered_rail", "detector_rail", "activator_rail", "minecart",
            "chest_minecart", "hopper_minecart", "tnt_minecart", "oak_door", "iron_door", "oak_trapdoor",
            "oak_fence_gate", "crafter", "lightning_rod", "sculk_sensor", "slime_block", "honey_block")),
    DECORATION("Decoración", "crafting_table", List.of(
            "crafting_table", "furnace", "blast_furnace", "smoker", "anvil", "enchanting_table", "bookshelf",
            "brewing_stand", "cauldron", "grindstone", "smithing_table", "stonecutter", "loom", "cartography_table",
            "fletching_table", "lectern", "composter", "bell", "campfire", "white_bed", "red_bed", "painting",
            "item_frame", "armor_stand", "flower_pot", "white_banner", "red_banner", "white_carpet", "oak_sign",
            "oak_fence", "glass_pane", "iron_bars", "candle", "sea_lantern", "end_rod", "decorated_pot", "beacon",
            "conduit", "ender_chest", "shulker_box", "respawn_anchor", "lodestone", "scaffolding")),
    MISC("Varios", "diamond", List.of(
            "diamond", "emerald", "gold_ingot", "iron_ingot", "copper_ingot", "netherite_ingot", "coal", "charcoal",
            "lapis_lazuli", "quartz", "amethyst_shard", "raw_iron", "raw_gold", "raw_copper", "gold_nugget",
            "iron_nugget", "flint", "stick", "paper", "slime_ball", "gunpowder", "blaze_rod", "ghast_tear",
            "nether_star", "heart_of_the_sea", "nautilus_shell", "prismarine_shard", "echo_shard", "glass_bottle",
            "bowl", "clay_ball", "brick", "nether_brick", "magma_cream", "phantom_membrane", "rabbit_foot", "ink_sac",
            "glow_ink_sac", "white_dye", "red_dye", "blue_dye", "enchanted_book", "music_disc_cat", "trial_key",
            "heavy_core"));

    /** Items the picker shows per page: five rows of a double chest, the sixth holds the buttons. */
    public static final int PAGE_SIZE = 45;

    private static final String NAMESPACE = "minecraft:";

    private final String title;
    private final String icon;
    private final List<String> items;

    IconCategory(String title, String icon, List<String> items) {
        this.title = title;
        this.icon = NAMESPACE + icon;
        this.items = items.stream().map(item -> NAMESPACE + item).toList();
    }

    /** Name players see. */
    public String title() {
        return title;
    }

    /** Key of the item that stands for the category. */
    public String icon() {
        return icon;
    }

    /** Namespaced item keys, in display order. */
    public List<String> items() {
        return items;
    }
}
