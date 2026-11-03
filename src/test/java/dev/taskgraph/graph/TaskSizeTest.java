package dev.taskgraph.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskSizeTest {

    @Test
    void parsesTheSpanishWords() {
        assertEquals(Optional.of(TaskSize.SMALL), TaskSize.parse("chica"));
        assertEquals(Optional.of(TaskSize.MEDIUM), TaskSize.parse("mediana"));
        assertEquals(Optional.of(TaskSize.LARGE), TaskSize.parse("grande"));
    }

    @Test
    void ignoresCaseAccentsAndSpaces() {
        assertEquals(Optional.of(TaskSize.MEDIUM), TaskSize.parse("  MEDIÁNA "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "chico", "large", "SMALL"})
    void rejectsAnythingElse(String input) {
        assertEquals(Optional.empty(), TaskSize.parse(input));
    }

    @Test
    void nullIsNoSize() {
        assertEquals(Optional.empty(), TaskSize.parse(null));
    }

    @Test
    void wordsAreInSizeOrder() {
        assertEquals(List.of("chica", "mediana", "grande"), List.of(TaskSize.values()).stream().map(TaskSize::word).toList());
    }
}
