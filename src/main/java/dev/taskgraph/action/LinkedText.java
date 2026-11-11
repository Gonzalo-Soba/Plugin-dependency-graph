package dev.taskgraph.action;

import dev.taskgraph.graph.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plain text where some parts name a task, e.g. an announcement. Each part keeps the task's internal id, so
 * the Paper layer can turn those parts into chat links without searching the text for titles.
 */
public record LinkedText(List<Part> parts) {

    /** @param taskId the task this part names, or {@code null} for plain text */
    public record Part(String text, String taskId) {

        public Part {
            Objects.requireNonNull(text);
        }

        public Optional<String> taskIdOptional() {
            return Optional.ofNullable(taskId);
        }
    }

    public LinkedText {
        parts = List.copyOf(parts);
    }

    /** The whole text, as the console and the tests see it. */
    public String text() {
        StringBuilder text = new StringBuilder();
        parts.forEach(part -> text.append(part.text()));
        return text.toString();
    }

    /** Ids of the tasks this text names, in order. */
    public List<String> taskIds() {
        return parts.stream().map(Part::taskId).filter(Objects::nonNull).toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Part> parts = new ArrayList<>();

        private Builder() {
        }

        public Builder text(String text) {
            if (!text.isEmpty()) {
                parts.add(new Part(text, null));
            }
            return this;
        }

        public Builder link(String taskId, String text) {
            parts.add(new Part(text, taskId));
            return this;
        }

        /** The task's title between quotes, as announcements write it: "Madera". */
        public Builder quoted(Task task) {
            return link(task.id(), TaskActions.quoted(task.title()));
        }

        public Builder append(LinkedText other) {
            parts.addAll(other.parts());
            return this;
        }

        public LinkedText build() {
            return new LinkedText(parts);
        }
    }
}
