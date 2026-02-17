package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.config.PrismAttacksConfig;
import com.factioncore.database.DatabaseManager;
import com.factioncore.faction.FactionManager;
import com.factioncore.points.FactionPointsManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service for prism attacks: in-memory cache of active attacks, lookup by prism id,
 * assign participant slot and persist. Used by PrismAttackEventHandler and by startAttack/joinDefender flows.
 */
public final class PrismAttackService {

    private static final PrismAttackService INSTANCE = new PrismAttackService();

    /** prism_id -> PrismAttackRecord (active attacks only). */
    private final Map<UUID, PrismAttackRecord> prismIdToAttack = new ConcurrentHashMap<>();
    /** attack_id -> PrismAttackRecord. */
    private final Map<UUID, PrismAttackRecord> attackIdToRecord = new ConcurrentHashMap<>();

    private static final String ATTACK_WORLD_PREFIX = "prism-attack-";
    private static final ScheduledExecutorService timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PrismAttack-Timer");
        t.setDaemon(true);
        return t;
    });

    private volatile CompletableFuture<Void> initializationFuture;

    private PrismAttackService() {}

    @Nonnull
    public static PrismAttackService getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes on server start: cleans all prism attacks (no persistence across restart).
     * Cancels every active attack in DB, resets prism status to harvesting, and removes attack worlds.
     */
    public CompletableFuture<Void> initializeAsync() {
        if (initializationFuture != null) {
            return initializationFuture;
        }
        prismIdToAttack.clear();
        attackIdToRecord.clear();
        DatabaseManager db = FactionManager.getInstance().getDatabaseManager();
        initializationFuture = PrismManager.getInstance().initializeAsync()
                .thenCompose(v -> db.loadPrismAttacksActiveAsync())
                .thenAccept(list -> {
                    for (PrismAttackRecord r : list) {
                        db.updatePrismAttackStatus(r.getAttackId(), "CANCELLED");
                        PrismManager.getInstance().updateStatus(r.getPrismId(), "harvesting");
                        try {
                            World w = Universe.get().getWorld(r.getAttackWorldName());
                            if (w != null) {
                                Universe.get().removeWorld(r.getAttackWorldName());
                            }
                        } catch (Exception e) {
                            ConfigUtil.debug("[PrismAttackService] clean on start: removeWorld failed " + r.getAttackWorldName() + ": " + e.getMessage());
                        }
                    }
                    PrismManager.getInstance().resetAllAttackedPrismsToHarvesting();
                    ConfigUtil.debug("[PrismAttackService] cleaned " + list.size() + " prism attacks on start (no persistence across restart)");
                });
        return initializationFuture;
    }

    /**
     * Updates attack status to PREPARING in DB and cache. Schedules combat start (status -> IN_PROGRESS) and destruction (resolve).
     */
    public void updateAttackStatusToActive(@Nonnull UUID attackId) {
        log(Level.INFO, "[PrismAttackService] updateAttackStatusToActive called: attackId=%s", attackId);
        PrismAttackRecord r = attackIdToRecord.get(attackId);
        if (r == null) {
            log(Level.WARNING, "[PrismAttackService] updateAttackStatusToActive: attack not in cache attackId=%s (cache size=%d)",
                    attackId, attackIdToRecord.size());
            return;
        }
        FactionManager.getInstance().getDatabaseManager().updatePrismAttackStatus(attackId, "PREPARING");
        PrismAttackRecord updated = new PrismAttackRecord(r.getAttackId(), r.getPrismId(), r.getAttackWorldName(),
                r.getDefenderFactionId(), r.getAttackerFactionId(), "PREPARING", r.getStartedAtMs(), r.getEndsAtMs(), r.getDestructionAtMs(),
                r.getPrefabId(), r.getAttackersBlockId(), r.getDefendersBlockId(),
                r.getDefenderPositionsJson(), r.getAttackerPositionsJson());
        prismIdToAttack.put(updated.getPrismId(), updated);
        attackIdToRecord.put(attackId, updated);
        log(Level.INFO, "[PrismAttackService] updateAttackStatusToActive done: attackWorldName=%s status=PREPARING", updated.getAttackWorldName());
        long now = System.currentTimeMillis();
        long combatStartDelayMs = updated.getEndsAtMs() - now;
        long destructionDelayMs = updated.getDestructionAtMs() - now;
        if (combatStartDelayMs > 0) {
            scheduleCombatStart(attackId, combatStartDelayMs);
        }
        if (destructionDelayMs > 0) {
            scheduleDestruction(attackId, destructionDelayMs);
        }
    }

    private void scheduleCombatStart(@Nonnull UUID attackId, long delayMs) {
        timerScheduler.schedule(() -> onCombatStart(attackId), delayMs, TimeUnit.MILLISECONDS);
    }

    private void scheduleDestruction(@Nonnull UUID attackId, long delayMs) {
        timerScheduler.schedule(() -> resolveAttackAttackersWin(attackId), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Called when the join window ends: update status to IN_PROGRESS and clear HUD for participants.
     */
    private void onCombatStart(@Nonnull UUID attackId) {
        PrismAttackRecord r = attackIdToRecord.get(attackId);
        if (r == null) return;
        FactionManager.getInstance().getDatabaseManager().updatePrismAttackStatus(attackId, "IN_PROGRESS");
        PrismAttackRecord updated = new PrismAttackRecord(r.getAttackId(), r.getPrismId(), r.getAttackWorldName(),
                r.getDefenderFactionId(), r.getAttackerFactionId(), "IN_PROGRESS", r.getStartedAtMs(), r.getEndsAtMs(), r.getDestructionAtMs(),
                r.getPrefabId(), r.getAttackersBlockId(), r.getDefendersBlockId(),
                r.getDefenderPositionsJson(), r.getAttackerPositionsJson());
        prismIdToAttack.put(updated.getPrismId(), updated);
        attackIdToRecord.put(attackId, updated);
        PrismAttackHudHelper.clearHudForAttack(attackId);
    }

    /**
     * Resolves attack as attackers win: teleport participants back, award FP, remove prism, remove attack world, update DB.
     */
    public void resolveAttackAttackersWin(@Nonnull UUID attackId) {
        PrismAttackRecord attack = attackIdToRecord.get(attackId);
        if (attack == null) return;
        removeAttackFromCache(attackId);
        FactionManager.getInstance().getDatabaseManager().updatePrismAttackStatus(attackId, "RESOLVED_ATTACKERS_WIN");

        PrismRecord prism = PrismManager.getInstance().getById(attack.getPrismId());
        if (prism != null) {
            int timeAliveMinutes = prism.getScoreMinutes(System.currentTimeMillis());
            int multiplier = PrismMultiplierService.getInstance().getMultiplier(prism.getChunkX(), prism.getChunkZ());
            int pointsToAdd = timeAliveMinutes * multiplier;
            FactionPointsManager.getInstance().awardPoints(attack.getAttackerFactionId(), "prismAttack", pointsToAdd);
            PrismManager.getInstance().remove(prism.getWorldName(), new com.hypixel.hytale.math.vector.Vector3i(prism.getX(), prism.getY(), prism.getZ()));
            World mainWorld = Universe.get().getWorld(prism.getWorldName());
            if (mainWorld != null) {
                mainWorld.execute(() -> PrismService.setBlockToAirStatic(mainWorld, prism.getX(), prism.getY(), prism.getZ()));
            }
        }

        String attackWorldName = attack.getAttackWorldName();
        World attackWorld = Universe.get().getWorld(attackWorldName);
        if (attackWorld == null) {
            return;
        }
        FactionManager.getInstance().getDatabaseManager()
                .loadPrismAttackParticipantsForAttacksAsync(Collections.singletonList(attackId))
                .thenAccept(participants -> {
                    for (PrismAttackParticipantRecord p : participants) {
                        if (p.getReturnWorldName() == null || p.getReturnWorldName().isEmpty()) continue;
                        World returnWorld = Universe.get().getWorld(p.getReturnWorldName());
                        if (returnWorld == null) continue;
                        Transform returnTransform = new Transform(p.getReturnX(), p.getReturnY(), p.getReturnZ());
                        UUID playerUuid = p.getPlayerUuid();
                        attackWorld.execute(() -> {
                            java.util.Collection<PlayerRef> refs = attackWorld.getPlayerRefs();
                            if (refs == null) return;
                            for (PlayerRef pr : refs) {
                                if (pr != null && playerUuid.equals(pr.getUuid())) {
                                    Ref<EntityStore> ref = pr.getReference();
                                    if (ref != null && ref.isValid()) {
                                        Store<EntityStore> store = attackWorld.getEntityStore().getStore();
                                        if (store != null) {
                                            Teleport teleport = Teleport.createForPlayer(returnWorld, returnTransform);
                                            store.addComponent(ref, Teleport.getComponentType(), teleport);
                                        }
                                    }
                                    break;
                                }
                            }
                        });
                    }
                    timerScheduler.schedule(() -> {
                        try {
                            Universe.get().removeWorld(attackWorldName);
                        } catch (Exception e) {
                            ConfigUtil.debug("[PrismAttackService] removeWorld failed: " + e.getMessage());
                        }
                    }, 1, TimeUnit.SECONDS);
                })
                .exceptionally(throwable -> {
                    ConfigUtil.debug("[PrismAttackService] load participants for teleport-back failed: " + throwable.getMessage());
                    try {
                        Universe.get().removeWorld(attackWorldName);
                    } catch (Exception e) {
                        ConfigUtil.debug("[PrismAttackService] removeWorld failed: " + e.getMessage());
                    }
                    return null;
                });
    }

    /**
     * Adds an attack to the cache (call when creating a new attack or on recovery).
     */
    public void addAttackToCache(@Nonnull PrismAttackRecord attack) {
        prismIdToAttack.put(attack.getPrismId(), attack);
        attackIdToRecord.put(attack.getAttackId(), attack);
        log(Level.INFO, "[PrismAttackService] addAttackToCache: attackId=%s attackWorldName=%s status=%s",
                attack.getAttackId(), attack.getAttackWorldName(), attack.getStatus());
    }

    /**
     * Removes an attack from the cache (e.g. when resolved).
     */
    public void removeAttackFromCache(@Nonnull UUID attackId) {
        PrismAttackRecord removed = attackIdToRecord.remove(attackId);
        if (removed != null) {
            prismIdToAttack.remove(removed.getPrismId());
        }
    }

    /**
     * Gets an active attack by prism id. If not in cache, loads from DB and caches.
     */
    @Nullable
    public PrismAttackRecord getAttackByPrismId(@Nullable UUID prismId) {
        if (prismId == null) return null;
        PrismAttackRecord cached = prismIdToAttack.get(prismId);
        if (cached != null) return cached;
        try {
            Optional<PrismAttackRecord> loaded = FactionManager.getInstance().getDatabaseManager()
                    .loadPrismAttackByPrismIdAsync(prismId).get();
            if (loaded.isPresent()) {
                PrismAttackRecord r = loaded.get();
                addAttackToCache(r);
                return r;
            }
        } catch (Exception e) {
            ConfigUtil.debug("[PrismAttackService] getAttackByPrismId failed: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    public PrismAttackRecord getAttackById(@Nullable UUID attackId) {
        return attackIdToRecord.get(attackId);
    }

    /**
     * Returns attacks with status PREPARING (for HUD ticker to show countdown). Caller must not modify the list.
     */
    @Nonnull
    public List<PrismAttackRecord> getActiveAttacksForHud() {
        List<PrismAttackRecord> out = new ArrayList<>();
        for (PrismAttackRecord r : attackIdToRecord.values()) {
            if ("PREPARING".equals(r.getStatus())) out.add(r);
        }
        return out;
    }

    /** Returns true if the world name is a prism attack temp world (e.g. prism-attack-&lt;uuid&gt;). */
    public static boolean isAttackWorldName(@Nullable String worldName) {
        return worldName != null && worldName.startsWith(ATTACK_WORLD_PREFIX);
    }

    /**
     * Returns the PREPARING attack whose attack world name equals the given world name, or null if none.
     * Returns null when status is IN_PROGRESS (combat started, HUD hidden) to avoid log spam.
     */
    @Nullable
    public PrismAttackRecord getActiveAttackByWorldName(@Nullable String worldName) {
        if (worldName == null) return null;
        for (PrismAttackRecord r : attackIdToRecord.values()) {
            if ("PREPARING".equals(r.getStatus()) && worldName.equals(r.getAttackWorldName())) {
                return r;
            }
        }
        // Log when lookup fails for an attack world only if not expected (e.g. IN_PROGRESS = combat started, no log spam)
        if (isAttackWorldName(worldName)) {
            boolean worldInProgress = false;
            for (PrismAttackRecord r : attackIdToRecord.values()) {
                if (worldName.equals(r.getAttackWorldName()) && "IN_PROGRESS".equals(r.getStatus())) {
                    worldInProgress = true;
                    break;
                }
            }
            if (!worldInProgress) {
                StringBuilder cacheSnapshot = new StringBuilder();
                for (PrismAttackRecord r : attackIdToRecord.values()) {
                    if (cacheSnapshot.length() > 0) cacheSnapshot.append("; ");
                    cacheSnapshot.append(r.getAttackWorldName()).append("=").append(r.getStatus());
                }
                log(Level.INFO, "[PrismAttackService] getActiveAttackByWorldName(null): worldName=%s cache=[%s]",
                        worldName, cacheSnapshot.length() > 0 ? cacheSnapshot.toString() : "empty");
            }
        }
        return null;
    }

    private static void log(Level level, String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(level).log(String.format(format, args));
        }
    }

    /**
     * Parses positions JSON: format "x,y,z;x,y,z;..." (semicolon-separated "x,y,z" triples).
     * Returns empty list if json is null or invalid.
     */
    public static List<int[]> parsePositionsJson(@Nullable String json) {
        List<int[]> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        for (String part : json.split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] coords = part.split(",");
            if (coords.length >= 3) {
                try {
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    int z = Integer.parseInt(coords[2].trim());
                    out.add(new int[] { x, y, z });
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    /**
     * Encodes positions as "x,y,z;x,y,z;...".
     */
    public static String encodePositionsJson(@Nonnull List<int[]> positions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            if (i > 0) sb.append(";");
            int[] p = positions.get(i);
            sb.append(p[0]).append(",").append(p[1]).append(",").append(p[2]);
        }
        return sb.toString();
    }

    /**
     * Assigns a participant slot: picks a random free spawn position for the role, inserts into DB, returns spawn [x,y,z].
     * Returns empty if no free slot, or if player is already in this attack (caller should check DB or catch duplicate).
     * If returnWorldName is non-null, the return position is stored for teleporting the player back when the attack ends.
     */
    public Optional<int[]> assignParticipantSlot(@Nonnull UUID attackId, @Nonnull UUID playerUuid, @Nonnull String role,
                                                  @Nullable String returnWorldName, double returnX, double returnY, double returnZ) {
        PrismAttackRecord attack = attackIdToRecord.get(attackId);
        if (attack == null) return Optional.empty();
        String positionsJson = "defender".equalsIgnoreCase(role)
                ? attack.getDefenderPositionsJson()
                : attack.getAttackerPositionsJson();
        List<int[]> positions = parsePositionsJson(positionsJson);
        if (positions.isEmpty()) return Optional.empty();

        List<PrismAttackParticipantRecord> existing;
        try {
            existing = FactionManager.getInstance().getDatabaseManager()
                    .loadPrismAttackParticipantsForAttacksAsync(Collections.singletonList(attackId)).get();
        } catch (Exception e) {
            ConfigUtil.debug("[PrismAttackService] load participants failed: " + e.getMessage());
            return Optional.empty();
        }
        if (existing.stream().anyMatch(p -> p.getPlayerUuid().equals(playerUuid))) {
            return Optional.empty(); // already in attack
        }
        Set<String> taken = new HashSet<>();
        for (PrismAttackParticipantRecord p : existing) {
            taken.add(p.getSpawnX() + "," + p.getSpawnY() + "," + p.getSpawnZ());
        }
        List<int[]> free = new ArrayList<>();
        for (int[] pos : positions) {
            String key = pos[0] + "," + pos[1] + "," + pos[2];
            if (!taken.contains(key)) free.add(pos);
        }
        if (free.isEmpty()) return Optional.empty();
        int[] pos = free.get(new Random().nextInt(free.size()));
        long now = System.currentTimeMillis();
        PrismAttackParticipantRecord participant = new PrismAttackParticipantRecord(
                attackId, playerUuid, role, now, pos[0], pos[1], pos[2],
                returnWorldName, returnX, returnY, returnZ
        );
        FactionManager.getInstance().getDatabaseManager().insertPrismAttackParticipant(participant);
        return Optional.of(pos);
    }

    /**
     * Starts an attack on a prism: if attack already exists, dispatches event (join attacker).
     * Otherwise creates attack world, pastes prefab, inserts attack row, then dispatches event for first attacker.
     * Call from OtherPrismPage when player clicks Attack.
     */
    public void startAttack(@Nonnull UUID prismId, @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ConfigUtil.debug("[PrismAttackService] startAttack entered prismId=" + prismId + " player=" + playerRef.getUuid());

        PrismAttackRecord existing = getAttackByPrismId(prismId);
        if (existing != null) {
            ConfigUtil.debug("[PrismAttackService] startAttack existing attack found for prismId=" + prismId + ", dispatching event");
            PrismAttackEventDispatcher.getInstance().dispatch(
                new PlayerJoinPrismAttackEvent(playerRef, ref, store, prismId, "attacker"));
            return;
        }

        PrismRecord prism = PrismManager.getInstance().getById(prismId);
        if (prism == null) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: prism == null for prismId=" + prismId);
            return;
        }
        if (!"harvesting".equals(prism.getStatus())) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: prism status is '" + prism.getStatus() + "' (not harvesting) prismId=" + prismId);
            return;
        }

        com.factioncore.faction.FactionInfo playerFaction = FactionManager.getInstance().getFactionFromPlayer(playerRef.getUuid());
        if (playerFaction == null) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: playerFaction == null prismId=" + prismId);
            return;
        }

        Main main = Main.get();
        if (main == null || main.getPrismAttacksConfig() == null) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: main or getPrismAttacksConfig() == null prismId=" + prismId);
            return;
        }
        PrismAttacksConfig config = main.getPrismAttacksConfig().get();
        if (config == null || config.getPrefabs().isEmpty()) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: config == null or prefabs empty prismId=" + prismId);
            return;
        }

        PrismManager.getInstance().updateStatus(prismId, "attacked");

        World playerWorld = store.getExternalData().getWorld();
        if (playerWorld == null) {
            ConfigUtil.debug("[PrismAttackService] startAttack early return: playerWorld == null");
            PrismManager.getInstance().updateStatus(prismId, "harvesting");
            return;
        }

        UUID attackId = UUID.randomUUID();
        String attackWorldName = ATTACK_WORLD_PREFIX + prismId.toString();
        long nowMs = System.currentTimeMillis();
        int joinWindowSeconds = config.getAttackerTimeSeconds();
        if (joinWindowSeconds <= 0) joinWindowSeconds = 60;
        int destructionSeconds = config.getTimeBeforeAutoDestruction();
        if (destructionSeconds <= 0) destructionSeconds = 600;
        long endsAtMs = nowMs + (joinWindowSeconds * 1000L);
        long destructionAtMs = nowMs + (destructionSeconds * 1000L);

        List<PrismAttacksConfig.PrefabEntry> prefabs = config.getPrefabs();
        PrismAttacksConfig.PrefabEntry chosen = prefabs.get(new Random().nextInt(prefabs.size()));
        String prefabId = chosen.getPrefabId();
        String attackersBlockId = chosen.getAttackersBlockId();
        String defendersBlockId = chosen.getDefendersBlockId();

        Path universePath = Universe.get().getPath();
        Path savePath = universePath.resolve("worlds").resolve(attackWorldName);
        try {
            Files.createDirectories(savePath);
        } catch (Exception e) {
            ConfigUtil.debug("[PrismAttackService] create dir failed: " + e.getMessage());
            PrismManager.getInstance().updateStatus(prismId, "harvesting");
            return;
        }

        ConfigUtil.debug("[PrismAttackService] startAttack starting async world creation prismId=" + prismId + " attackWorldName=" + attackWorldName);

        World existingWorld = Universe.get().getWorld(attackWorldName);
        if (existingWorld != null) {
            PrismAttackRecord cachedAttack = getAttackByPrismId(prismId);
            if (cachedAttack != null) {
                PrismAttackEventDispatcher.getInstance().dispatch(
                    new PlayerJoinPrismAttackEvent(playerRef, ref, store, prismId, "attacker"));
                return;
            }
            try {
                Universe.get().removeWorld(attackWorldName);
            } catch (Exception ignored) {
                ConfigUtil.debug("[PrismAttackService] startAttack: could not remove existing world " + attackWorldName);
            }
        }

        WorldConfig worldConfig = buildAttackWorldConfig();
        CompletableFuture<World> worldFuture = Universe.get().makeWorld(attackWorldName, savePath, worldConfig, true);

        worldFuture
            .thenCompose(world -> waitForChunkAt(world, 0, 0))
            .thenAccept(attackWorld -> {
                BlockSelection selection = PrefabStore.get().getServerPrefab(prefabId);
                if (selection == null) {
                    ConfigUtil.debug("[PrismAttackService] prefab not found: " + prefabId);
                    PrismManager.getInstance().updateStatus(prismId, "harvesting");
                    try { Universe.get().removeWorld(attackWorldName); } catch (Exception ignored) {}
                    return;
                }
                List<int[]> attackerPositions = new ArrayList<>();
                List<int[]> defenderPositions = new ArrayList<>();
                collectSpawnPositionsFromPrefab(selection, attackersBlockId, defendersBlockId, attackerPositions, defenderPositions);
                if (attackerPositions.isEmpty()) {
                    attackerPositions = Arrays.asList(new int[] { 0, 1, 0 }, new int[] { 1, 1, 0 });
                }
                if (defenderPositions.isEmpty()) {
                    defenderPositions = Arrays.asList(new int[] { 0, 2, 0 }, new int[] { 1, 2, 0 });
                }
                try {
                    pastePrefabAtOrigin(attackWorld, selection);
                } catch (Exception e) {
                    ConfigUtil.debug("[PrismAttackService] prefab paste failed: " + e.getMessage());
                    PrismManager.getInstance().updateStatus(prismId, "harvesting");
                    try { Universe.get().removeWorld(attackWorldName); } catch (Exception ignored) {}
                    return;
                }
                String attackerPositionsJson = encodePositionsJson(attackerPositions);
                String defenderPositionsJson = encodePositionsJson(defenderPositions);

                PrismAttackRecord attack = new PrismAttackRecord(attackId, prismId, attackWorldName,
                        prism.getFactionId(), playerFaction.getId(), "PENDING_START", nowMs, endsAtMs, destructionAtMs,
                        prefabId, attackersBlockId, defendersBlockId, defenderPositionsJson, attackerPositionsJson);
                FactionManager.getInstance().getDatabaseManager().insertPrismAttack(attack);
                addAttackToCache(attack);
                // Combat start and destruction are scheduled when handler calls updateAttackStatusToActive (first attacker)
                // Dispatch on the player's world thread so the handler and Teleport run on the correct thread.
                ConfigUtil.debug("[PrismAttackService] startAttack attack created, dispatching event prismId=" + prismId + " attackId=" + attackId);
                PlayerJoinPrismAttackEvent joinEvent = new PlayerJoinPrismAttackEvent(playerRef, ref, store, prismId, "attacker");
                playerWorld.execute(() -> PrismAttackEventDispatcher.getInstance().dispatch(joinEvent));
            })
            .exceptionally(throwable -> {
                ConfigUtil.debug("[PrismAttackService] startAttack failed prismId=" + prismId + ": " + throwable.getMessage());
                PrismManager.getInstance().updateStatus(prismId, "harvesting");
                return null;
            });
    }

    private static WorldConfig buildAttackWorldConfig() {
        WorldConfig config = new WorldConfig();
        config.setUuid(UUID.randomUUID());
        config.setDisplayName("Prism Attack");
        config.setSeed(System.currentTimeMillis());
        config.setDeleteOnRemove(true);
        com.hypixel.hytale.codec.builder.BuilderCodec<?> flatCodec = (com.hypixel.hytale.codec.builder.BuilderCodec<?>) IWorldGenProvider.CODEC.getCodecFor("Flat");
        if (flatCodec != null) {
            config.setWorldGenProvider((IWorldGenProvider) flatCodec.getDefaultValue());
        }
        Box2D region = new Box2D(new Vector2d(0, 0), new Vector2d(96, 96));
        config.getChunkConfig().setPregenerateRegion(region);
        config.getChunkConfig().setKeepLoadedRegion(region);
        config.setSpawnProvider(new GlobalSpawnProvider(new Transform(0, 1, 0)));
        return config;
    }

    private static CompletableFuture<World> waitForChunkAt(World world, int blockX, int blockZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        return world.getChunkStore().getChunkReferenceAsync(chunkIndex).thenApply(ref -> world);
    }

    /**
     * Resolves a BlockType to the numeric block ID used in prefab block data (e.g. block.blockId()).
     * Tries getBlockId() or getAssetIndex() via reflection; otherwise iterates the asset map to find the int key. Returns -1 if not found.
     */
    private static int resolveBlockTypeToNumericId(@Nonnull Object assetMap, @Nonnull BlockType blockType) {
        try {
            for (String methodName : new String[] { "getBlockId", "getAssetIndex" }) {
                Method m = BlockType.class.getMethod(methodName);
                Object result = m.invoke(blockType);
                if (result instanceof Number) return ((Number) result).intValue();
            }
        } catch (Exception ignored) {
        }
        try {
            Method getKeys = assetMap.getClass().getMethod("getKeys");
            Iterable<?> keys = (Iterable<?>) getKeys.invoke(assetMap);
            Method getAsset = assetMap.getClass().getMethod("getAsset", Object.class);
            for (Object key : keys) {
                if (key instanceof Number && blockType.equals(getAsset.invoke(assetMap, key))) {
                    return ((Number) key).intValue();
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Scans the prefab selection for blocks matching attackersBlockId and defendersBlockId (config strings).
     * Fills attackerOut and defenderOut with [x,y,z] coordinates (local = world when pasted at origin).
     * If a block ID cannot be resolved or no blocks match, the corresponding list is left empty (caller applies fallback).
     */
    private static void collectSpawnPositionsFromPrefab(@Nonnull BlockSelection selection,
                                                        @Nullable String attackersBlockId,
                                                        @Nullable String defendersBlockId,
                                                        @Nonnull List<int[]> attackerOut,
                                                        @Nonnull List<int[]> defenderOut) {
        int attackerBid = -1;
        int defenderBid = -1;
        if (attackersBlockId != null && !attackersBlockId.isEmpty()) {
            BlockType at = BlockType.getAssetMap().getAsset(attackersBlockId);
            if (at != null) attackerBid = resolveBlockTypeToNumericId(BlockType.getAssetMap(), at);
            else ConfigUtil.debug("[PrismAttackService] attackersBlockId not found: " + attackersBlockId);
        }
        if (defendersBlockId != null && !defendersBlockId.isEmpty()) {
            BlockType dt = BlockType.getAssetMap().getAsset(defendersBlockId);
            if (dt != null) defenderBid = resolveBlockTypeToNumericId(BlockType.getAssetMap(), dt);
            else ConfigUtil.debug("[PrismAttackService] defendersBlockId not found: " + defendersBlockId);
        }
        final int finalAttackerBid = attackerBid;
        final int finalDefenderBid = defenderBid;
        selection.forEachBlock((x, y, z, block) -> {
            int bid = block.blockId();
            if (bid == finalAttackerBid) attackerOut.add(new int[] { x, y, z });
            if (bid == finalDefenderBid) defenderOut.add(new int[] { x, y, z });
        });
    }

    private static void pastePrefabAtOrigin(World world, String prefabKey) {
        BlockSelection selection = PrefabStore.get().getServerPrefab(prefabKey);
        if (selection == null) {
            throw new IllegalStateException("Attack prefab not found: " + prefabKey);
        }
        selection.placeNoReturn(world, new Vector3i(0, 0, 0), world.getEntityStore().getStore());
    }

    private static void pastePrefabAtOrigin(World world, BlockSelection selection) {
        selection.placeNoReturn(world, new Vector3i(0, 0, 0), world.getEntityStore().getStore());
    }
}
