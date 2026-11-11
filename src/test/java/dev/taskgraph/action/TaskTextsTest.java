package dev.taskgraph.action;

import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.graph.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTextsTest {

    @Test
    void taskListMarksResolvedTasks() {
        TaskGraph graph = new TaskGraph();
        Task wood = graph.create("Madera", "minecraft:oak_log");
        Task stone = graph.create("Piedra", "minecraft:cobblestone");
        graph.resolve(wood.id());

        assertEquals("✔ Madera, ✘ Piedra", TaskTexts.taskList(graph, List.of(wood.id(), stone.id())));
    }

    @Test
    void resolutionNamesWhoAndTheDateInTheGivenZone() {
        TaskGraph graph = new TaskGraph();
        Task wood = graph.create("Madera", "minecraft:oak_log");
        // 01:30 UTC on the 4th is still the 3rd in Buenos Aires.
        Task resolved = graph.resolve(wood.id(), new PlayerRef(UUID.randomUUID(), "Steve"), Instant.parse("2026-11-04T01:30:00Z"));

        assertEquals(Optional.of("Resuelta por Steve el 03/11/2026"),
                TaskTexts.resolution(resolved, ZoneId.of("America/Argentina/Buenos_Aires")));
    }

    @Test
    void resolutionLeavesOutWhatWasNotRecorded() {
        TaskGraph graph = new TaskGraph();
        Task console = graph.resolve(graph.create("Madera", "minecraft:oak_log").id(), null, Instant.parse("2026-11-03T12:00:00Z"));
        Task old = graph.resolve(graph.create("Piedra", "minecraft:cobblestone").id());
        Task pending = graph.create("Horno", "minecraft:furnace");

        assertEquals(Optional.of("Resuelta el 03/11/2026"), TaskTexts.resolution(console, ZoneId.of("UTC")));
        assertEquals(Optional.empty(), TaskTexts.resolution(old, ZoneId.of("UTC")));
        assertEquals(Optional.empty(), TaskTexts.resolution(pending, ZoneId.of("UTC")));
    }

    @Test
    void emptyTaskListSaysNothing() {
        assertEquals("nada", TaskTexts.taskList(new TaskGraph(), List.of()));
    }

    @Test
    void alsoRequiresListsDependenciesAfterTheVisualParentAndNamesOtherProjects() {
        TaskGraph graph = new TaskGraph();
        Project base = graph.createProject("Base");
        Task wood = graph.create("Madera", "minecraft:oak_log");
        Task stone = graph.create("Piedra", "minecraft:cobblestone");
        Task wall = graph.create("Muralla", "minecraft:stone_bricks", null, base.id());
        Task gate = graph.create("Portón", "minecraft:oak_door", null, base.id());
        graph.addDependency(gate.id(), wood.id());
        graph.addDependency(gate.id(), wall.id());
        Task updated = graph.addDependency(gate.id(), stone.id());
        Set<String> drawnInBase = Set.of(wall.id(), gate.id());

        assertEquals("También requiere: Madera (en General), Piedra (en General)",
                TaskTexts.alsoRequires(graph, updated, drawnInBase).orElseThrow());
    }

    @Test
    void alsoRequiresIsEmptyWithOnlyTheVisualParent() {
        TaskGraph graph = new TaskGraph();
        Task wood = graph.create("Madera", "minecraft:oak_log");
        Task table = graph.create("Mesa", "minecraft:crafting_table");
        Task updated = graph.addDependency(table.id(), wood.id());

        assertTrue(TaskTexts.alsoRequires(graph, updated, Set.of(wood.id(), table.id())).isEmpty());
    }

    @Test
    void unlockedNamesOneTask() {
        TaskGraph graph = new TaskGraph();
        Task oven = graph.create("Horno", "minecraft:furnace");

        assertEquals("▶ Ahora se puede hacer: \"Horno\".", TaskTexts.unlocked(graph, List.of(oven)));
    }

    @Test
    void unlockedNamesSeveralAndTheirProjectOutsideGeneral() {
        TaskGraph graph = new TaskGraph();
        Project mining = graph.createProject("Minería");
        Task oven = graph.create("Horno", "minecraft:furnace");
        Task pickaxe = graph.create("Pico de hierro", "minecraft:iron_pickaxe", null, mining.id());

        assertEquals("▶ Ahora se pueden hacer: \"Horno\", \"Pico de hierro\" (en Minería).",
                TaskTexts.unlocked(graph, List.of(oven, pickaxe)));
    }

    @Test
    void sizesAreCapitalized() {
        assertEquals("Chica", TaskTexts.size(TaskSize.SMALL));
        assertEquals("Mediana", TaskTexts.size(TaskSize.MEDIUM));
        assertEquals("Grande", TaskTexts.size(TaskSize.LARGE));
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void everyStatusHasLabelAndMarker(TaskStatus status) {
        assertFalse(TaskTexts.status(status).isBlank());
        assertFalse(TaskTexts.marker(status).isBlank());
    }
}
