package dev.taskgraph.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlanTest {

    private static final String ICON = "minecraft:stone";

    private static TaskKey general(String task) {
        return new TaskKey(Project.GENERAL_ID, task);
    }

    @Test
    void grantsResolvedTasksThePlayerIsMissing() {
        SyncPlan plan = SyncPlan.between(Set.of(general("a"), general("b")), Set.of(general("a")));

        assertEquals(Set.of(general("b")), plan.toGrant());
        assertTrue(plan.toRevoke().isEmpty());
    }

    @Test
    void revokesKeysThatAreNoLongerResolvedOrWereRetired() {
        SyncPlan plan = SyncPlan.between(Set.of(general("a")), Set.of(general("a"), general("unresolved"), general("deleted")));

        assertTrue(plan.toGrant().isEmpty());
        assertEquals(Set.of(general("deleted"), general("unresolved")), plan.toRevoke());
    }

    @Test
    void isEmptyWhenPlayerIsInSync() {
        SyncPlan plan = SyncPlan.between(Set.of(general("a"), general("b")), Set.of(general("b"), general("a")));

        assertTrue(plan.isEmpty());
    }

    @Test
    void keysAreSortedByProjectThenTask() {
        SyncPlan plan = SyncPlan.between(
                Set.of(new TaskKey("base", "b"), general("c"), new TaskKey("base", "a")), Set.of());

        assertFalse(plan.isEmpty());
        assertEquals(List.of(new TaskKey("base", "a"), new TaskKey("base", "b"), general("c")), List.copyOf(plan.toGrant()));
    }

    @Test
    void movedResolvedTaskIsGrantedInItsNewProjectAndRevokedInTheOld() {
        TaskGraph graph = new TaskGraph();
        Project base = graph.createProject("Base");
        Task wood = graph.create("Madera", ICON);
        graph.resolve(wood.id());
        Set<TaskKey> granted = Set.of(general(wood.id()));

        graph.moveTask(wood.id(), base.id());
        Set<TaskKey> known = Set.copyOf(SyncPlan.knownKeys(graph));
        SyncPlan plan = SyncPlan.between(graph.resolvedKeys(), granted.stream().filter(known::contains).toList());

        assertEquals(Set.of(new TaskKey(base.id(), wood.id())), plan.toGrant());
        assertEquals(Set.of(general(wood.id())), plan.toRevoke());
    }

    @Test
    void knownKeysAreCurrentKeysThenRetiredKeys() {
        TaskGraph graph = new TaskGraph();
        Project base = graph.createProject("Base");
        Task wood = graph.create("Madera", ICON);
        Task wall = graph.create("Muralla", ICON, null, base.id());
        Task old = graph.create("Vieja", ICON);
        graph.remove(old.id());

        assertEquals(List.of(general(wood.id()), new TaskKey(base.id(), wall.id()), general(old.id())),
                List.copyOf(SyncPlan.knownKeys(graph)));
    }

    @Test
    void worksWithGraphResolvedKeys() {
        TaskGraph graph = new TaskGraph();
        graph.create("a", ICON);
        graph.create("b", ICON);
        graph.resolve("a");

        SyncPlan plan = SyncPlan.between(graph.resolvedKeys(), Set.of(general("b")));

        assertEquals(Set.of(general("a")), plan.toGrant());
        assertEquals(Set.of(general("b")), plan.toRevoke());
    }
}
