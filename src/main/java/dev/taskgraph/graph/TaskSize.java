package dev.taskgraph.graph;

import java.util.Arrays;
import java.util.Optional;

/**
 * How big a task is. The advancement tab shows it with the frame: task, goal or challenge.
 * Tasks stored before sizes existed are small.
 */
public enum TaskSize {

    SMALL("chica"),
    MEDIUM("mediana"),
    LARGE("grande");

    private final String word;

    TaskSize(String word) {
        this.word = word;
    }

    /** How players write it, in lowercase: {@code chica}, {@code mediana}, {@code grande}. */
    public String word() {
        return word;
    }

    /** The size a player typed, ignoring case, accents and surrounding spaces. */
    public static Optional<TaskSize> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = Titles.key(input);
        return Arrays.stream(values()).filter(size -> size.word.equals(key)).findFirst();
    }
}
