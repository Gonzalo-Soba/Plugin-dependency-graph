package dev.taskgraph.storage;

import dev.taskgraph.graph.TaskGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Test data from {@code dev-server/samples/}, which is what the dev server copies are made from. */
public final class DevServerSamples {

    private DevServerSamples() {
    }

    /**
     * Loads a sample through a copy in {@code directory}: loading a version 1 file migrates it in place,
     * and the samples in the repository must stay as they are. Tests run from the project directory.
     */
    public static TaskGraph load(String name, Path directory) throws IOException {
        Path copy = directory.resolve(name);
        Files.copy(Path.of("dev-server", "samples", name), copy);
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        return new JsonTaskStore(copy, quiet).load();
    }
}
