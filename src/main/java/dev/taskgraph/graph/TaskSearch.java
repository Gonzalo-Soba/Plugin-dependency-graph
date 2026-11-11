package dev.taskgraph.graph;

import java.util.Comparator;
import java.util.List;

/**
 * Finds tasks by part of their title, ignoring case, accents and repeated spaces ({@link Titles#key}), in
 * every project. The closest matches come first: the exact title, then titles that start with the text,
 * then titles with a word that starts with it, then any other title that contains it. Alphabetical within
 * each group.
 */
public final class TaskSearch {

    private static final int EXACT = 0;
    private static final int PREFIX = 1;
    private static final int WORD_PREFIX = 2;
    private static final int CONTAINS = 3;
    private static final int NO_MATCH = 4;

    private TaskSearch() {
    }

    /** Empty when the text is blank or nothing matches. */
    public static List<Task> find(TaskGraph graph, String text) {
        String query = Titles.key(text);
        if (query.isEmpty()) {
            return List.of();
        }
        return graph.tasks().stream()
                .filter(task -> rank(task, query) != NO_MATCH)
                .sorted(Comparator.<Task>comparingInt(task -> rank(task, query))
                        .thenComparing(task -> Titles.key(task.title())))
                .toList();
    }

    private static int rank(Task task, String query) {
        String title = Titles.key(task.title());
        if (title.equals(query)) {
            return EXACT;
        }
        if (title.startsWith(query)) {
            return PREFIX;
        }
        if (title.contains(" " + query)) {
            return WORD_PREFIX;
        }
        return title.contains(query) ? CONTAINS : NO_MATCH;
    }
}
