package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The advancement tabs each online player looks at, from the tab packets PacketEvents reads. Used from
 * the main thread only.
 * <p>
 * It answers two things. Which project "+ Tarea" creates in: the last project tab the player opened.
 * And which tab to bring back when the screen opens: the client doesn't keep the selected tab between
 * openings and falls back to the first one (a vanilla tab), so when the screen opens on a tab other than
 * the last one the player looked at, that one is selected again. The "+" tabs are never remembered.
 */
public final class TabMemory {

    /**
     * How long after the screen opens the client may report its default tab again (it does it twice in a
     * row) without that counting as the player choosing it.
     */
    static final long OPENING_WINDOW_MILLIS = 1000;

    private final Map<UUID, State> states = new HashMap<>();

    /**
     * Records that the player opened a tab, by its client id ({@code namespace:<root key>}).
     *
     * @param now           when the packet arrived, in milliseconds
     * @param projectExists whether a project id still exists, so a deleted project's tab isn't brought back
     * @return the tab id to select again when the screen opened on a different one, or empty
     */
    public Optional<String> opened(UUID player, String tabId, long now, Predicate<String> projectExists) {
        State state = states.computeIfAbsent(player, id -> new State());
        String namespace = TabNamespaces.ofTabId(tabId);
        boolean opening = !state.screenOpen;
        state.screenOpen = true;
        if (namespace.equals(TabNamespaces.NEW_TASK) || namespace.equals(TabNamespaces.NEW_PROJECT)) {
            return Optional.empty();
        }
        if (state.lastTabId != null && !stillExists(state.lastTabId, projectExists)) {
            state.lastTabId = null;
        }
        boolean clientDefault = opening
                || (tabId.equals(state.defaultTabId) && now - state.openedAt <= OPENING_WINDOW_MILLIS);
        if (clientDefault && state.lastTabId != null && !state.lastTabId.equals(tabId)) {
            if (opening) {
                state.defaultTabId = tabId;
                state.openedAt = now;
            }
            return Optional.of(state.lastTabId);
        }
        state.defaultTabId = null;
        state.lastTabId = tabId;
        TabNamespaces.projectOf(namespace).ifPresent(project -> state.lastProject = project);
        return Optional.empty();
    }

    public void closed(UUID player) {
        State state = states.get(player);
        if (state != null) {
            state.screenOpen = false;
        }
    }

    /**
     * The project of the last tab the player opened, or General if they opened none or that project
     * no longer exists.
     */
    public String origin(UUID player, Predicate<String> projectExists) {
        State state = states.get(player);
        String projectId = state == null ? null : state.lastProject;
        return projectId != null && projectExists.test(projectId) ? projectId : Project.GENERAL_ID;
    }

    public void forget(UUID player) {
        states.remove(player);
    }

    private static boolean stillExists(String tabId, Predicate<String> projectExists) {
        return TabNamespaces.projectOf(TabNamespaces.ofTabId(tabId)).map(projectExists::test).orElse(true);
    }

    private static final class State {

        private String lastTabId;
        private String lastProject;
        private boolean screenOpen;
        // The tab the client opened on by itself, and when, while a restore is pending.
        private String defaultTabId;
        private long openedAt;
    }
}
