package dev.taskgraph.storage;

import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskGraphException;
import dev.taskgraph.graph.TaskKey;
import dev.taskgraph.graph.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTaskStoreTest {

    @TempDir
    Path directory;

    private Path file;
    private JsonTaskStore store;

    @BeforeEach
    void setUp() {
        file = directory.resolve("tasks.json");
        store = new JsonTaskStore(file);
    }

    private static TaskGraph sampleGraph() {
        TaskGraph graph = new TaskGraph();
        Task wood = graph.create("Juntar madera", "minecraft:oak_log",
                new PlayerRef(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"), "Alex"));
        Task stone = graph.create("Picar piedra", "minecraft:cobblestone");
        Task furnace = graph.create("Armar un horno", "minecraft:furnace");
        graph.addDependency(furnace.id(), stone.id());
        graph.addDependency(furnace.id(), wood.id());
        graph.setDescription(furnace.id(), "Para cocinar la comida del pueblo: ñandú, ají y más");
        graph.assign(stone.id(), new PlayerRef(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "Steve"));
        graph.resolve(wood.id());
        return graph;
    }

    @Test
    void devServerSampleWithThirtyTasksIsValid() throws IOException {
        TaskGraph sample = DevServerSamples.load("tasks-30.json", directory);

        assertEquals(30, sample.size());
        assertEquals(sample.tasks(), sample.tasksIn(Project.GENERAL_ID));
        assertEquals(Set.of(TaskStatus.values()),
                sample.tasks().stream().map(task -> sample.status(task.id())).collect(Collectors.toSet()));
    }

    @Test
    void devServerSampleWithProjectsIsValidAndCrossesProjects() throws IOException {
        TaskGraph sample = DevServerSamples.load("tasks-projects.json", directory);

        assertEquals(List.of(Project.GENERAL_ID, "base", "mineria"), sample.projects().stream().map(Project::id).toList());
        assertEquals(11, sample.size());
        assertTrue(sample.tasks().stream().anyMatch(task -> task.dependencies().stream()
                .anyMatch(id -> !sample.get(id).orElseThrow().project().equals(task.project()))));
        assertEquals(Set.of(TaskStatus.values()),
                sample.tasks().stream().map(task -> sample.status(task.id())).collect(Collectors.toSet()));
        assertFalse(Files.exists(directory.resolve("tasks-projects.v1.json.bak")));
    }

    @Test
    void missingFileLoadsEmptyGraph() {
        assertTrue(store.load().isEmpty());
    }

    @Test
    void emptyFileLoadsEmptyGraph() throws IOException {
        Files.writeString(file, "");

        assertTrue(store.load().isEmpty());
    }

    @Test
    void saveAndLoadRoundTripKeepsEveryField() {
        TaskGraph original = sampleGraph();

        store.save(original);
        TaskGraph loaded = store.load();

        assertEquals(original.tasks(), loaded.tasks());
    }

    @Test
    void roundTripKeepsRemovedIds() {
        TaskGraph original = sampleGraph();
        Task old = original.create("Vieja", "minecraft:stone");
        original.remove(old.id());

        store.save(original);

        assertEquals(Set.of(old.id()), store.load().removedIds());
    }

    @Test
    void savedFileIsReadableUtf8WithVersion() throws IOException {
        store.save(sampleGraph());

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"version\": 2"));
        assertTrue(json.contains("ñandú, ají"));
    }

    @Test
    void roundTripKeepsProjectsTaskProjectsAndRetiredKeys() {
        TaskGraph original = sampleGraph();
        Project base = original.createProject("Base");
        original.renameProject(Project.GENERAL_ID, "Todo");
        Task wall = original.create("Muralla", "minecraft:stone_bricks", null, base.id());
        Task moved = original.create("Mudanza", "minecraft:chest");
        original.moveTask(moved.id(), base.id());
        original.remove(wall.id());

        store.save(original);
        TaskGraph loaded = store.load();

        assertEquals(original.projects(), loaded.projects());
        assertEquals(original.tasks(), loaded.tasks());
        assertEquals(Set.of(new TaskKey(Project.GENERAL_ID, moved.id()), new TaskKey(base.id(), wall.id())),
                loaded.retiredKeys());
        assertEquals(original.removedIds(), loaded.removedIds());
    }

    @Test
    void legacyFileIsMigratedToGeneralWithABackup() throws IOException {
        String legacy = """
                {
                  "version": 1,
                  "tasks": [{"id": "a", "title": "Árbol", "icon": "minecraft:oak_sapling", "resolved": true}],
                  "removedIds": ["old"]
                }
                """;
        Files.writeString(file, legacy, StandardCharsets.UTF_8);

        TaskGraph loaded = store.load();

        assertEquals(Project.GENERAL_ID, loaded.get("a").orElseThrow().project());
        assertEquals(Set.of(new TaskKey(Project.GENERAL_ID, "old")), loaded.retiredKeys());
        assertEquals(Set.of("old"), loaded.removedIds());
        assertEquals(legacy, Files.readString(directory.resolve("tasks.v1.json.bak"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"version\": 2"));
        assertEquals(loaded.tasks(), store.load().tasks());
    }

    @Test
    void currentFormatLeavesNoBackup() {
        store.save(sampleGraph());

        store.load();

        assertFalse(Files.exists(store.legacyBackup()));
    }

    @Test
    void legacyBackupSitsNextToTheFile() {
        assertEquals(directory.resolve("tasks.v1.json.bak"), store.legacyBackup());
    }

    @Test
    void taskInUnknownProjectIsRejected() throws IOException {
        Files.writeString(file, """
                {"version": 2, "tasks": [{"id": "a", "title": "A", "icon": "minecraft:stone", "project": "ghost"}]}
                """);

        TaskStoreException exception = assertThrows(TaskStoreException.class, store::load);
        assertInstanceOf(TaskGraphException.class, exception.getCause());
    }

    @Test
    void saveOverwritesAndLeavesNoTemporaryFile() {
        store.save(sampleGraph());
        TaskGraph smaller = new TaskGraph();
        smaller.create("Una sola", "minecraft:stone");

        store.save(smaller);

        assertEquals(1, store.load().size());
        assertFalse(Files.exists(directory.resolve("tasks.json.tmp")));
    }

    @Test
    void saveCreatesMissingDirectories() {
        JsonTaskStore nested = new JsonTaskStore(directory.resolve("plugins/TaskGraph/tasks.json"));

        nested.save(sampleGraph());

        assertEquals(3, nested.load().size());
    }

    @Test
    void malformedJsonIsRejectedAndFileIsKept() throws IOException {
        Files.writeString(file, "{ not json");

        assertThrows(TaskStoreException.class, store::load);
        assertEquals("{ not json", Files.readString(file));
    }

    @Test
    void graphRuleViolationIsRejectedWithoutMigrating() throws IOException {
        String broken = """
                {
                  "version": 1,
                  "tasks": [
                    {"id": "a", "title": "A", "icon": "minecraft:stone", "dependencies": ["b"]},
                    {"id": "b", "title": "B", "icon": "minecraft:stone", "dependencies": ["a"]}
                  ]
                }
                """;
        Files.writeString(file, broken);

        TaskStoreException exception = assertThrows(TaskStoreException.class, store::load);
        assertInstanceOf(TaskGraphException.class, exception.getCause());
        assertEquals(broken, Files.readString(file));
        assertFalse(Files.exists(store.legacyBackup()));
    }

    @Test
    void unsupportedVersionIsRejected() throws IOException {
        Files.writeString(file, "{\"version\": 99, \"tasks\": []}");

        assertThrows(TaskStoreException.class, store::load);
    }

    @Test
    void optionalFieldsDefaultWhenMissing() throws IOException {
        Files.writeString(file, """
                {"version": 1, "tasks": [{"id": "a", "title": "A", "icon": "minecraft:stone"}]}
                """);

        var task = store.load().get("a").orElseThrow();
        assertEquals("", task.description());
        assertTrue(task.dependencies().isEmpty());
        assertTrue(task.assigneeOptional().isEmpty());
        assertFalse(task.resolved());
    }
}
