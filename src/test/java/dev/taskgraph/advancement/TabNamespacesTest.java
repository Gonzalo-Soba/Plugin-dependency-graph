package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;
import org.junit.jupiter.api.Test;

import java.util.Optional;
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
    void projectIsReadBackFromItsNamespace() {
        assertEquals(Optional.of(Project.GENERAL_ID), TabNamespaces.projectOf(TabNamespaces.of(Project.GENERAL_ID)));
        assertEquals(Optional.of("base_del_norte"), TabNamespaces.projectOf(TabNamespaces.of("base_del_norte")));
        assertEquals(Optional.of("taskgraph"), TabNamespaces.projectOf(TabNamespaces.of("taskgraph")));
    }

    @Test
    void newTabsAndForeignTabsAreNoProject() {
        assertEquals(Optional.empty(), TabNamespaces.projectOf(TabNamespaces.NEW_TASK));
        assertEquals(Optional.empty(), TabNamespaces.projectOf(TabNamespaces.NEW_PROJECT));
        assertEquals(Optional.empty(), TabNamespaces.projectOf("minecraft"));
        assertEquals(Optional.empty(), TabNamespaces.projectOf("taskgraph."));
        assertEquals(Optional.empty(), TabNamespaces.projectOf("taskgraph.a.b"));
    }

    @Test
    void newTabsAreValidNamespacesNoProjectCanTake() {
        for (String namespace : new String[]{TabNamespaces.NEW_TASK, TabNamespaces.NEW_PROJECT}) {
            assertTrue(VALID_NAMESPACE.matcher(namespace).matches(), namespace);
            assertNotEquals(TabNamespaces.GENERAL, namespace);
            assertTrue(!namespace.startsWith(TabNamespaces.PROJECT_PREFIX), namespace);
        }
    }

    @Test
    void tabIdIsTheRootAdvancementId() {
        assertEquals("taskgraph.base", TabNamespaces.ofTabId("taskgraph.base:root"));
        assertEquals(TabNamespaces.NEW_TASK, TabNamespaces.ofTabId(TabNamespaces.NEW_TASK + ":root"));
        assertEquals("minecraft", TabNamespaces.ofTabId("adventure/root"));
    }

    @Test
    void longestProjectIdIsAValidNamespace() {
        assertTrue(VALID_NAMESPACE.matcher(TabNamespaces.of("a".repeat(32))).matches());
    }
}
