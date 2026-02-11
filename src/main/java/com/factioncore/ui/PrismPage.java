package com.factioncore.ui;

import com.factioncore.prism.PrismService;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.factioncore.ui.events.PrismPageEventData;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import javax.annotation.Nonnull;

/**
 * UI page for prism: "Test prism" (enter) or "Leave prism" (exit) depending on current world.
 */
public class PrismPage extends InteractiveCustomUIPage<PrismPageEventData> {

    public PrismPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PrismPageEventData.CODEC);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/PrismPage.ui");

        World world = store.getExternalData().getWorld();
        boolean inPrism = PrismService.isPrismWorld(world.getName());

        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.prism.title"));
        if (inPrism) {
            commandBuilder.set("#ActionButton.Text", I18n.get("ui.prism.leave_button"));
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ActionButton",
                    new EventData().append("Action", "leavePrism")
            );
        } else {
            commandBuilder.set("#ActionButton.Text", I18n.get("ui.prism.test_button"));
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ActionButton",
                    new EventData().append("Action", "testPrism")
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PrismPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            player.sendMessage(Message.raw(I18n.get("chat.command.no_player_ref")).color(Color.RED));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }

        Runnable onError = () -> {
            player.sendMessage(Message.raw(I18n.get("chat.prism.error")).color(Color.RED));
            player.getPageManager().setPage(ref, store, Page.None);
        };

        if ("testPrism".equals(data.action)) {
            if (!PermissionUtil.canUsePrism(playerRef.getUuid())) {
                player.sendMessage(Message.raw(I18n.get("chat.prism.no_permission")).color(Color.RED));
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
            PrismService.getInstance().enterPrism(ref, store, playerRef, onError);
            player.getPageManager().setPage(ref, store, Page.None);
        } else if ("leavePrism".equals(data.action)) {
            PrismService.getInstance().leavePrism(ref, store, playerRef, onError);
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
