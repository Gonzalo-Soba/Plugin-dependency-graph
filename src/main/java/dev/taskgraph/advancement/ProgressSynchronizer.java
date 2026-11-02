package dev.taskgraph.advancement;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.database.DatabaseManager;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.SyncPlan;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes each player's UAAPI progress match the graph: resolved tasks are granted, everything else
 * (unresolved tasks and retired keys of deleted or moved tasks) is revoked.
 */
public final class ProgressSynchronizer {

    private final Logger logger;

    public ProgressSynchronizer(Logger logger) {
        this.logger = logger;
    }

    /** @param tabs the tab of every project, by project id */
    public void syncOnlinePlayers(TaskGraph graph, Map<String, AdvancementTab> tabs) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player, graph, tabs);
        }
    }

    /** @param tabs the tab of every project, by project id */
    public void sync(Player player, TaskGraph graph, Map<String, AdvancementTab> tabs) {
        // Every tab shares UAAPI's single database, and General always has one.
        DatabaseManager database = tabs.get(Project.GENERAL_ID).getDatabaseManager();
        // Players still loading are synced again by PlayerLoadingCompletedEvent.
        if (!database.isLoadedAndOnline(player)) {
            return;
        }
        TeamProgression progression = database.getTeamProgression(player);

        Set<TaskKey> grantedKeys = new LinkedHashSet<>();
        for (TaskKey key : SyncPlan.knownKeys(graph)) {
            if (progression.getRawProgression(TaskTabRenderer.taskKey(key)) > 0) {
                grantedKeys.add(key);
            }
        }

        SyncPlan plan = SyncPlan.between(graph.resolvedKeys(), grantedKeys);
        for (TaskKey key : plan.toGrant()) {
            advancement(tabs, key).grant(player, false);
        }
        for (TaskKey key : plan.toRevoke()) {
            Advancement advancement = advancement(tabs, key);
            if (advancement != null) {
                advancement.revoke(player);
            } else {
                // Retired key: no tab shows it anymore, so clear it straight in the database.
                database.setProgression(TaskTabRenderer.taskKey(key), player, 0).exceptionally(error -> {
                    logger.log(Level.WARNING, "Could not clear progress of retired key " + key + " for " + player.getName(), error);
                    return null;
                });
            }
        }
    }

    /** The advancement drawn for the key, or null if no tab shows it. */
    private static Advancement advancement(Map<String, AdvancementTab> tabs, TaskKey key) {
        AdvancementTab tab = tabs.get(key.project());
        return tab == null ? null : tab.getAdvancement(TaskTabRenderer.taskKey(key));
    }
}
