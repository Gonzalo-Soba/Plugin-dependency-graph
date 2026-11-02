package dev.taskgraph.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    private static final List<Integer> TEN = IntStream.range(0, 10).boxed().toList();

    @Test
    void splitsIntoPagesOfTheGivenSize() {
        Page<Integer> second = Page.of(TEN, 1, 4);

        assertEquals(List.of(4, 5, 6, 7), second.items());
        assertEquals(1, second.index());
        assertEquals(3, second.count());
        assertTrue(second.hasPrevious());
        assertTrue(second.hasNext());
    }

    @Test
    void lastPageHoldsTheRest() {
        Page<Integer> last = Page.of(TEN, 2, 4);

        assertEquals(List.of(8, 9), last.items());
        assertFalse(last.hasNext());
    }

    @Test
    void requestedPageIsClamped() {
        assertEquals(0, Page.of(TEN, -3, 4).index());
        assertEquals(2, Page.of(TEN, 99, 4).index());
    }

    @Test
    void emptyListHasOneEmptyPage() {
        Page<Integer> page = Page.of(List.of(), 0, 4);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.count());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void exactMultipleHasNoTrailingEmptyPage() {
        assertEquals(2, Page.of(TEN.subList(0, 8), 0, 4).count());
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> Page.of(TEN, 0, 0));
    }
}
