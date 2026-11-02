package dev.taskgraph.graph;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Title handling. Titles are what players see and type, so they are compared ignoring case, accents
 * and repeated spaces. Internal ids are derived from them once, at creation, and never shown.
 */
public final class Titles {

    static final int MAX_ID_LENGTH = 32;
    static final String FALLBACK_ID = "tarea";

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NON_ID_CHARS = Pattern.compile("[^a-z0-9]+");

    private Titles() {
    }

    /** Comparison key: "  Jardín  de la Ñandú " and "jardin de la nandu" are the same title. */
    public static String key(String title) {
        String withoutAccents = MARKS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutAccents.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    /** Trims and collapses inner whitespace, keeping case and accents as typed. */
    public static String clean(String title) {
        return SPACES.matcher(title.strip()).replaceAll(" ");
    }

    /**
     * Internal id derived from the title: {@code jardin_de_la_nandu}, then {@code _2}, {@code _3}... while
     * {@code taken} reports a clash. Titles with no usable characters fall back to {@code tarea}.
     */
    static String generateId(String title, Predicate<String> taken) {
        return generateId(title, FALLBACK_ID, taken);
    }

    /** Same as {@link #generateId(String, Predicate)}, with {@code fallback} for titles with no usable characters. */
    static String generateId(String title, String fallback, Predicate<String> taken) {
        String base = NON_ID_CHARS.matcher(key(title)).replaceAll("_");
        base = trimUnderscores(base);
        if (base.isEmpty()) {
            base = fallback;
        }
        String candidate = fit(base, "");
        for (int suffix = 2; taken.test(candidate); suffix++) {
            candidate = fit(base, "_" + suffix);
        }
        return candidate;
    }

    private static String fit(String base, String suffix) {
        String trimmed = base.length() + suffix.length() > MAX_ID_LENGTH
                ? trimUnderscores(base.substring(0, MAX_ID_LENGTH - suffix.length()))
                : base;
        return trimmed + suffix;
    }

    private static String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }
}
