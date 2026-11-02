package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskPermissions.Actor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPermissionsTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private static final Actor ALEX_PLAYER = Actor.player(ALEX.uuid(), ALEX.name(), false);
    private static final Actor STEVE_PLAYER = Actor.player(STEVE.uuid(), STEVE.name(), false);
    private static final Actor ADMIN = Actor.player(UUID.randomUUID(), "Admin", true);

    private static Task task(PlayerRef creator, PlayerRef assignee) {
        return new Task("a", "A", "", "minecraft:stone", List.of(), assignee, false, creator);
    }

    @Nested
    class Projects {

        @Test
        void onlyAdminsAndConsoleRemoveProjects() {
            assertFalse(TaskPermissions.canRemoveProject(ALEX_PLAYER));
            assertTrue(TaskPermissions.canRemoveProject(ADMIN));
            assertTrue(TaskPermissions.canRemoveProject(Actor.console()));
        }
    }

    @Nested
    class Remove {

        @Test
        void creatorCanRemove() {
            assertTrue(TaskPermissions.canRemove(task(ALEX, null), ALEX_PLAYER));
        }

        @Test
        void otherPlayerCannotRemove() {
            assertFalse(TaskPermissions.canRemove(task(ALEX, STEVE), STEVE_PLAYER));
        }

        @Test
        void adminAndConsoleCanRemove() {
            assertTrue(TaskPermissions.canRemove(task(ALEX, null), ADMIN));
            assertTrue(TaskPermissions.canRemove(task(ALEX, null), Actor.console()));
        }

        @Test
        void taskWithoutCreatorOnlyByAdmins() {
            assertFalse(TaskPermissions.canRemove(task(null, null), ALEX_PLAYER));
            assertTrue(TaskPermissions.canRemove(task(null, null), ADMIN));
        }
    }

    @Nested
    class Release {

        @Test
        void assigneeCanRelease() {
            assertTrue(TaskPermissions.canRelease(task(STEVE, ALEX), ALEX_PLAYER));
        }

        @Test
        void otherPlayerCannotReleaseEvenIfCreator() {
            assertFalse(TaskPermissions.canRelease(task(STEVE, ALEX), STEVE_PLAYER));
        }

        @Test
        void adminCanRelease() {
            assertTrue(TaskPermissions.canRelease(task(STEVE, ALEX), ADMIN));
        }

        @Test
        void unassignedTaskCannotBeReleasedByPlayers() {
            assertFalse(TaskPermissions.canRelease(task(ALEX, null), ALEX_PLAYER));
        }
    }

    @Nested
    class AssignToSelf {

        @Test
        void freeTaskCanBeTaken() {
            assertTrue(TaskPermissions.canAssignToSelf(task(STEVE, null), ALEX_PLAYER));
        }

        @Test
        void ownTaskCanBeTakenAgain() {
            assertTrue(TaskPermissions.canAssignToSelf(task(STEVE, ALEX), ALEX_PLAYER));
        }

        @Test
        void taskOfSomeoneElseOnlyByAdmin() {
            assertFalse(TaskPermissions.canAssignToSelf(task(STEVE, STEVE), ALEX_PLAYER));
            assertTrue(TaskPermissions.canAssignToSelf(task(STEVE, STEVE), ADMIN));
        }
    }
}
