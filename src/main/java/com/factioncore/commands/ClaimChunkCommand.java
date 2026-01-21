package com.factioncore.commands;

import com.factioncore.claim.ChunkInfo;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
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
 * Command handler for /f claim
 * Claims the chunk where the player is standing.
 */
public class ClaimChunkCommand extends AbstractAsyncCommand {
    
    public ClaimChunkCommand() {
        super("claim", "Claims the chunk where you are");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            commandContext.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            commandContext.sendMessage(Message.raw("You must be in a world to use this command").color(Color.RED));
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
            if (!PermissionUtil.canClaimChunk(playerUuid)) {
                player.sendMessage(Message.raw("You don't have permission to claim chunks").color(Color.RED));
                return;
            }
            
            FactionManager manager = FactionManager.getInstance();
            
            // Check if claiming is allowed in this dimension
            if (!manager.canClaimInDimension(world)) {
                player.sendMessage(Message.raw("You cannot claim chunks in this dimension").color(Color.RED));
                return;
            }
            
            // Get or create faction
            FactionInfo faction = manager.getFactionFromPlayer(playerRef.getUuid());
            if (faction == null) {
                player.sendMessage(Message.raw("You must be in a faction to claim chunks. Use /f create <name> to create one.").color(Color.RED));
                return;
            }
            
            // Get player position
            int blockX = (int) playerRef.getTransform().getPosition().getX();
            int blockZ = (int) playerRef.getTransform().getPosition().getZ();
            World playerWorld = player.getWorld();
            if (playerWorld == null) {
                player.sendMessage(Message.raw("Error: Could not get world").color(Color.RED));
                return;
            }
            String dimension = playerWorld.getName();
            
            // Check if chunk is already claimed
            ChunkInfo existingChunk = manager.getChunkRawCoords(dimension, blockX, blockZ);
            if (existingChunk != null) {
                if (existingChunk.getFactionOwner().equals(faction.getId())) {
                    player.sendMessage(Message.raw("This chunk is already claimed by your faction").color(Color.YELLOW));
                } else {
                    player.sendMessage(Message.raw("This chunk is already claimed by another faction").color(Color.RED));
                }
                return;
            }
            
            // Check if faction has enough claims left
            if (!manager.hasEnoughClaimsLeft(faction)) {
                int maxClaims = faction.getMaxClaimAmount();
                int currentClaims = manager.getAmountOfClaims(faction);
                player.sendMessage(Message.raw("You have reached your claim limit (" + currentClaims + "/" + maxClaims + ")").color(Color.RED));
                return;
            }
            
            // Claim the chunk
            ChunkInfo chunkInfo = manager.claimChunkByRawCoords(dimension, blockX, blockZ, faction, player, playerRef);
            player.sendMessage(Message.raw("Chunk claimed successfully! (" + manager.getAmountOfClaims(faction) + "/" + faction.getMaxClaimAmount() + " claims)").color(Color.GREEN));
        }, (Executor) world);
    }
}
