package dev.taskgraph.action;

import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.graph.TaskPermissions.Actor;
import dev.taskgraph.service.TaskService;
import dev.taskgraph.storage.JsonTaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the messages the commands showed before the refactor, so moving them here changed no behavior.
 */
class TaskActionsTest {

    private static final String ICON = "minecraft:stone";

    private static final Actor ALEX = Actor.player(UUID.randomUUID(), "Alex", false);
    private static final Actor STEVE = Actor.player(UUID.randomUUID(), "Steve", false);
    private static final Actor ADMIN = Actor.player(UUID.randomUUID(), "Admin", true);

    @TempDir
    Path directory;

    private final List<String> announcements = new ArrayList<>();
    private TaskService service;
    private TaskActions actions;

    @BeforeEach
    void setUp() {
        actions = actionsWith(new JsonTaskStore(directory.resolve("tasks.json")));
    }

    private TaskActions actionsWith(JsonTaskStore store) {
        service = new TaskService(store, new TaskGraph(), graph -> { });
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        return new TaskActions(service, announcements::add, quiet);
    }

    private Task create(Actor actor, String title) {
        ActionResult result = actions.create(actor, title, ICON, false);
        assertTrue(result.success(), result.message());
        return result.task();
    }

    private static void assertFailure(String message, ActionResult result) {
        assertFalse(result.success());
        assertEquals(message, result.message());
    }

    private static void assertSuccess(String message, ActionResult result) {
        assertTrue(result.success(), result.message());
        assertEquals(message, result.message());
    }

    @Nested
    class Create {

        @Test
        void reportsCreationAndRecordsCreator() {
            ActionResult result = actions.create(ALEX, "Mesa", ICON, false);

            assertSuccess("Tarea \"Mesa\" creada.", result);
            assertTrue(result.details().isEmpty());
            assertEquals("Alex", result.task().creator().name());
        }

        @Test
        void mentionsFallbackIcon() {
            ActionResult result = actions.create(ALEX, "Mesa", "minecraft:paper", true);

            assertEquals(List.of(TaskActions.FALLBACK_ICON_NOTE), result.details());
        }

        @Test
        void consoleCreatesWithoutCreator() {
            assertTrue(actions.create(Actor.console(), "Mesa", ICON, false).task().creatorOptional().isEmpty());
        }

        @Test
        void duplicateTitleIsRejected() {
            create(ALEX, "Jardín");

            assertFailure("Ya existe una tarea llamada \"Jardín\".", actions.create(STEVE, "jardin", ICON, false));
        }
    }

    @Nested
    class Edit {

        @Test
        void renameReportsOldAndNewTitle() {
            Task task = create(ALEX, "Mesa");

            assertSuccess("\"Mesa\" ahora se llama \"Mesa de trabajo\".", actions.rename(STEVE, task.id(), "Mesa de trabajo"));
        }

        @Test
        void describeReportsTask() {
            Task task = create(ALEX, "Mesa");

            assertSuccess("Descripción de \"Mesa\" actualizada.", actions.describe(STEVE, task.id(), "De roble"));
            assertEquals("De roble", service.task(task.id()).orElseThrow().description());
        }

        @Test
        void changeIconReportsItWithoutTheMinecraftNamespace() {
            Task task = create(ALEX, "Mesa");

            assertSuccess("El ícono de \"Mesa\" ahora es oak_log.", actions.changeIcon(STEVE, task.id(), "minecraft:oak_log"));
            assertEquals("minecraft:oak_log", service.task(task.id()).orElseThrow().icon());
        }

        @Test
        void anyoneChangesTheSize() {
            Task task = create(ALEX, "Mesa");

            assertSuccess("\"Mesa\" ahora es grande.", actions.changeSize(STEVE, task.id(), TaskSize.LARGE));
            assertEquals(TaskSize.LARGE, service.task(task.id()).orElseThrow().size());
            assertSuccess("\"Mesa\" ya era grande.", actions.changeSize(STEVE, task.id(), TaskSize.LARGE));
        }

        @Test
        void unknownSizeExplainsWhatToType() {
            assertEquals("No hay ningún tamaño \"enorme\". Usá chica, mediana o grande.", TaskActions.unknownSize(" enorme "));
        }

        @Test
        void unknownIconExplainsWhatToType() {
            assertEquals("No hay ningún ítem \"diamante\" que sirva de ícono. Usá su id, por ejemplo diamond o minecraft:oak_log.",
                    TaskActions.unknownIcon(" diamante "));
        }
    }

    @Nested
    class Dependencies {

        @Test
        void addingDependencyReportsIt() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");
            Task stone = create(ALEX, "Piedra");

            assertSuccess("\"Mesa\" ahora depende de \"Madera\".", actions.addDependency(ALEX, table.id(), wood.id()));
            assertSuccess("\"Mesa\" ahora depende de \"Piedra\".",
                    actions.addDependency(ALEX, table.id(), stone.id()));
        }

        @Test
        void removingOneOfSeveralDependenciesHasNoDetails() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");
            Task stone = create(ALEX, "Piedra");
            actions.addDependency(ALEX, table.id(), wood.id());
            actions.addDependency(ALEX, table.id(), stone.id());

            ActionResult result = actions.removeDependency(ALEX, table.id(), stone.id());

            assertSuccess("\"Mesa\" ya no depende de \"Piedra\".", result);
            assertTrue(result.details().isEmpty());
            assertEquals(List.of(wood.id()), result.task().dependencies());
        }

        @Test
        void removingLastDependencyHangsFromRoot() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");
            actions.addDependency(ALEX, table.id(), wood.id());

            ActionResult result = actions.removeDependency(ALEX, table.id(), wood.id());

            assertEquals(List.of("En la pestaña ahora cuelga directamente del raíz."), result.details());
        }

        @Test
        void removingSomethingThatIsNotADependencyIsRejected() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");

            assertFailure("\"Mesa\" no depende de \"Madera\".", actions.removeDependency(ALEX, table.id(), wood.id()));
        }

        @Test
        void cycleIsRejectedWithTitles() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");
            actions.addDependency(ALEX, table.id(), wood.id());

            assertFailure("Esa dependencia crea un ciclo: \"Madera\" → \"Mesa\" → \"Madera\".",
                    actions.addDependency(ALEX, wood.id(), table.id()));
        }
    }

    @Nested
    class Assignment {

        @Test
        void consoleCannotAssignToItself() {
            Task task = create(ALEX, "Mesa");

            assertFailure("Solo un jugador puede asignarse una tarea.", actions.assignToSelf(Actor.console(), task.id()));
        }

        @Test
        void freeTaskIsAssigned() {
            Task task = create(ALEX, "Mesa");

            ActionResult result = actions.assignToSelf(STEVE, task.id());

            assertSuccess("Te asignaste la tarea \"Mesa\".", result);
            assertTrue(result.details().isEmpty());
            assertEquals("Steve", result.task().assignee().name());
        }

        @Test
        void taskOfSomeoneElseIsRejected() {
            Task task = create(ALEX, "Mesa");
            actions.assignToSelf(STEVE, task.id());

            assertFailure("\"Mesa\" ya está asignada a Steve: tiene que soltarla o un admin la reasigna.",
                    actions.assignToSelf(ALEX, task.id()));
        }

        @Test
        void adminReassignmentReportsPreviousAssignee() {
            Task task = create(ALEX, "Mesa");
            actions.assignToSelf(STEVE, task.id());

            ActionResult result = actions.assignToSelf(ADMIN, task.id());

            assertSuccess("Te asignaste la tarea \"Mesa\".", result);
            assertEquals(List.of("Antes estaba asignada a Steve."), result.details());
        }

        @Test
        void assigneeReleasesOwnTask() {
            Task task = create(ALEX, "Mesa");
            actions.assignToSelf(STEVE, task.id());

            assertSuccess("Soltaste la tarea \"Mesa\".", actions.release(STEVE, task.id()));
        }

        @Test
        void adminReleasesTaskOfSomeoneElse() {
            Task task = create(ALEX, "Mesa");
            actions.assignToSelf(STEVE, task.id());

            assertSuccess("\"Mesa\" ya no está asignada a Steve.", actions.release(ADMIN, task.id()));
        }

        @Test
        void otherPlayerCannotRelease() {
            Task task = create(ALEX, "Mesa");
            actions.assignToSelf(STEVE, task.id());

            assertFailure("Solo Steve o un admin pueden soltar \"Mesa\".", actions.release(ALEX, task.id()));
        }

        @Test
        void releasingFreeTaskIsRejected() {
            Task task = create(ALEX, "Mesa");

            assertFailure("\"Mesa\" no está asignada a nadie.", actions.release(ALEX, task.id()));
        }
    }

    @Nested
    class Resolve {

        @Test
        void announcesToEveryoneInsteadOfReplying() {
            Task task = create(ALEX, "Mesa");

            ActionResult result = actions.resolve(STEVE, task.id());

            assertTrue(result.success());
            assertNull(result.message());
            assertEquals(List.of("✔ Steve resolvió la tarea \"Mesa\""), announcements);
        }

        @Test
        void unresolvedDependenciesAreRejectedWithoutAnnouncement() {
            Task table = create(ALEX, "Mesa");
            Task wood = create(ALEX, "Madera");
            actions.addDependency(ALEX, table.id(), wood.id());

            assertFailure("No se puede resolver \"Mesa\": faltan \"Madera\".", actions.resolve(ALEX, table.id()));
            assertTrue(announcements.isEmpty());
        }
    }

    @Nested
    class Remove {

        @Test
        void creatorRemoves() {
            Task task = create(ALEX, "Mesa");

            assertSuccess("Tarea \"Mesa\" borrada.", actions.remove(ALEX, task.id()));
            assertTrue(service.tasks().isEmpty());
        }

        @Test
        void otherPlayerIsToldWhoCanRemove() {
            Task task = create(ALEX, "Mesa");

            assertFailure("Solo Alex (quien la creó) o un admin pueden borrar \"Mesa\".", actions.remove(STEVE, task.id()));
        }

        @Test
        void taskWithoutCreatorOnlyByAdmin() {
            Task task = create(Actor.console(), "Mesa");

            assertFailure("Solo un admin puede borrar \"Mesa\".", actions.remove(STEVE, task.id()));
            assertTrue(actions.remove(ADMIN, task.id()).success());
        }

        @Test
        void taskWithDependentsIsRejected() {
            Task wood = create(ALEX, "Madera");
            Task table = create(ALEX, "Mesa");
            actions.addDependency(ALEX, table.id(), wood.id());

            assertFailure("No se puede borrar \"Madera\": dependen de ella \"Mesa\".", actions.remove(ALEX, wood.id()));
        }
    }

    @Nested
    class Projects {

        private Project project(String title) {
            assertTrue(actions.createProject(ALEX, title).success());
            return service.findProjectByTitle(title).orElseThrow();
        }

        @Test
        void anyoneCreatesAProject() {
            ActionResult result = actions.createProject(STEVE, "Base del norte");

            assertSuccess("Proyecto \"Base del norte\" creado.", result);
            assertEquals(List.of("Tiene su propia pestaña en la pantalla de logros."), result.details());
            assertNull(result.task());
            assertTrue(service.findProjectByTitle("base del norte").isPresent());
        }

        @Test
        void repeatedProjectTitleIsRejected() {
            project("Base");

            assertFailure("Ya existe un proyecto llamado \"Base\".", actions.createProject(STEVE, "BASE"));
        }

        @Test
        void anyoneRenamesAProject() {
            Project base = project("Base");

            assertSuccess("El proyecto \"Base\" ahora se llama \"Castillo\".", actions.renameProject(STEVE, base.id(), "Castillo"));
        }

        @Test
        void onlyAdminsRemoveProjects() {
            Project base = project("Base");

            assertFailure("Solo un admin puede borrar proyectos.", actions.removeProject(ALEX, base.id()));
            assertSuccess("Proyecto \"Base\" borrado.", actions.removeProject(ADMIN, base.id()));
            assertTrue(service.project(base.id()).isEmpty());
        }

        @Test
        void removingProjectWithTasksListsThem() {
            Project base = project("Base");
            actions.create(ALEX, "Muralla", ICON, false, base.id());

            assertFailure("No se puede borrar el proyecto \"Base\": todavía tiene tareas (\"Muralla\"). Movelas o borralas primero.",
                    actions.removeProject(ADMIN, base.id()));
        }

        @Test
        void generalCannotBeRemoved() {
            assertFailure("El proyecto \"General\" no se puede borrar.", actions.removeProject(ADMIN, Project.GENERAL_ID));
        }

        @Test
        void createsTaskInAProjectNamingIt() {
            Project base = project("Base");

            ActionResult result = actions.create(ALEX, "Muralla", ICON, false, base.id());

            assertSuccess("Tarea \"Muralla\" creada en \"Base\".", result);
            assertEquals(base.id(), result.task().project());
        }

        @Test
        void anyoneMovesATask() {
            Project base = project("Base");
            Task wall = create(ALEX, "Muralla");

            ActionResult result = actions.moveTask(STEVE, wall.id(), base.id());

            assertSuccess("\"Muralla\" ahora está en el proyecto \"Base\".", result);
            assertEquals(base.id(), result.task().project());
        }

        @Test
        void movingToItsOwnProjectIsRejected() {
            Task wall = create(ALEX, "Muralla");

            assertFailure("\"Muralla\" ya está en el proyecto \"General\".", actions.moveTask(ALEX, wall.id(), Project.GENERAL_ID));
        }

        @Test
        void staleProjectIdIsReported() {
            Project base = project("Base");
            Task wall = create(ALEX, "Muralla");
            actions.removeProject(ADMIN, base.id());

            assertFailure(TaskActions.PROJECT_GONE, actions.moveTask(ALEX, wall.id(), base.id()));
            assertFailure(TaskActions.PROJECT_GONE, actions.create(ALEX, "Torre", ICON, false, base.id()));
            assertFailure(TaskActions.PROJECT_GONE, actions.renameProject(ALEX, base.id(), "Otra"));
            assertTrue(service.findByTitle("Torre").isEmpty());
        }

        @Test
        void anyoneHidesAndShowsResolvedTasks() {
            Project base = project("Base");

            ActionResult hidden = actions.setHideResolved(STEVE, base.id(), true);
            assertSuccess("\"Base\" ahora oculta las ramas terminadas.", hidden);
            assertEquals(List.of(TaskActions.HIDE_RESOLVED_NOTE), hidden.details());
            assertTrue(service.project(base.id()).orElseThrow().hideResolved());

            assertSuccess("\"Base\" ahora muestra todas las tareas resueltas.", actions.setHideResolved(STEVE, base.id(), false));
            assertFalse(service.project(base.id()).orElseThrow().hideResolved());
        }

        @Test
        void hidingTwiceSaysItAlreadyWas() {
            actions.setHideResolved(ALEX, Project.GENERAL_ID, true);

            assertSuccess("\"General\" ya ocultaba las ramas terminadas.", actions.setHideResolved(ALEX, Project.GENERAL_ID, true));
            assertSuccess("\"Base\" ya mostraba todas las tareas resueltas.",
                    actions.setHideResolved(ALEX, project("Base").id(), false));
        }

        @Test
        void unknownProjectMessage() {
            assertEquals("No existe el proyecto \"Nada\".", TaskActions.unknownProject("Nada"));
        }
    }

    @Nested
    class Failures {

        @Test
        void staleTaskIdIsReported() {
            Task task = create(ALEX, "Mesa");
            actions.remove(ALEX, task.id());

            assertFailure("Esa tarea ya no existe.", actions.resolve(ALEX, task.id()));
        }

        @Test
        void saveFailureIsReportedAndChangesNothing() throws IOException {
            Path file = directory.resolve("broken/tasks.json");
            Files.createDirectories(file.resolve("blocker"));
            TaskActions broken = actionsWith(new JsonTaskStore(file));

            assertFailure("No se pudo guardar el cambio. Revisá el log del servidor.", broken.create(ALEX, "Mesa", ICON, false));
            assertTrue(service.tasks().isEmpty());
        }

        @Test
        void unknownTitleMessage() {
            assertEquals("No existe la tarea \"Nada\".", TaskActions.unknownTitle("Nada"));
        }
    }
}
