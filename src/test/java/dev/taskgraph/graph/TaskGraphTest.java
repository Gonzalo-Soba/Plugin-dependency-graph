package dev.taskgraph.graph;

import dev.taskgraph.graph.TaskGraphException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskGraphTest {

    private static final String ICON = "minecraft:stone";

    private TaskGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TaskGraph();
    }

    // Simple lowercase titles produce the same internal id, so tests can refer to tasks by title.
    private void createAll(String... ids) {
        for (String id : ids) {
            assertEquals(id, graph.create(id, ICON).id());
        }
    }

    private static TaskGraphException assertRejected(Reason reason, Executable action) {
        TaskGraphException exception = assertThrows(TaskGraphException.class, action);
        assertEquals(reason, exception.reason());
        return exception;
    }

    @Nested
    class Create {

        @Test
        void storesTaskWithDefaults() {
            Task task = graph.create("  Build   a farm ", "minecraft:wheat");

            assertEquals("build_a_farm", task.id());
            assertEquals("Build a farm", task.title());
            assertEquals("", task.description());
            assertEquals("minecraft:wheat", task.icon());
            assertTrue(task.dependencies().isEmpty());
            assertTrue(task.assigneeOptional().isEmpty());
            assertFalse(task.resolved());
            assertEquals(task, graph.get("build_a_farm").orElseThrow());
        }

        @ParameterizedTest
        @ValueSource(strings = {"farm", "FARM", " Farm ", "fárm"})
        void rejectsDuplicateTitleIgnoringCaseAccentsAndSpaces(String title) {
            graph.create("Farm", ICON);

            TaskGraphException exception = assertRejected(Reason.DUPLICATE_TITLE, () -> graph.create(title, ICON));
            assertEquals("farm", exception.taskId());
            assertEquals(1, graph.size());
        }

        @Test
        void rejectsBlankTitle() {
            assertRejected(Reason.INVALID_TITLE, () -> graph.create("   ", ICON));
        }

        @Test
        void derivesIdFromTitle() {
            assertEquals("jardin_de_la_nandu", graph.create("Jardín de la Ñandú", ICON).id());
            assertEquals("mesa_de_trabajo", graph.create("¡Mesa de trabajo!", ICON).id());
        }

        @Test
        void addsSuffixWhenIdIsTaken() {
            graph.create("Mesa", ICON);

            // Different titles that slug to the same id.
            assertEquals("mesa_2", graph.create("Mesa!", ICON).id());
            assertEquals("mesa_3", graph.create("¿Mesa?", ICON).id());
        }

        @Test
        void fallsBackToTareaWhenTitleHasNoIdCharacters() {
            assertEquals("tarea", graph.create("!!!", ICON).id());
            assertEquals("tarea_2", graph.create("¿?", ICON).id());
            assertEquals("tarea_3", graph.create("中文", ICON).id());
        }

        @Test
        void truncatesLongIdsKeepingTheSuffix() {
            String title = "una tarea con un titulo muy pero muy largo";
            Task first = graph.create(title, ICON);
            Task second = graph.create(title + "!", ICON);

            assertEquals(32, first.id().length());
            assertTrue(second.id().endsWith("_2"));
            assertTrue(second.id().length() <= 32);
            assertFalse(second.id().contains("__"));
        }
    }

    @Nested
    class Rename {

        @Test
        void changesTitleAndKeepsId() {
            Task task = graph.create("Mesa", ICON);

            Task renamed = graph.rename(task.id(), "  Mesa de   trabajo ");

            assertEquals("mesa", renamed.id());
            assertEquals("Mesa de trabajo", renamed.title());
            assertTrue(graph.findByTitle("mesa de trabajo").isPresent());
            assertTrue(graph.findByTitle("Mesa").isEmpty());
        }

        @Test
        void allowsChangingOnlyCaseOrAccents() {
            Task task = graph.create("jardin", ICON);

            assertEquals("Jardín", graph.rename(task.id(), "Jardín").title());
        }

        @Test
        void rejectsTitleOfAnotherTask() {
            createAll("a", "b");

            assertRejected(Reason.DUPLICATE_TITLE, () -> graph.rename("a", "B"));
            assertEquals("a", graph.get("a").orElseThrow().title());
        }

        @Test
        void rejectsBlankTitle() {
            createAll("a");

            assertRejected(Reason.INVALID_TITLE, () -> graph.rename("a", " "));
        }
    }

    @Nested
    class Icon {

        @Test
        void changesOnlyTheIcon() {
            createAll("a", "b");
            graph.addDependency("b", "a");
            graph.resolve("a");
            Task before = graph.get("b").orElseThrow();

            Task changed = graph.setIcon("b", "minecraft:diamond");

            assertEquals(new Task(before.id(), before.title(), before.description(), "minecraft:diamond",
                    before.dependencies(), before.assignee(), before.resolved(), before.creator()), changed);
            assertEquals(changed, graph.get("b").orElseThrow());
        }

        @Test
        void rejectsUnknownTask() {
            assertRejected(Reason.NOT_FOUND, () -> graph.setIcon("missing", "minecraft:diamond"));
        }
    }

    @Nested
    class FindByTitle {

        @Test
        void ignoresCaseAccentsAndSpaces() {
            Task task = graph.create("Jardín de la Ñandú", ICON);

            assertEquals(task, graph.findByTitle("  jardin  de LA nandu").orElseThrow());
            assertTrue(graph.findByTitle("jardin").isEmpty());
        }
    }

    @Nested
    class Unlocking {

        @Test
        void resolvingTheLastPendingDependencyUnlocksTheTask() {
            createAll("a", "b", "c");
            graph.addDependency("c", "a");
            graph.addDependency("c", "b");
            graph.resolve("a");
            TaskGraph before = graph.copy();

            graph.resolve("b");

            assertEquals(List.of("c"), graph.unlockedSince(before).stream().map(Task::id).toList());
        }

        @Test
        void taskWithAnotherPendingDependencyStaysBlocked() {
            createAll("a", "b", "c");
            graph.addDependency("c", "a");
            graph.addDependency("c", "b");
            TaskGraph before = graph.copy();

            graph.resolve("a");

            assertTrue(graph.unlockedSince(before).isEmpty());
        }

        @Test
        void removingTheBlockingDependencyUnlocks() {
            createAll("a", "b");
            graph.addDependency("b", "a");
            TaskGraph before = graph.copy();

            graph.removeDependency("b", "a");

            assertEquals(List.of("b"), graph.unlockedSince(before).stream().map(Task::id).toList());
        }

        @Test
        void newTasksAndResolvedOnesAreNotUnlocked() {
            createAll("a");
            TaskGraph before = graph.copy();

            graph.create("b", ICON);
            graph.resolve("a");

            assertTrue(graph.unlockedSince(before).isEmpty());
        }

        @Test
        void severalAreListedInCreationOrder() {
            createAll("a", "c", "b");
            graph.addDependency("b", "a");
            graph.addDependency("c", "a");
            TaskGraph before = graph.copy();

            graph.resolve("a");

            assertEquals(List.of("c", "b"), graph.unlockedSince(before).stream().map(Task::id).toList());
        }
    }

    @Nested
    class Sizes {

        @Test
        void newTasksAreSmall() {
            assertEquals(TaskSize.SMALL, graph.create("a", ICON).size());
        }

        @Test
        void sizeChangesAndKeepsTheRest() {
            createAll("a", "b");
            graph.addDependency("b", "a");
            graph.resolve("a");

            Task large = graph.setSize("b", TaskSize.LARGE);

            assertEquals(TaskSize.LARGE, large.size());
            assertEquals(List.of("a"), large.dependencies());
            assertEquals(TaskSize.LARGE, graph.get("b").orElseThrow().size());
        }

        @Test
        void missingSizeIsSmall() {
            assertEquals(TaskSize.SMALL, new Task("a", "A", "", ICON, List.of(), null, false, null, null, null).size());
        }

        @Test
        void unknownTaskIsRejected() {
            assertRejected(Reason.NOT_FOUND, () -> graph.setSize("ghost", TaskSize.MEDIUM));
        }
    }

    @Nested
    class Dependencies {

        @Test
        void firstDependencyIsVisualParent() {
            createAll("a", "b", "c");
            graph.addDependency("c", "b");
            Task task = graph.addDependency("c", "a");

            assertEquals(List.of("b", "a"), task.dependencies());
            assertEquals("b", task.visualParent(Set.of("a", "b", "c")).orElseThrow());
            assertEquals(List.of("a"), task.extraRequirements(Set.of("a", "b", "c")));
        }

        @Test
        void visualParentIsTheFirstDrawnDependency() {
            createAll("a", "b", "c");
            graph.addDependency("c", "b");
            Task task = graph.addDependency("c", "a");

            assertEquals("a", task.visualParent(Set.of("a", "c")).orElseThrow());
            assertEquals(List.of("b"), task.extraRequirements(Set.of("a", "c")));
        }

        @Test
        void withoutDrawnDependenciesEveryDependencyIsListed() {
            createAll("a", "b", "c");
            graph.addDependency("c", "b");
            Task task = graph.addDependency("c", "a");

            assertTrue(task.visualParent(Set.of("c")).isEmpty());
            assertEquals(List.of("b", "a"), task.extraRequirements(Set.of("c")));
        }

        @Test
        void everyDrawnDependencyGetsALineInDependencyOrder() {
            createAll("a", "b", "c", "d");
            graph.addDependency("d", "c");
            graph.addDependency("d", "a");
            Task task = graph.addDependency("d", "b");

            assertEquals(List.of("c", "a", "b"), task.lineParents(Set.of("a", "b", "c", "d")));
        }

        @Test
        void dependenciesThatAreNotDrawnGetNoLine() {
            createAll("a", "b", "c");
            graph.addDependency("c", "a");
            Task task = graph.addDependency("c", "b");

            assertEquals(List.of("b"), task.lineParents(Set.of("b", "c")));
            assertTrue(task.lineParents(Set.of("c")).isEmpty());
        }

        @Test
        void taskWithoutDependenciesHasNoVisualParent() {
            createAll("a");

            Task task = graph.get("a").orElseThrow();
            assertTrue(task.visualParent(Set.of("a")).isEmpty());
            assertTrue(task.extraRequirements(Set.of("a")).isEmpty());
        }

        @Test
        void rejectsSelfDependency() {
            createAll("a");

            assertRejected(Reason.SELF_DEPENDENCY, () -> graph.addDependency("a", "a"));
        }

        @Test
        void rejectsTwoNodeCycle() {
            createAll("a", "b");
            graph.addDependency("a", "b");

            TaskGraphException exception = assertRejected(Reason.CYCLE, () -> graph.addDependency("b", "a"));
            assertEquals(List.of("b", "a", "b"), exception.relatedIds());
            assertTrue(graph.get("b").orElseThrow().dependencies().isEmpty());
        }

        @Test
        void rejectsLongCycle() {
            createAll("a", "b", "c", "d");
            graph.addDependency("a", "b");
            graph.addDependency("b", "c");
            graph.addDependency("c", "d");

            TaskGraphException exception = assertRejected(Reason.CYCLE, () -> graph.addDependency("d", "a"));
            assertEquals(List.of("d", "a", "b", "c", "d"), exception.relatedIds());
            assertTrue(graph.get("d").orElseThrow().dependencies().isEmpty());
        }

        @Test
        void allowsDiamondShape() {
            createAll("base", "left", "right", "top");
            graph.addDependency("left", "base");
            graph.addDependency("right", "base");
            graph.addDependency("top", "left");

            Task top = graph.addDependency("top", "right");
            assertEquals(List.of("left", "right"), top.dependencies());
        }

        @Test
        void removeDependencyKeepsOrderOfTheRest() {
            createAll("a", "b", "c", "d");
            graph.addDependency("d", "a");
            graph.addDependency("d", "b");
            graph.addDependency("d", "c");

            Task task = graph.removeDependency("d", "a");

            assertEquals(List.of("b", "c"), task.dependencies());
            assertEquals("b", task.visualParent(Set.of("a", "b", "c", "d")).orElseThrow());
        }

        @Test
        void removeLastDependencyLeavesNoParent() {
            createAll("a", "b");
            graph.addDependency("b", "a");

            assertTrue(graph.removeDependency("b", "a").visualParent(Set.of("a", "b")).isEmpty());
            assertTrue(graph.dependents("a").isEmpty());
        }

        @Test
        void removeDependencyRejectsNonDependency() {
            createAll("a", "b");

            TaskGraphException exception = assertRejected(Reason.NOT_A_DEPENDENCY, () -> graph.removeDependency("b", "a"));
            assertEquals(List.of("a"), exception.relatedIds());
        }

        @Test
        void removeDependencyAllowsResolvedTask() {
            createAll("a", "b");
            graph.addDependency("b", "a");
            graph.resolve("a");
            graph.resolve("b");

            assertTrue(graph.removeDependency("b", "a").resolved());
        }

        @Test
        void rejectsDuplicateDependency() {
            createAll("a", "b");
            graph.addDependency("a", "b");

            assertRejected(Reason.DUPLICATE_DEPENDENCY, () -> graph.addDependency("a", "b"));
        }

        @Test
        void rejectsMissingTasks() {
            createAll("a");

            assertRejected(Reason.NOT_FOUND, () -> graph.addDependency("a", "missing"));
            assertRejected(Reason.NOT_FOUND, () -> graph.addDependency("missing", "a"));
        }

        @Test
        void rejectsUnresolvedDependencyOnResolvedTask() {
            createAll("a", "b");
            graph.resolve("a");

            assertRejected(Reason.RESOLVED_TASK_DEPENDENCY, () -> graph.addDependency("a", "b"));
            assertTrue(graph.get("a").orElseThrow().dependencies().isEmpty());
        }

        @Test
        void allowsResolvedDependencyOnResolvedTask() {
            createAll("a", "b");
            graph.resolve("a");
            graph.resolve("b");

            assertEquals(List.of("b"), graph.addDependency("a", "b").dependencies());
        }
    }

    @Nested
    class Resolve {

        @Test
        void rejectsWhenDependenciesAreUnresolved() {
            createAll("a", "b", "c");
            graph.addDependency("c", "a");
            graph.addDependency("c", "b");
            graph.resolve("a");

            TaskGraphException exception = assertRejected(Reason.UNRESOLVED_DEPENDENCIES, () -> graph.resolve("c"));
            assertEquals(List.of("b"), exception.relatedIds());
            assertFalse(graph.get("c").orElseThrow().resolved());
        }

        @Test
        void succeedsWhenAllDependenciesAreResolved() {
            createAll("a", "b");
            graph.addDependency("b", "a");
            graph.resolve("a");

            assertTrue(graph.resolve("b").resolved());
            assertEquals(Set.of("a", "b"), graph.resolvedIds());
        }

        @Test
        void rejectsAlreadyResolved() {
            createAll("a");
            graph.resolve("a");

            assertRejected(Reason.ALREADY_RESOLVED, () -> graph.resolve("a"));
        }
    }

    @Nested
    class Remove {

        @Test
        void rejectsWhenOtherTasksDependOnIt() {
            createAll("a", "b", "c");
            graph.addDependency("b", "a");
            graph.addDependency("c", "a");

            TaskGraphException exception = assertRejected(Reason.HAS_DEPENDENTS, () -> graph.remove("a"));
            assertEquals(List.of("b", "c"), exception.relatedIds());
            assertTrue(graph.contains("a"));
        }

        @Test
        void removesLeafTask() {
            createAll("a", "b");
            graph.addDependency("b", "a");

            graph.remove("b");

            assertFalse(graph.contains("b"));
            assertTrue(graph.dependents("a").isEmpty());
        }

        @Test
        void rejectsMissingTask() {
            assertRejected(Reason.NOT_FOUND, () -> graph.remove("missing"));
        }

        @Test
        void remembersRemovedIdsAndNeverReusesThem() {
            createAll("a", "b");
            graph.remove("b");
            graph.remove("a");

            assertEquals(List.of("b", "a"), List.copyOf(graph.removedIds()));

            assertEquals("a_2", graph.create("a", ICON).id());
            assertEquals(List.of("b", "a"), List.copyOf(graph.removedIds()));
        }

        @Test
        void rejectedRemovalDoesNotRecordId() {
            createAll("a", "b");
            graph.addDependency("b", "a");

            assertThrows(TaskGraphException.class, () -> graph.remove("a"));
            assertTrue(graph.removedIds().isEmpty());
        }
    }

    @Nested
    class Copy {

        @Test
        void changesToCopyDoNotAffectOriginal() {
            createAll("a", "b");
            graph.remove("b");
            TaskGraph copy = graph.copy();

            copy.create("c", ICON);
            copy.resolve("a");
            copy.remove("c");

            assertEquals(List.of("a"), graph.tasks().stream().map(Task::id).toList());
            assertFalse(graph.get("a").orElseThrow().resolved());
            assertEquals(Set.of("b"), graph.removedIds());
            assertEquals(Set.of("b", "c"), copy.removedIds());
        }
    }

    @Nested
    class Edit {

        @Test
        void setsDescription() {
            createAll("a");

            assertEquals("Needs lots of wood", graph.setDescription("a", " Needs lots of wood ").description());
        }

        @Test
        void assignRejectsTaskAssignedToSomeoneElse() {
            createAll("a");
            PlayerRef first = new PlayerRef(UUID.randomUUID(), "Alex");
            PlayerRef second = new PlayerRef(UUID.randomUUID(), "Steve");
            graph.assign("a", first);

            assertRejected(Reason.ALREADY_ASSIGNED, () -> graph.assign("a", second));
            assertEquals(first, graph.get("a").orElseThrow().assignee());
        }

        @Test
        void assignAgainToSamePlayerIsAllowed() {
            createAll("a");
            PlayerRef alex = new PlayerRef(UUID.randomUUID(), "Alex");
            graph.assign("a", alex);

            // Same UUID with a new name, e.g. after a name change.
            PlayerRef renamed = new PlayerRef(alex.uuid(), "Alex2");
            assertEquals(renamed, graph.assign("a", renamed).assignee());
        }

        @Test
        void adminReassignmentReplacesAssignee() {
            createAll("a");
            PlayerRef first = new PlayerRef(UUID.randomUUID(), "Alex");
            PlayerRef second = new PlayerRef(UUID.randomUUID(), "Steve");
            graph.assign("a", first);

            assertEquals(second, graph.assign("a", second, true).assignee());
        }

        @Test
        void unassignClearsAssignee() {
            createAll("a");
            graph.assign("a", new PlayerRef(UUID.randomUUID(), "Alex"));

            assertTrue(graph.unassign("a").assigneeOptional().isEmpty());
        }

        @Test
        void unassignRejectsFreeTask() {
            createAll("a");

            assertRejected(Reason.NOT_ASSIGNED, () -> graph.unassign("a"));
        }

        @Test
        void createRecordsCreator() {
            PlayerRef alex = new PlayerRef(UUID.randomUUID(), "Alex");

            assertEquals(alex, graph.create("Mesa", ICON, alex).creator());
            assertTrue(graph.create("Silla", ICON).creatorOptional().isEmpty());
        }
    }

    @Nested
    class TopologicalOrder {

        @Test
        void placesDependenciesFirstAndKeepsCreationOrderOnTies() {
            createAll("top", "left", "right", "base");
            graph.addDependency("top", "left");
            graph.addDependency("top", "right");
            graph.addDependency("left", "base");
            graph.addDependency("right", "base");

            List<String> order = graph.topologicalOrder().stream().map(Task::id).toList();

            assertEquals(List.of("base", "left", "right", "top"), order);
        }

        @Test
        void isStableAcrossCalls() {
            createAll("c", "b", "a");
            graph.addDependency("a", "c");

            assertEquals(graph.topologicalOrder(), graph.topologicalOrder());
            assertEquals(List.of("c", "b", "a"), graph.topologicalOrder().stream().map(Task::id).toList());
        }
    }

    @Nested
    class Status {

        @Test
        void reflectsResolutionAndDependencies() {
            createAll("a", "b", "c");
            graph.addDependency("b", "a");
            graph.resolve("c");

            assertEquals(TaskStatus.AVAILABLE, graph.status("a"));
            assertEquals(TaskStatus.BLOCKED, graph.status("b"));
            assertEquals(TaskStatus.RESOLVED, graph.status("c"));

            graph.resolve("a");
            assertEquals(TaskStatus.AVAILABLE, graph.status("b"));
        }
    }

    @Nested
    class ListOrder {

        @Test
        void availableThenBlockedThenResolvedAlphabeticalInside() {
            for (String title : List.of("Zanahoria", "Árbol", "Mesa", "banco", "Puerta", "Cama")) {
                graph.create(title, ICON);
            }
            String mesa = graph.findByTitle("Mesa").orElseThrow().id();
            String puerta = graph.findByTitle("Puerta").orElseThrow().id();
            String arbol = graph.findByTitle("Árbol").orElseThrow().id();
            String cama = graph.findByTitle("Cama").orElseThrow().id();
            graph.addDependency(mesa, arbol);
            graph.addDependency(puerta, mesa);
            graph.resolve(cama);

            List<String> titles = graph.listOrder().stream().map(Task::title).toList();

            // Available (accents and case ignored when sorting), then blocked, then resolved.
            assertEquals(List.of("Árbol", "banco", "Zanahoria", "Mesa", "Puerta", "Cama"), titles);
        }

        @Test
        void handlesThirtyTasks() {
            for (int i = 1; i <= 30; i++) {
                graph.create("Tarea %02d".formatted(i), ICON);
            }
            for (int i = 2; i <= 30; i += 3) {
                graph.addDependency("tarea_%02d".formatted(i), "tarea_%02d".formatted(i - 1));
            }
            // Tasks 1, 7, 13... have no dependencies; resolving them leaves 2, 8, 14... available and 5, 11, 17... blocked.
            for (int i = 1; i <= 30; i += 6) {
                graph.resolve("tarea_%02d".formatted(i));
            }

            List<Task> order = graph.listOrder();

            assertEquals(30, order.size());
            assertEquals(Set.of(TaskStatus.values()),
                    order.stream().map(task -> graph.status(task.id())).collect(Collectors.toSet()));
            List<TaskStatus> statuses = order.stream().map(task -> graph.status(task.id())).toList();
            List<TaskStatus> sorted = statuses.stream().sorted().toList();
            assertEquals(sorted, statuses);
        }
    }

    @Nested
    class DependencyCandidates {

        @Test
        void excludesSelfExistingDependenciesAndCycles() {
            createAll("a", "b", "c", "d", "e");
            graph.addDependency("b", "a");
            graph.addDependency("c", "b");
            graph.addDependency("b", "d");

            // b already depends on a and d; c depends on b, so b depending on c would close a cycle.
            assertEquals(List.of("e"), graph.dependencyCandidates("b").stream().map(Task::id).toList());
            // b and c depend on a, so a can only depend on d or e.
            assertEquals(List.of("d", "e"), graph.dependencyCandidates("a").stream().map(Task::id).toList());
        }

        @Test
        void excludesTransitiveDependents() {
            createAll("a", "b", "c", "d");
            graph.addDependency("b", "a");
            graph.addDependency("c", "b");

            assertEquals(List.of("d"), graph.dependencyCandidates("a").stream().map(Task::id).toList());
        }

        @Test
        void everyCandidateCanBeAdded() {
            createAll("a", "b", "c", "d", "e");
            graph.addDependency("c", "a");
            graph.addDependency("d", "c");
            graph.resolve("b");

            for (Task candidate : graph.dependencyCandidates("c")) {
                TaskGraph copy = graph.copy();
                copy.addDependency("c", candidate.id());
            }
        }

        @Test
        void resolvedTaskOnlyGetsResolvedCandidates() {
            createAll("a", "b", "c");
            graph.resolve("a");
            graph.resolve("b");

            assertEquals(List.of("b"), graph.dependencyCandidates("a").stream().map(Task::id).toList());
        }

        @Test
        void sortedByTitle() {
            graph.create("Zanahoria", ICON);
            Task target = graph.create("Mesa", ICON);
            graph.create("árbol", ICON);

            assertEquals(List.of("árbol", "Zanahoria"),
                    graph.dependencyCandidates(target.id()).stream().map(Task::title).toList());
        }
    }

    @Nested
    class Load {

        private static Task task(String id, boolean resolved, String... dependencies) {
            return new Task(id, "Title " + id, "", ICON, List.of(dependencies), null, resolved);
        }

        @Test
        void rebuildsValidGraph() {
            TaskGraph loaded = TaskGraph.of(List.of(task("a", true), task("b", false, "a")));

            assertEquals(List.of("a", "b"), loaded.tasks().stream().map(Task::id).toList());
            assertEquals(List.of("b"), loaded.dependents("a"));
        }

        @Test
        void keepsRemovedIdsThatAreNotExistingTasks() {
            TaskGraph loaded = TaskGraph.of(List.of(task("a", false)), List.of("old", "a"));

            assertEquals(Set.of("old"), loaded.removedIds());
        }

        @Test
        void rejectsCycle() {
            assertRejected(Reason.CYCLE, () -> TaskGraph.of(List.of(task("a", false, "b"), task("b", false, "a"))));
        }

        @Test
        void rejectsUnknownDependency() {
            assertRejected(Reason.NOT_FOUND, () -> TaskGraph.of(List.of(task("a", false, "ghost"))));
        }

        @Test
        void rejectsDuplicateIds() {
            assertRejected(Reason.DUPLICATE_ID, () -> TaskGraph.of(List.of(task("a", false), task("a", false))));
        }

        @Test
        void disambiguatesRepeatedTitlesFromOlderData() {
            Task first = new Task("4", "Mesa", "", ICON, List.of(), null, false);
            Task second = new Task("mesa", "mesa", "", ICON, List.of(), null, false);

            TaskGraph loaded = TaskGraph.of(List.of(first, second));

            assertEquals("Mesa", loaded.get("4").orElseThrow().title());
            assertEquals("mesa (mesa)", loaded.get("mesa").orElseThrow().title());
            assertEquals("mesa", loaded.findByTitle("mesa (mesa)").orElseThrow().id());
        }

        @Test
        void rejectsResolvedTaskWithUnresolvedDependency() {
            assertRejected(Reason.RESOLVED_TASK_DEPENDENCY,
                    () -> TaskGraph.of(List.of(task("a", false), task("b", true, "a"))));
        }
    }
}
