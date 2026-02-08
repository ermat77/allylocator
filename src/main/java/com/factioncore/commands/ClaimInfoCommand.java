package com.factioncore.commands;

import com.factioncore.claim.ChunkInfo;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Command handler for /f claiminfo
 * Shows information about the chunk where the player is standing.
 */
public class ClaimInfoCommand extends AbstractAsyncCommand {
    
    public ClaimInfoCommand() {
        super("claiminfo", "Shows information about the current chunk");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.players_only")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.must_be_in_world")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            
            UUID playerUuid = playerRef.getUuid();
            
            // Check permission
            if (!PermissionUtil.canViewClaimInfo(playerUuid)) {
                player.sendMessage(Message.raw(I18n.get("chat.claiminfo.no_permission")).color(Color.RED));
                return;
            }
            
            FactionManager manager = FactionManager.getInstance();
            
            // Get player position
            int blockX = (int) playerRef.getTransform().getPosition().getX();
            int blockZ = (int) playerRef.getTransform().getPosition().getZ();
            World playerWorld = player.getWorld();
            if (playerWorld == null) {
                player.sendMessage(Message.raw(I18n.get("chat.claiminfo.error_world")).color(Color.RED));
                return;
            }
            String dimension = playerWorld.getName();
            
            // Get chunk info
            ChunkInfo chunk = manager.getChunkRawCoords(dimension, blockX, blockZ);
            
            player.sendMessage(Message.raw(I18n.get("chat.claiminfo.header")).color(Color.YELLOW).bold(true));
            
            if (chunk == null) {
                player.sendMessage(Message.raw(I18n.get("chat.claiminfo.status_not_claimed")).color(Color.GRAY));
            } else {
                FactionInfo chunkFaction = manager.getFactionById(chunk.getFactionOwner());
                if (chunkFaction != null) {
                    player.sendMessage(Message.raw(I18n.get("chat.claiminfo.status_claimed_by", chunkFaction.getName())).color(Color.GREEN));
                    player.sendMessage(Message.raw(I18n.get("chat.claiminfo.chunk_coords", chunk.getChunkX(), chunk.getChunkZ())).color(Color.GRAY));
                    
                    FactionInfo playerFaction = manager.getFactionFromPlayer(playerRef.getUuid());
                    if (playerFaction != null && chunkFaction.getId().equals(playerFaction.getId())) {
                        player.sendMessage(Message.raw(I18n.get("chat.claiminfo.your_claim")).color(Color.GREEN));
                    }
                } else {
                    player.sendMessage(Message.raw(I18n.get("chat.claiminfo.status_faction_not_found")).color(Color.YELLOW));
                }
            }
            
            // Show player's faction claim info
            FactionInfo playerFaction = manager.getFactionFromPlayer(playerRef.getUuid());
            if (playerFaction != null) {
                int currentClaims = manager.getAmountOfClaims(playerFaction);
                int maxClaims = playerFaction.getMaxClaimAmount();
                player.sendMessage(Message.raw(I18n.get("chat.claiminfo.your_faction_claims", currentClaims, maxClaims)).color(Color.CYAN));
            }
        }, (Executor) world);
    }
}
