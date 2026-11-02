package dev.taskgraph.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconCategoryTest {

    @ParameterizedTest
    @EnumSource(IconCategory.class)
    void itemsAreNormalizedKeysThatFitOnOnePage(IconCategory category) {
        assertFalse(category.items().isEmpty());
        assertTrue(category.items().size() <= IconCategory.PAGE_SIZE, category + " has " + category.items().size());
        for (String key : category.items()) {
            assertEquals(Optional.of(key), IconKeys.normalize(key), key);
        }
    }

    @ParameterizedTest
    @EnumSource(IconCategory.class)
    void iconIsOneOfItsItems(IconCategory category) {
        assertTrue(category.items().contains(category.icon()), category.icon());
    }

    @Test
    void noItemIsInTwoCategories() {
        Set<String> seen = new HashSet<>();
        for (IconCategory category : IconCategory.values()) {
            for (String key : category.items()) {
                assertTrue(seen.add(key), key + " is repeated, last in " + category);
            }
        }
    }

    @Test
    void titlesAreUnique() {
        List<String> titles = List.of(IconCategory.values()).stream().map(IconCategory::title).toList();

        assertEquals(titles.size(), Set.copyOf(titles).size());
    }
}
