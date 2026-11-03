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
 * (unresolved tasks and retired keys of deleted or moved tasks) is revoked. Keys with no advancement in
 * any tab (retired keys, hidden tasks) are set straight in the database.
 */
public final class ProgressSynchronizer {

    // Task advancements keep UAAPI's default maximum progression of 1, so 1 means granted.
    private static final int GRANTED = 1;

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
            Advancement advancement = advancement(tabs, key);
            if (advancement != null) {
                advancement.grant(player, false);
            } else {
                // Hidden task: once shown again it is already granted, so no toast or announcement follows.
                setProgression(database, player, key, GRANTED);
            }
        }
        for (TaskKey key : plan.toRevoke()) {
            Advancement advancement = advancement(tabs, key);
            if (advancement != null) {
                advancement.revoke(player);
            } else {
                // Retired key or hidden task: no tab shows it, so clear it straight in the database.
                setProgression(database, player, key, 0);
            }
        }
    }

    private void setProgression(DatabaseManager database, Player player, TaskKey key, int progression) {
        database.setProgression(TaskTabRenderer.taskKey(key), player, progression).exceptionally(error -> {
            logger.log(Level.WARNING, "Could not set progress of " + key + " to " + progression + " for " + player.getName(), error);
            return null;
        });
    }

    /** The advancement drawn for the key, or null if no tab shows it. */
    private static Advancement advancement(Map<String, AdvancementTab> tabs, TaskKey key) {
        AdvancementTab tab = tabs.get(key.project());
        return tab == null ? null : tab.getAdvancement(TaskTabRenderer.taskKey(key));
    }
}
