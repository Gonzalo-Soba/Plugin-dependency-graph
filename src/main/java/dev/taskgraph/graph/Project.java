package dev.taskgraph.graph;

import java.util.Objects;

/**
 * A group of tasks shown as its own advancement tab. General always exists and can't be removed.
 *
 * @param id           internal id generated from the title; never shown to players
 * @param title        what players see and type; unique among projects ignoring case, accents and spacing
 * @param hideResolved whether finished branches are left out of the tab
 */
public record Project(String id, String title, boolean hideResolved) {

    public static final String GENERAL_ID = "general";
    public static final String GENERAL_TITLE = "General";

    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
    }

    public static Project general() {
        return new Project(GENERAL_ID, GENERAL_TITLE, false);
    }

    public boolean isGeneral() {
        return id.equals(GENERAL_ID);
    }

    Project withTitle(String newTitle) {
        return new Project(id, newTitle, hideResolved);
    }
}
