package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskGraphException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskGraphProjectsTest {

    private static final String ICON = "minecraft:stone";
    private static final String GENERAL = Project.GENERAL_ID;

    private TaskGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TaskGraph();
    }

    private static TaskGraphException assertRejected(Reason reason, Executable action) {
        TaskGraphException exception = assertThrows(TaskGraphException.class, action);
        assertEquals(reason, exception.reason());
        return exception;
    }

    private List<String> projectIds() {
        return graph.projects().stream().map(Project::id).toList();
    }

    @Nested
    class Projects {

        @Test
        void newGraphHasOnlyGeneral() {
            assertEquals(List.of(Project.general()), graph.projects());
        }

        @Test
        void createsProjectsAfterGeneralWithIdFromTitle() {
            Project base = graph.createProject("  Base   del Norte ");

            assertEquals(new Project("base_del_norte", "Base del Norte", false), base);
            assertEquals(List.of(GENERAL, "base_del_norte"), projectIds());
        }

        @Test
        void titlesAreUniqueIgnoringCaseAccentsAndSpaces() {
            graph.createProject("Minería");

            assertRejected(Reason.DUPLICATE_PROJECT_TITLE, () -> graph.createProject("MINERIA"));
            assertRejected(Reason.DUPLICATE_PROJECT_TITLE, () -> graph.createProject("general"));
        }

        @Test
        void projectAndTaskTitlesDoNotClash() {
            graph.create("Base", ICON);

            assertEquals("Base", graph.createProject("Base").title());
        }

        @Test
        void rejectsBlankTitle() {
            assertRejected(Reason.INVALID_TITLE, () -> graph.createProject("  "));
        }

        @Test
        void titleWithoutIdCharactersFallsBackToProyecto() {
            assertEquals("proyecto", graph.createProject("¡¿?!").id());
        }

        @Test
        void renameKeepsIdAndAllowsOwnTitle() {
            Project base = graph.createProject("Base");

            assertEquals(new Project(base.id(), "Base principal", false), graph.renameProject(base.id(), "Base principal"));
            assertEquals("BASE PRINCIPAL", graph.renameProject(base.id(), "BASE PRINCIPAL").title());
            assertRejected(Reason.DUPLICATE_PROJECT_TITLE, () -> graph.renameProject(base.id(), "General"));
        }

        @Test
        void generalCanBeRenamedButNotRemoved() {
            graph.renameProject(GENERAL, "Todo");

            assertEquals("Todo", graph.project(GENERAL).orElseThrow().title());
            assertRejected(Reason.GENERAL_PROJECT, () -> graph.removeProject(GENERAL));
        }

        @Test
        void removingProjectWithTasksIsRejectedListingThem() {
            Project base = graph.createProject("Base");
            Task wall = graph.create("Muralla", ICON, null, base.id());

            TaskGraphException exception = assertRejected(Reason.PROJECT_NOT_EMPTY, () -> graph.removeProject(base.id()));

            assertEquals(base.id(), exception.taskId());
            assertEquals(List.of(wall.id()), exception.relatedIds());
            assertTrue(graph.project(base.id()).isPresent());
        }

        @Test
        void removesEmptyProject() {
            Project base = graph.createProject("Base");

            graph.removeProject(base.id());

            assertEquals(List.of(GENERAL), projectIds());
        }

        @Test
        void unknownProjectIsRejected() {
            assertRejected(Reason.PROJECT_NOT_FOUND, () -> graph.removeProject("ghost"));
            assertRejected(Reason.PROJECT_NOT_FOUND, () -> graph.renameProject("ghost", "X"));
            assertRejected(Reason.PROJECT_NOT_FOUND, () -> graph.tasksIn("ghost"));
        }

        @Test
        void findsProjectByTitleIgnoringCaseAndAccents() {
            Project mining = graph.createProject("Minería");

            assertEquals(mining, graph.findProjectByTitle("mineria").orElseThrow());
            assertTrue(graph.findProjectByTitle("Base").isEmpty());
        }

        @Test
        void copyDoesNotShareProjects() {
            TaskGraph copy = graph.copy();

            copy.createProject("Base");

            assertEquals(List.of(GENERAL), projectIds());
        }
    }

    @Nested
    class Tasks {

        @Test
        void tasksGoToGeneralByDefault() {
            assertEquals(GENERAL, graph.create("Madera", ICON).project());
        }

        @Test
        void createsTaskInProject() {
            Project base = graph.createProject("Base");
            Task wall = graph.create("Muralla", ICON, null, base.id());
            graph.create("Madera", ICON);

            assertEquals(base.id(), wall.project());
            assertEquals(List.of(wall), graph.tasksIn(base.id()));
        }

        @Test
        void creatingInUnknownProjectIsRejectedAndChangesNothing() {
            assertRejected(Reason.PROJECT_NOT_FOUND, () -> graph.create("Muralla", ICON, null, "ghost"));
            assertTrue(graph.isEmpty());
        }

        @Test
        void dependenciesMayCrossProjects() {
            Project base = graph.createProject("Base");
            Task wood = graph.create("Madera", ICON);
            Task wall = graph.create("Muralla", ICON, null, base.id());

            assertEquals(List.of(wood.id()), graph.addDependency(wall.id(), wood.id()).dependencies());
        }

        @Test
        void movingKeepsDependenciesAndRetiresOldKey() {
            Project base = graph.createProject("Base");
            Task wood = graph.create("Madera", ICON);
            Task table = graph.create("Mesa", ICON);
            graph.addDependency(table.id(), wood.id());

            Task moved = graph.moveTask(table.id(), base.id());

            assertEquals(base.id(), moved.project());
            assertEquals(List.of(wood.id()), moved.dependencies());
            assertEquals(Set.of(new TaskKey(GENERAL, table.id())), graph.retiredKeys());
        }

        @Test
        void movingBackReusesTheOldKey() {
            Project base = graph.createProject("Base");
            Task table = graph.create("Mesa", ICON);

            graph.moveTask(table.id(), base.id());
            graph.moveTask(table.id(), GENERAL);

            assertEquals(Set.of(new TaskKey(base.id(), table.id())), graph.retiredKeys());
        }

        @Test
        void movingToItsOwnOrAnUnknownProjectIsRejected() {
            Task table = graph.create("Mesa", ICON);

            TaskGraphException exception = assertRejected(Reason.ALREADY_IN_PROJECT, () -> graph.moveTask(table.id(), GENERAL));
            assertEquals(List.of(GENERAL), exception.relatedIds());
            assertRejected(Reason.PROJECT_NOT_FOUND, () -> graph.moveTask(table.id(), "ghost"));
            assertTrue(graph.retiredKeys().isEmpty());
        }

        @Test
        void removingTaskRetiresItsKeyInItsProject() {
            Project base = graph.createProject("Base");
            Task wall = graph.create("Muralla", ICON, null, base.id());

            graph.remove(wall.id());

            assertEquals(Set.of(new TaskKey(base.id(), wall.id())), graph.retiredKeys());
            assertEquals(Set.of(wall.id()), graph.removedIds());
        }
    }

    @Nested
    class Load {

        private static Task task(String id, String project) {
            return new Task(id, "Title " + id, "", ICON, List.of(), null, false, null, project);
        }

        @Test
        void addsGeneralFirstWhenMissing() {
            Project base = new Project("base", "Base", true);

            TaskGraph loaded = TaskGraph.of(List.of(base), List.of(task("a", "base")), List.of(), List.of());

            assertEquals(List.of(Project.general(), base), loaded.projects());
        }

        @Test
        void keepsStoredGeneralAndPutsItFirst() {
            Project general = new Project(GENERAL, "Todo", false);
            Project base = new Project("base", "Base", false);

            TaskGraph loaded = TaskGraph.of(List.of(base, general), List.of(), List.of(), List.of());

            assertEquals(List.of(general, base), loaded.projects());
        }

        @Test
        void taskInUnknownProjectIsRejected() {
            assertRejected(Reason.PROJECT_NOT_FOUND,
                    () -> TaskGraph.of(List.of(), List.of(task("a", "ghost")), List.of(), List.of()));
        }

        @Test
        void repeatedProjectIdOrTitleIsRejected() {
            assertRejected(Reason.DUPLICATE_ID, () -> TaskGraph.of(
                    List.of(new Project("base", "Base", false), new Project("base", "Otra", false)), List.of(), List.of(), List.of()));
            assertRejected(Reason.DUPLICATE_PROJECT_TITLE, () -> TaskGraph.of(
                    List.of(new Project("base", "Base", false), new Project("otra", "BASE", false)), List.of(), List.of(), List.of()));
        }

        @Test
        void invalidProjectIdIsRejected() {
            assertRejected(Reason.INVALID_ID,
                    () -> TaskGraph.of(List.of(new Project("Base!", "Base", false)), List.of(), List.of(), List.of()));
        }

        @Test
        void dropsRetiredKeysStillUsedByATask() {
            Project base = new Project("base", "Base", false);
            TaskKey current = new TaskKey("base", "a");
            TaskKey retired = new TaskKey(GENERAL, "a");

            TaskGraph loaded = TaskGraph.of(List.of(base), List.of(task("a", "base")), List.of(), List.of(current, retired));

            assertEquals(Set.of(retired), loaded.retiredKeys());
        }

        @Test
        void legacyDataPutsTasksInGeneralAndRetiresRemovedIdsThere() {
            TaskGraph loaded = TaskGraph.of(List.of(new Task("a", "A", "", ICON, List.of(), null, false)), List.of("old"));

            assertEquals(GENERAL, loaded.get("a").orElseThrow().project());
            assertEquals(Set.of(new TaskKey(GENERAL, "old")), loaded.retiredKeys());
            assertEquals(Set.of("old"), loaded.removedIds());
        }
    }
}
