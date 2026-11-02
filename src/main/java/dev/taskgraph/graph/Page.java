package dev.taskgraph.graph;

import java.util.List;

/**
 * One page of a list, for screens that show a fixed number of entries at a time.
 *
 * @param items the entries on this page
 * @param index zero-based page number
 * @param count number of pages, at least 1 even for an empty list
 */
public record Page<T>(List<T> items, int index, int count) {

    public Page {
        items = List.copyOf(items);
    }

    /** The requested page, clamped to the existing ones, so a stale page number never fails. */
    public static <T> Page<T> of(List<T> all, int requested, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be positive: " + size);
        }
        int count = Math.max(1, (all.size() + size - 1) / size);
        int index = Math.clamp(requested, 0, count - 1);
        int from = index * size;
        return new Page<>(all.subList(from, Math.min(all.size(), from + size)), index, count);
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean hasNext() {
        return index < count - 1;
    }
}
