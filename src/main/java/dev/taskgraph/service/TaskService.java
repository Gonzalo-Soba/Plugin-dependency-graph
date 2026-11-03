package dev.taskgraph.service;

import dev.taskgraph.graph.PlayerRef;
import dev.taskgraph.graph.Project;
import dev.taskgraph.graph.Task;
import dev.taskgraph.graph.TaskGraph;
import dev.taskgraph.graph.TaskSize;
import dev.taskgraph.storage.JsonTaskStore;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Single entry point for task changes. Each change runs on a copy of the graph and only replaces it
 * once the JSON file is saved, so a rejected or failed change never leaves memory and disk apart.
 * After every change the listener receives the new graph (the plugin rebuilds the advancement tab there).
 * Free of Paper types so it can be unit tested.
 */
public final class TaskService {

    private final JsonTaskStore store;
    private final Consumer<TaskGraph> onChange;

    private TaskGraph graph;

    /**
     * @param onChange receives the current graph on {@link #start()} and after every saved change. The graph
     *                 it gets is never modified afterwards: each change replaces it with a new instance.
     */
    public TaskService(JsonTaskStore store, TaskGraph graph, Consumer<TaskGraph> onChange) {
        this.store = store;
        this.graph = graph;
        this.onChange = onChange;
    }

    /** Publishes the loaded graph, e.g. to build the tab and sync players already online after a reload. */
    public void start() {
        onChange.accept(graph);
    }

    public Optional<Task> task(String id) {
        return graph.get(id);
    }

    public Optional<Task> findByTitle(String title) {
        return graph.findByTitle(title);
    }

    public List<Task> tasks() {
        return graph.tasks();
    }

    public Optional<Project> project(String id) {
        return graph.project(id);
    }

    public Optional<Project> findProjectByTitle(String title) {
        return graph.findProjectByTitle(title);
    }

    /** In tab order: General first, then by creation. */
    public List<Project> projects() {
        return graph.projects();
    }

    /** Read-only view for callers that need graph queries; changes to it are not saved. */
    public TaskGraph snapshot() {
        return graph.copy();
    }

    /** @param creator player creating the task, or {@code null} for the console */
    public Task create(String title, String icon, PlayerRef creator) {
        return create(title, icon, creator, Project.GENERAL_ID);
    }

    /** @param creator player creating the task, or {@code null} for the console */
    public Task create(String title, String icon, PlayerRef creator, String projectId) {
        return apply(next -> next.create(title, icon, creator, projectId));
    }

    public Task moveTask(String id, String projectId) {
        return apply(next -> next.moveTask(id, projectId));
    }

    public Project createProject(String title) {
        return apply(next -> next.createProject(title));
    }

    public Project renameProject(String id, String newTitle) {
        return apply(next -> next.renameProject(id, newTitle));
    }

    public Project removeProject(String id) {
        return apply(next -> next.removeProject(id));
    }

    public Project setHideResolved(String id, boolean hide) {
        return apply(next -> next.setHideResolved(id, hide));
    }

    public Task rename(String id, String newTitle) {
        return apply(next -> next.rename(id, newTitle));
    }

    public Task describe(String id, String description) {
        return apply(next -> next.setDescription(id, description));
    }

    public Task changeSize(String id, TaskSize size) {
        return apply(next -> next.setSize(id, size));
    }

    public Task changeIcon(String id, String icon) {
        return apply(next -> next.setIcon(id, icon));
    }

    public Task addDependency(String id, String dependencyId) {
        return apply(next -> next.addDependency(id, dependencyId));
    }

    public Task removeDependency(String id, String dependencyId) {
        return apply(next -> next.removeDependency(id, dependencyId));
    }

    /** @param replaceOther whether a task assigned to someone else may be taken over (admin reassignment) */
    public Task assign(String id, PlayerRef assignee, boolean replaceOther) {
        return apply(next -> next.assign(id, assignee, replaceOther));
    }

    public Task unassign(String id) {
        return apply(next -> next.unassign(id));
    }

    public Task resolve(String id) {
        return apply(next -> next.resolve(id));
    }

    public Task remove(String id) {
        return apply(next -> next.remove(id));
    }

    private <T> T apply(Function<TaskGraph, T> change) {
        TaskGraph next = graph.copy();
        T result = change.apply(next);
        store.save(next);
        graph = next;
        onChange.accept(graph);
        return result;
    }
}
