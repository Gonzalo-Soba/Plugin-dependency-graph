package dev.taskgraph.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"diamond", "minecraft:diamond", "DIAMOND", "Minecraft:Diamond", "  diamond  "})
    void acceptsIdWithOrWithoutNamespaceIgnoringCase(String input) {
        assertEquals(Optional.of("minecraft:diamond"), IconKeys.normalize(input));
    }

    @Test
    void keepsOtherNamespaces() {
        assertEquals(Optional.of("custom:ruby"), IconKeys.normalize("custom:ruby"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "minecraft:", ":diamond", "diamond pickaxe", "diamante!", "a:b:c"})
    void rejectsInputThatIsNotAnItemId(String input) {
        assertTrue(IconKeys.normalize(input).isEmpty());
    }

    @Test
    void displayDropsOnlyTheMinecraftNamespace() {
        assertEquals("oak_log", IconKeys.display("minecraft:oak_log"));
        assertEquals("custom:ruby", IconKeys.display("custom:ruby"));
    }
}
