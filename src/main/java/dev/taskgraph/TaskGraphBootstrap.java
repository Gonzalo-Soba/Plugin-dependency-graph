package dev.taskgraph;

import dev.taskgraph.dialog.QuickAccess;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Set;

/**
 * Registers the quick access dialog and adds it to the quick actions key and the pause menu.
 * <p>
 * This is the only class that uses experimental Paper API ({@link PluginBootstrap}, and the version-dependent
 * {@link DialogTagKeys#QUICK_ACTIONS} and {@link DialogTagKeys#PAUSE_SCREEN_ADDITIONS} tags). Check it first when
 * moving to a new Paper version. Removing it (and the {@code bootstrapper} line in paper-plugin.yml) only removes
 * these shortcuts; {@code /tareas} keeps working.
 */
public final class TaskGraphBootstrap implements PluginBootstrap {

    private static final TypedKey<Dialog> DIALOG_KEY = TypedKey.create(RegistryKey.DIALOG, QuickAccess.DIALOG);

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
                event.registry().register(DIALOG_KEY, builder -> builder
                        .base(DialogBase.builder(Component.text("Tareas del servidor"))
                                // Label of the pause menu button.
                                .externalTitle(Component.text("Tareas del servidor"))
                                .canCloseWithEscape(true)
                                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                                .body(List.of(DialogBody.plainMessage(Component.text(
                                        "Abrí la lista para ver, crear y resolver las tareas comunales.", NamedTextColor.GRAY))))
                                .build())
                        .type(DialogType.multiAction(List.of(
                                        ActionButton.builder(Component.text("Abrir lista", NamedTextColor.GREEN))
                                                .width(200)
                                                .action(DialogAction.customClick(QuickAccess.OPEN_LIST, null))
                                                .build(),
                                        ActionButton.builder(Component.text("Nueva tarea", NamedTextColor.GOLD))
                                                .width(200)
                                                .action(DialogAction.customClick(QuickAccess.NEW_TASK, null))
                                                .build()))
                                .columns(1)
                                .build())));

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event -> {
            event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, Set.of(DIALOG_KEY));
            event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, Set.of(DIALOG_KEY));
        });
    }
}
