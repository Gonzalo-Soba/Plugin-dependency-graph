package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;

/**
 * Translates projects to UAAPI tab namespaces. General keeps the namespace from before projects
 * existed, so its progress carries over. Project ids only use {@code [a-z0-9_]}, so the dot after the
 * prefix keeps every project namespace apart from General and from tabs that are not projects.
 */
public final class TabNamespaces {

    public static final String GENERAL = "taskgraph";
    static final String PROJECT_PREFIX = GENERAL + ".";

    private TabNamespaces() {
    }

    public static String of(String projectId) {
        return Project.GENERAL_ID.equals(projectId) ? GENERAL : PROJECT_PREFIX + projectId;
    }
}
