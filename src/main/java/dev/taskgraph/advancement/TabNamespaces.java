package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Translates projects to UAAPI tab namespaces. General keeps the namespace from before projects
 * existed, so its progress carries over. Project ids only use {@code [a-z0-9_]}, so the dot after the
 * prefix keeps every project namespace apart from General, and the hyphen keeps the "+" tabs apart from
 * every project.
 */
public final class TabNamespaces {

    public static final String GENERAL = "taskgraph";
    static final String PROJECT_PREFIX = GENERAL + ".";
    /** The "+ Tarea" tab: opening it starts a new task in the project the player was looking at. */
    public static final String NEW_TASK = GENERAL + "-new-task";
    /** The "+ Proyecto" tab: opening it starts a new project. */
    public static final String NEW_PROJECT = GENERAL + "-new-project";

    private static final Pattern PROJECT_ID = Pattern.compile("[a-z0-9_]+");

    private TabNamespaces() {
    }

    public static String of(String projectId) {
        return Project.GENERAL_ID.equals(projectId) ? GENERAL : PROJECT_PREFIX + projectId;
    }

    /** The project whose tab uses this namespace, or empty for any other tab (the "+" tabs, other plugins). */
    public static Optional<String> projectOf(String namespace) {
        if (GENERAL.equals(namespace)) {
            return Optional.of(Project.GENERAL_ID);
        }
        if (namespace.startsWith(PROJECT_PREFIX)) {
            String id = namespace.substring(PROJECT_PREFIX.length());
            return PROJECT_ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Namespace of a tab as the client names it: the id of its root advancement, {@code namespace:<root key>}.
     * An id without a namespace is a vanilla {@code minecraft:} one.
     */
    public static String ofTabId(String tabId) {
        int colon = tabId.indexOf(':');
        return colon < 0 ? "minecraft" : tabId.substring(0, colon);
    }
}
