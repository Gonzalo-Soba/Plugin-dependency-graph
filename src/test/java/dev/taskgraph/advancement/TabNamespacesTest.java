package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabNamespacesTest {

    // Same pattern as UAAPI's AdvancementKey.VALID_NAMESPACE, which tests cannot load.
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,127}");

    @Test
    void generalKeepsTheNamespaceFromBeforeProjects() {
        assertEquals("taskgraph", TabNamespaces.of(Project.GENERAL_ID));
    }

    @Test
    void otherProjectsGetTheirIdAfterThePrefix() {
        assertEquals("taskgraph.base_del_norte", TabNamespaces.of("base_del_norte"));
    }

    @Test
    void noProjectIdClashesWithGeneral() {
        // "taskgraph" itself is a valid project id, and still gets a namespace of its own.
        assertNotEquals(TabNamespaces.GENERAL, TabNamespaces.of("taskgraph"));
    }

    @Test
    void longestProjectIdIsAValidNamespace() {
        assertTrue(VALID_NAMESPACE.matcher(TabNamespaces.of("a".repeat(32))).matches());
    }
}
