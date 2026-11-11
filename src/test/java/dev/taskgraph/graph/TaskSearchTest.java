package dev.taskgraph.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSearchTest {

    private static final String ICON = "minecraft:stone";

    private TaskGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TaskGraph();
    }

    private List<String> titles(String text) {
        return TaskSearch.find(graph, text).stream().map(Task::title).toList();
    }

    @Test
    void ignoresCaseAccentsAndSpaces() {
        graph.create("Jardín de la Ñandú", ICON);

        assertEquals(List.of("Jardín de la Ñandú"), titles("jardin"));
        assertEquals(List.of("Jardín de la Ñandú"), titles("  DE   LA  nandu "));
    }

    @Test
    void findsTextInsideTheTitle() {
        graph.create("Muralla", ICON);
        graph.create("Pozo", ICON);

        assertEquals(List.of("Muralla"), titles("ural"));
    }

    @Test
    void blankTextOrNoMatchFindsNothing() {
        graph.create("Muralla", ICON);

        assertTrue(titles("").isEmpty());
        assertTrue(titles("   ").isEmpty());
        assertTrue(titles("torre").isEmpty());
    }

    @Test
    void closestMatchesComeFirst() {
        graph.create("Arreglar la torre", ICON);  // a word starts with it
        graph.create("Torreón", ICON);            // the title starts with it
        graph.create("Retorre", ICON);            // only contains it
        graph.create("Torre", ICON);              // exact

        assertEquals(List.of("Torre", "Torreón", "Arreglar la torre", "Retorre"), titles("torre"));
    }

    @Test
    void alphabeticalWithinTheSameGroup() {
        graph.create("Pico de hierro", ICON);
        graph.create("Pico de diamante", ICON);

        assertEquals(List.of("Pico de diamante", "Pico de hierro"), titles("pico"));
    }

    @Test
    void searchesEveryProject() {
        graph.createProject("Base");
        String base = graph.findProjectByTitle("Base").orElseThrow().id();
        graph.create("Torre", ICON, null, base);
        graph.create("Torre de vigilancia", ICON);

        assertEquals(List.of("Torre", "Torre de vigilancia"), titles("torre"));
    }
}
