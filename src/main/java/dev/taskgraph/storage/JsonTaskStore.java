package dev.taskgraph.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskGraphException;
import dev.taskgraph.graph.TaskKey;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stores the task graph as a JSON file. This file is the source of truth for every task and its state.
 * <p>
 * Version 1 had no projects. Loading it puts every task in General, writes a copy of the old file next to
 * it ({@code tasks.v1.json.bak}) and saves the graph again as version 2.
 */
public final class JsonTaskStore {

    static final int FORMAT_VERSION = 2;
    static final int LEGACY_FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
            .create();

    private final Path file;
    private final Logger logger;

    public JsonTaskStore(Path file) {
        this(file, Logger.getLogger(JsonTaskStore.class.getName()));
    }

    /** @param logger reports the migration from version 1 */
    public JsonTaskStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    /** Where a version 1 file is copied before it is migrated: {@code tasks.json} becomes {@code tasks.v1.json.bak}. */
    public Path legacyBackup() {
        String name = file.getFileName().toString();
        String base = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
        return file.resolveSibling(base + ".v" + LEGACY_FORMAT_VERSION + ".json.bak");
    }

    /** Loads the graph, or returns an empty one if the file does not exist yet. */
    public TaskGraph load() {
        if (Files.notExists(file)) {
            return new TaskGraph();
        }
        StoredGraph stored;
        TaskGraph graph;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            stored = GSON.fromJson(reader, StoredGraph.class);
            if (stored == null) {
                return new TaskGraph();
            }
            graph = switch (stored.version()) {
                case FORMAT_VERSION -> TaskGraph.of(
                        orEmpty(stored.projects()), orEmpty(stored.tasks()),
                        orEmpty(stored.removedIds()), orEmpty(stored.retiredKeys()));
                // Version 1 tasks have no project field, so they are read into General.
                case LEGACY_FORMAT_VERSION -> TaskGraph.of(orEmpty(stored.tasks()), orEmpty(stored.removedIds()));
                default -> throw new TaskStoreException("Unsupported format version " + stored.version() + " in " + file, null);
            };
        } catch (IOException | JsonParseException | TaskGraphException exception) {
            throw new TaskStoreException("Could not load tasks from " + file, exception);
        }
        if (stored.version() == LEGACY_FORMAT_VERSION) {
            migrate(graph);
        }
        return graph;
    }

    /** Only runs once the old file loaded fine, so a broken file is never replaced. */
    private void migrate(TaskGraph graph) {
        Path backup = legacyBackup();
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new TaskStoreException("Could not back up " + file + " to " + backup + " before migrating it", exception);
        }
        save(graph);
        logger.info("Migrated " + file.getFileName() + " to format version " + FORMAT_VERSION
                + "; the old file is kept as " + backup.getFileName());
    }

    /**
     * Writes to a temporary file first and then moves it over the old one, so a crash mid-write
     * never leaves a truncated task file.
     */
    public void save(TaskGraph graph) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(new StoredGraph(FORMAT_VERSION, graph.projects(), graph.tasks(),
                        List.copyOf(graph.removedIds()), List.copyOf(graph.retiredKeys())), writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new TaskStoreException("Could not save tasks to " + file, exception);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** ISO-8601 in UTC, e.g. {@code 2026-11-03T18:20:00Z}. Gson cannot reach into {@code java.time} by reflection. */
    private static final class InstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.STRING) {
                throw new JsonParseException("Expected a date string at " + in.getPath());
            }
            String text = in.nextString();
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException exception) {
                throw new JsonParseException("Invalid date " + text + " at " + in.getPath(), exception);
            }
        }
    }

    private record StoredGraph(int version, List<Project> projects, List<Task> tasks, List<String> removedIds,
                               List<TaskKey> retiredKeys) {
    }
}
