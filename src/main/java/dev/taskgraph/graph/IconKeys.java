package dev.taskgraph.graph;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Item ids as players type them, turned into the namespaced keys tasks store as icons. Whether the
 * item exists and can be shown is up to the Paper layer.
 */
public final class IconKeys {

    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private IconKeys() {
    }

    /**
     * {@code diamond}, {@code minecraft:diamond} and {@code DIAMOND} all become {@code minecraft:diamond}.
     *
     * @return the key, or empty when the input is blank or can't be an item id
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = input.strip().toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = DEFAULT_NAMESPACE + key;
        }
        return KEY.matcher(key).matches() ? Optional.of(key) : Optional.empty();
    }

    /** The key as players read and type it: without the {@code minecraft:} namespace. */
    public static String display(String key) {
        return key.startsWith(DEFAULT_NAMESPACE) ? key.substring(DEFAULT_NAMESPACE.length()) : key;
    }
}
