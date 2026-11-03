package dev.taskgraph.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Changes needed so a player's advancements match the stored graph, which is the source of truth.
 * Granted keys that are no longer resolved, or that no task uses anymore, are revoked.
 */
public record SyncPlan(SortedSet<TaskKey> toGrant, SortedSet<TaskKey> toRevoke) {

    public SyncPlan {
        toGrant = Collections.unmodifiableSortedSet(new TreeSet<>(toGrant));
        toRevoke = Collections.unmodifiableSortedSet(new TreeSet<>(toRevoke));
    }

    /**
     * @param resolvedKeys keys of the tasks resolved in the graph
     * @param grantedKeys  keys the player currently has granted, possibly including retired ones
     */
    public static SyncPlan between(Collection<TaskKey> resolvedKeys, Collection<TaskKey> grantedKeys) {
        TreeSet<TaskKey> toGrant = new TreeSet<>(resolvedKeys);
        toGrant.removeAll(grantedKeys);
        TreeSet<TaskKey> toRevoke = new TreeSet<>(grantedKeys);
        toRevoke.removeAll(resolvedKeys);
        return new SyncPlan(toGrant, toRevoke);
    }

    /** Every key a player may have progress on: current tasks first, then retired keys. */
    public static Collection<TaskKey> knownKeys(TaskGraph graph) {
        Collection<TaskKey> keys = new LinkedHashSet<>(graph.currentKeys());
        keys.addAll(graph.retiredKeys());
        return keys;
    }

    public boolean isEmpty() {
        return toGrant.isEmpty() && toRevoke.isEmpty();
    }
}
