package dev.taskgraph.advancement;

import dev.taskgraph.graph.Project;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabMemoryTest {

    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID STEVE = UUID.randomUUID();

    private static final String ADVENTURE = "minecraft:adventure/root";
    private static final String GENERAL = tab(TabNamespaces.GENERAL);
    private static final String BASE = tab(TabNamespaces.of("base"));
    private static final String MINING = tab(TabNamespaces.of("mineria"));
    private static final String NEW_TASK = tab(TabNamespaces.NEW_TASK);

    private final TabMemory memory = new TabMemory();
    private final Set<String> projects = Set.of(Project.GENERAL_ID, "base", "mineria");

    private static String tab(String namespace) {
        return namespace + ":root";
    }

    private Optional<String> open(UUID player, String tabId, long now) {
        return memory.opened(player, tabId, now, projects::contains);
    }

    @Test
    void withoutAnyTabTheOriginIsGeneral() {
        assertEquals(Project.GENERAL_ID, memory.origin(ALEX, projects::contains));
    }

    @Test
    void originIsTheLastProjectTabPerPlayer() {
        open(ALEX, BASE, 0);
        open(ALEX, MINING, 10);
        open(ALEX, ADVENTURE, 20);
        open(STEVE, BASE, 0);

        assertEquals("mineria", memory.origin(ALEX, projects::contains));
        assertEquals("base", memory.origin(STEVE, projects::contains));
    }

    @Test
    void newTabsAreNeverRemembered() {
        open(ALEX, BASE, 0);

        assertEquals(Optional.empty(), open(ALEX, NEW_TASK, 10));
        memory.closed(ALEX);

        assertEquals(Optional.of(BASE), open(ALEX, ADVENTURE, 5000));
        assertEquals("base", memory.origin(ALEX, projects::contains));
    }

    @Test
    void screenOpeningOnAnotherTabBringsBackTheLastOne() {
        open(ALEX, BASE, 0);
        memory.closed(ALEX);

        assertEquals(Optional.of(BASE), open(ALEX, ADVENTURE, 5000));
        // The client reports its default tab twice while the screen opens.
        assertEquals(Optional.of(BASE), open(ALEX, ADVENTURE, 5100));
    }

    @Test
    void screenOpeningOnTheLastTabNeedsNothing() {
        open(ALEX, BASE, 0);
        memory.closed(ALEX);

        assertEquals(Optional.empty(), open(ALEX, BASE, 5000));
    }

    @Test
    void choosingTheDefaultTabLaterIsThePlayersChoice() {
        open(ALEX, BASE, 0);
        memory.closed(ALEX);
        open(ALEX, ADVENTURE, 5000);

        assertEquals(Optional.empty(), open(ALEX, ADVENTURE, 5000 + TabMemory.OPENING_WINDOW_MILLIS + 1));
        memory.closed(ALEX);

        assertEquals(Optional.empty(), open(ALEX, ADVENTURE, 9000));
    }

    @Test
    void clickingAnotherTabWhileOpenIsRemembered() {
        open(ALEX, BASE, 0);
        open(ALEX, MINING, 100);
        memory.closed(ALEX);

        assertEquals(Optional.of(MINING), open(ALEX, GENERAL, 5000));
    }

    @Test
    void firstOpeningEverRestoresNothing() {
        assertEquals(Optional.empty(), open(ALEX, ADVENTURE, 0));
    }

    @Test
    void deletedProjectIsNotBroughtBackAndOriginFallsBackToGeneral() {
        open(ALEX, tab(TabNamespaces.of("castillo")), 0);
        memory.closed(ALEX);

        assertEquals(Optional.empty(), open(ALEX, ADVENTURE, 5000));
        assertEquals(Project.GENERAL_ID, memory.origin(ALEX, projects::contains));
    }

    @Test
    void forgettingAPlayerStartsOver() {
        open(ALEX, BASE, 0);
        memory.closed(ALEX);

        memory.forget(ALEX);

        assertEquals(Optional.empty(), open(ALEX, ADVENTURE, 5000));
        assertEquals(Project.GENERAL_ID, memory.origin(ALEX, projects::contains));
    }
}
