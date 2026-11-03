package dev.taskgraph.advancement;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import dev.taskgraph.graph.TaskGraph;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Keeps the advancement tabs in step with the graph: every published graph rebuilds the tabs and syncs
 * online players; players who finish loading later are synced against the last published graph.
 */
public final class TaskTabPublisher {

    private final TaskTabRenderer renderer;
    private final ProgressSynchronizer synchronizer;

    private TaskGraph graph;
    private Map<String, AdvancementTab> tabs;

    public TaskTabPublisher(TaskTabRenderer renderer, ProgressSynchronizer synchronizer) {
        this.renderer = renderer;
        this.synchronizer = synchronizer;
    }

    public void publish(TaskGraph graph) {
        this.graph = graph;
        tabs = renderer.render(graph);
        synchronizer.syncOnlinePlayers(graph, tabs);
    }

    public void syncPlayer(Player player) {
        if (tabs != null) {
            synchronizer.sync(player, graph, tabs);
        }
    }
}
