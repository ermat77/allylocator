package com.factioncore.ui.factionbase;

import com.factioncore.ui.factionbase.events.ConfirmationPageEventData;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Reusable confirmation dialog page. Caller supplies title, message, button labels,
 * and callbacks for confirm/cancel. Typically used before destructive or important actions.
 */
public class ConfirmationPage extends InteractiveCustomUIPage<ConfirmationPageEventData> {

    private final String title;
    private final String message;
    private final String confirmLabel;
    private final String cancelLabel;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmationPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull String title,
        @Nonnull String message,
        @Nonnull String confirmLabel,
        @Nonnull String cancelLabel,
        @Nonnull Runnable onConfirm,
        @Nonnull Runnable onCancel
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfirmationPageEventData.CODEC);
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/FactionBase/ConfirmationPage.ui");
        commandBuilder.set("#titleLabel.Text", title != null ? title : "");
        commandBuilder.set("#messageLabel.Text", message != null ? message : "");
        commandBuilder.set("#confirmButton.Text", confirmLabel != null ? confirmLabel : I18n.get("ui.confirm.confirm"));
        commandBuilder.set("#cancelButton.Text", cancelLabel != null ? cancelLabel : I18n.get("ui.confirm.cancel"));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#confirmButton",
            new EventData().append("Action", "confirm"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#cancelButton",
            new EventData().append("Action", "cancel"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ConfirmationPageEventData data) {
        if ("confirm".equals(data.action)) {
            onConfirm.run();
            return;
        }
        if ("cancel".equals(data.action)) {
            onCancel.run();
            return;
        }
    }
}
