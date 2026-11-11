package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskPermissions.Actor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainActionTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private static final Actor ALEX_PLAYER = Actor.player(ALEX.uuid(), ALEX.name(), false);
    private static final Actor ADMIN = Actor.player(UUID.randomUUID(), "Admin", true);

    private static Task task(PlayerRef assignee, boolean resolved) {
        return new Task("a", "A", "", "minecraft:stone", List.of(), assignee, resolved, null);
    }

    @Test
    void resolvedTaskOffersReopen() {
        assertEquals(MainAction.REOPEN, MainAction.of(task(null, true), TaskStatus.RESOLVED, ALEX_PLAYER));
        assertEquals(MainAction.REOPEN, MainAction.of(task(STEVE, true), TaskStatus.RESOLVED, ALEX_PLAYER));
    }

    @Test
    void myAvailableTaskOffersResolve() {
        assertEquals(MainAction.RESOLVE, MainAction.of(task(ALEX, false), TaskStatus.AVAILABLE, ALEX_PLAYER));
    }

    @Test
    void myBlockedTaskOffersNothing() {
        assertEquals(MainAction.NONE, MainAction.of(task(ALEX, false), TaskStatus.BLOCKED, ALEX_PLAYER));
    }

    @Test
    void freeTaskOffersAssignWhetherAvailableOrBlocked() {
        assertEquals(MainAction.ASSIGN, MainAction.of(task(null, false), TaskStatus.AVAILABLE, ALEX_PLAYER));
        assertEquals(MainAction.ASSIGN, MainAction.of(task(null, false), TaskStatus.BLOCKED, ALEX_PLAYER));
    }

    @Test
    void someoneElsesTaskOffersNothingToPlayers() {
        assertEquals(MainAction.NONE, MainAction.of(task(STEVE, false), TaskStatus.AVAILABLE, ALEX_PLAYER));
        assertEquals(MainAction.NONE, MainAction.of(task(STEVE, false), TaskStatus.BLOCKED, ALEX_PLAYER));
    }

    @Test
    void someoneElsesTaskOffersReassignToAdmins() {
        assertEquals(MainAction.REASSIGN, MainAction.of(task(STEVE, false), TaskStatus.AVAILABLE, ADMIN));
        assertEquals(MainAction.REASSIGN, MainAction.of(task(STEVE, false), TaskStatus.BLOCKED, ADMIN));
    }

    @Test
    void adminsOwnTaskStillOffersResolve() {
        Actor admin = Actor.player(ALEX.uuid(), ALEX.name(), true);
        assertEquals(MainAction.RESOLVE, MainAction.of(task(ALEX, false), TaskStatus.AVAILABLE, admin));
    }

    @Test
    void consoleOnlyGetsReopen() {
        assertEquals(MainAction.NONE, MainAction.of(task(null, false), TaskStatus.AVAILABLE, Actor.console()));
        assertEquals(MainAction.REOPEN, MainAction.of(task(null, true), TaskStatus.RESOLVED, Actor.console()));
    }
}
