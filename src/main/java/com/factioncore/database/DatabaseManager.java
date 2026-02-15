package com.factioncore.database;

import com.factioncore.claim.ChunkInfo;
import com.factioncore.claim.tracking.ModifiedTracking;
import com.factioncore.diplomacy.AllySettings;
import com.factioncore.diplomacy.PendingAllyRequest;
import com.factioncore.faction.FactionInfo;
import com.factioncore.invite.PendingInvite;
import com.factioncore.prism.PrismRecord;
import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Manages SQLite database operations for FactionCore.
 * 
 * CRITICAL: This class is ONLY for persistence (save periodically, load on startup).
 * ALL runtime queries use in-memory data structures in FactionManager.
 * DatabaseManager should NEVER be called during gameplay for reads.
 * 
 * Design Pattern: Single Writer Thread with Queue
 * - All writes are queued and processed by a single writer thread
 * - Prevents SQLite lock contention
 * - Implements retry with exponential backoff for error recovery
 */
public class DatabaseManager {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final HytaleLogger logger;
    private final Connection connection;
    private final ExecutorService writerExecutor;
    private final BlockingQueue<WriteOperation> writeQueue;
    private final AtomicBoolean writerRunning;
    private final ExecutorService loadExecutor; // For async loading
    
    /**
     * Creates a new DatabaseManager instance.
     * 
     * @param logger The logger to use
     */
    public DatabaseManager(HytaleLogger logger) {
        this.logger = logger;
        this.writeQueue = new LinkedBlockingQueue<>();
        this.writerRunning = new AtomicBoolean(true);
        
        // Initialize SQLite connection
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.at(Level.SEVERE).log("SQLite JDBC driver not found: " + e.getMessage());
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
        
        // Create database path (basePath is e.g. mods/FactionCore, passed from Main)
        Path basePath = com.factioncore.Main.get().getPluginDataPath();
        try {
            Files.createDirectories(basePath);
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Failed to create FactionCore directory: " + e.getMessage());
        }
        
        Path dbPath = basePath.resolve("factioncore.db");
        
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
            }
            createTables();
        } catch (SQLException e) {
            logger.at(Level.SEVERE).log("Error initializing database: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
        
        // Create single writer thread
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FactionCore-DatabaseWriter");
            t.setDaemon(true);
            return t;
        });
        
        // Create executor for async loading
        this.loadExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FactionCore-DatabaseLoader");
            t.setDaemon(true);
            return t;
        });
        
        // Start writer thread
        startWriterThread();
    }
    
    /**
     * Creates all required database tables.
     */
    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Factions table
            statement.execute("CREATE TABLE IF NOT EXISTS factions (" +
                    "id TEXT PRIMARY KEY," +
                    "owner TEXT," +
                    "name TEXT," +
                    "description TEXT," +
                    "color INTEGER," +
                    "created_date TEXT," +
                    "modified_date TEXT" +
                    ")");
            
            // Faction members table
            statement.execute("CREATE TABLE IF NOT EXISTS faction_members (" +
                    "faction_id TEXT," +
                    "member_uuid TEXT," +
                    "PRIMARY KEY (faction_id, member_uuid)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Faction generals table
            statement.execute("CREATE TABLE IF NOT EXISTS faction_generals (" +
                    "faction_id TEXT," +
                    "general_uuid TEXT," +
                    "PRIMARY KEY (faction_id, general_uuid)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Faction permissions table
            statement.execute("CREATE TABLE IF NOT EXISTS faction_permissions (" +
                    "faction_id TEXT," +
                    "member_uuid TEXT," +
                    "permission TEXT," +
                    "PRIMARY KEY (faction_id, member_uuid, permission)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Claims table
            statement.execute("CREATE TABLE IF NOT EXISTS claims (" +
                    "dimension TEXT," +
                    "chunkX INTEGER," +
                    "chunkZ INTEGER," +
                    "faction_owner TEXT," +
                    "created_user_uuid TEXT," +
                    "created_user_name TEXT," +
                    "created_date TEXT," +
                    "PRIMARY KEY (dimension, chunkX, chunkZ)" +
                    ")");
            
            // Pending invites table
            statement.execute("CREATE TABLE IF NOT EXISTS pending_invites (" +
                    "player_uuid TEXT," +
                    "faction_id TEXT," +
                    "inviter_name TEXT," +
                    "invite_date TEXT," +
                    "PRIMARY KEY (player_uuid, faction_id)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Faction allies table (symmetric relationship)
            statement.execute("CREATE TABLE IF NOT EXISTS faction_allies (" +
                    "faction_id TEXT," +
                    "ally_faction_id TEXT," +
                    "PRIMARY KEY (faction_id, ally_faction_id)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (ally_faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Faction enemies table (one-way)
            statement.execute("CREATE TABLE IF NOT EXISTS faction_enemies (" +
                    "faction_id TEXT," +
                    "enemy_faction_id TEXT," +
                    "PRIMARY KEY (faction_id, enemy_faction_id)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (enemy_faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Pending ally requests table
            statement.execute("CREATE TABLE IF NOT EXISTS pending_ally_requests (" +
                    "requester_faction_id TEXT," +
                    "target_faction_id TEXT," +
                    "created_date TEXT," +
                    "PRIMARY KEY (requester_faction_id, target_faction_id)," +
                    "FOREIGN KEY (requester_faction_id) REFERENCES factions(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (target_faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Ally settings table (per faction + ally pair)
            statement.execute("CREATE TABLE IF NOT EXISTS ally_settings (" +
                    "faction_id TEXT," +
                    "ally_faction_id TEXT," +
                    "share_markers INTEGER," +
                    "allow_interactions INTEGER," +
                    "PRIMARY KEY (faction_id, ally_faction_id)," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (ally_faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Prisms table (one prism per position; one per chunk enforced in PrismManager).
            // uuid = prism id; created_at = epoch ms; score = 1 point per minute computed on interaction.
            statement.execute("CREATE TABLE IF NOT EXISTS prisms (" +
                    "world_name TEXT," +
                    "x INTEGER," +
                    "y INTEGER," +
                    "z INTEGER," +
                    "uuid TEXT," +
                    "faction_id TEXT," +
                    "player_uuid TEXT," +
                    "created_at INTEGER," +
                    "time_alive INTEGER DEFAULT 0," +
                    "PRIMARY KEY (world_name, x, y, z)" +
                    ")");
            
            // Faction points table (FP total per faction)
            statement.execute("CREATE TABLE IF NOT EXISTS faction_points (" +
                    "faction_id TEXT PRIMARY KEY," +
                    "points INTEGER NOT NULL DEFAULT 0," +
                    "FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE" +
                    ")");
            
            // Create indexes for performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_faction_id ON faction_members(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_permissions_faction_id ON faction_permissions(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_claims_dimension ON claims(dimension)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pending_invites_player_uuid ON pending_invites(player_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_allies_faction_id ON faction_allies(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_enemies_faction_id ON faction_enemies(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pending_ally_requests_requester ON pending_ally_requests(requester_faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pending_ally_requests_target ON pending_ally_requests(target_faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_prisms_world ON prisms(world_name)");

            migratePrismColumns(statement);
            migrateAllySettingsColumns(statement);
            migrateFactionHomeColumns(statement);
        }
    }

    /**
     * Adds uuid and created_at to prisms table if missing (migration for existing DBs).
     */
    private void migratePrismColumns(Statement statement) throws SQLException {
        for (String colDef : new String[] { "uuid TEXT", "created_at INTEGER" }) {
            try {
                statement.execute("ALTER TABLE prisms ADD COLUMN " + colDef);
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                    throw e;
                }
            }
        }
    }

    /**
     * Adds faction home columns to factions table if missing (migration for existing DBs).
     */
    private void migrateFactionHomeColumns(Statement statement) throws SQLException {
        String[] columns = {
            "home_dimension TEXT",
            "home_x REAL",
            "home_y REAL",
            "home_z REAL"
        };
        for (String colDef : columns) {
            try {
                statement.execute("ALTER TABLE factions ADD COLUMN " + colDef);
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                    throw e;
                }
            }
        }
    }

    /**
     * Adds per-interaction columns to ally_settings if missing (migration for existing DBs).
     */
    private void migrateAllySettingsColumns(Statement statement) throws SQLException {
        String[] columns = {
            "allow_open_chest INTEGER DEFAULT 0",
            "allow_open_door INTEGER DEFAULT 0",
            "allow_open_bench INTEGER DEFAULT 0",
            "allow_use_chair INTEGER DEFAULT 0",
            "allow_use_portal INTEGER DEFAULT 0",
            "allow_place_block INTEGER DEFAULT 0",
            "allow_break_block INTEGER DEFAULT 0"
        };
        for (String colDef : columns) {
            String colName = colDef.split(" ")[0];
            try {
                statement.execute("ALTER TABLE ally_settings ADD COLUMN " + colName + " INTEGER DEFAULT 0");
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                    throw e;
                }
                // Column already exists, ignore
            }
        }
    }

    /**
     * Starts the writer thread that processes queued write operations.
     */
    private void startWriterThread() {
        writerExecutor.submit(() -> {
            while (writerRunning.get()) {
                try {
                    WriteOperation operation = writeQueue.take(); // Blocks until operation available
                    executeWithRetry(() -> {
                        try {
                            operation.execute(connection);
                        } catch (SQLException e) {
                            throw new RuntimeException("SQL error in " + operation.getName(), e);
                        }
                    }, operation.getName());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.at(Level.SEVERE).log("Error in writer thread: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Executes an operation with retry and exponential backoff.
     */
    private void executeWithRetry(Runnable operation, String operationName) {
        int maxRetries = 5;
        long baseDelayMs = 100;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                operation.run();
                return; // Success
            } catch (RuntimeException e) {
                // Check if the cause is a retryable SQLException
                Throwable cause = e.getCause();
                if (cause instanceof SQLException) {
                    SQLException sqlEx = (SQLException) cause;
                    if (isRetryableError(sqlEx) && attempt < maxRetries - 1) {
                        long delay = baseDelayMs * (1L << attempt); // Exponential backoff
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry", ie);
                        }
                        logger.at(Level.WARNING).log("Retrying database operation " + operationName + " (attempt " + (attempt + 2) + "/" + maxRetries + ")");
                        continue;
                    }
                }
                logger.at(Level.SEVERE).log("Database operation failed after retries: " + operationName + " - " + e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.at(Level.SEVERE).log("Non-retryable error in database operation: " + operationName + " - " + e.getMessage());
                throw new RuntimeException("Database operation failed: " + operationName, e);
            }
        }
    }
    
    /**
     * Checks if an SQLException is retryable.
     */
    private boolean isRetryableError(SQLException e) {
        String message = e.getMessage();
        
        // SQLite error codes
        return message != null && (
            message.contains("SQLITE_BUSY") ||
            message.contains("SQLITE_LOCKED") ||
            message.contains("database is locked") ||
            message.contains("table is locked")
        );
    }
    
    // ========== Async Loading Methods (ONLY called on server startup) ==========
    
    /**
     * Loads all factions from the database asynchronously.
     * 
     * @return CompletableFuture with map of faction ID to FactionInfo
     */
    public CompletableFuture<Map<UUID, FactionInfo>> loadFactionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, FactionInfo> factions = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM factions")) {
                
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    FactionInfo faction = new FactionInfo(
                            id,
                            UUID.fromString(rs.getString("owner")),
                            rs.getString("name"),
                            rs.getString("description"),
                            new UUID[0], // Members loaded separately
                            rs.getInt("color")
                    );
                    
                    // Set dates
                    String createdDateStr = rs.getString("created_date");
                    if (createdDateStr != null) {
                        faction.setCreatedDate(LocalDateTime.parse(createdDateStr, DATE_FORMATTER));
                    }
                    String modifiedDateStr = rs.getString("modified_date");
                    if (modifiedDateStr != null) {
                        faction.setModifiedDate(LocalDateTime.parse(modifiedDateStr, DATE_FORMATTER));
                    }
                    
                    // Load faction home (columns added by migrateFactionHomeColumns)
                    String homeDim = rs.getString("home_dimension");
                    if (homeDim != null && !homeDim.isEmpty()) {
                        faction.setHome(homeDim,
                            rs.getDouble("home_x"),
                            rs.getDouble("home_y"),
                            rs.getDouble("home_z"));
                    }
                    
                    // Load members
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT member_uuid FROM faction_members WHERE faction_id = ?")) {
                        ps.setString(1, id.toString());
                        try (ResultSet rsMembers = ps.executeQuery()) {
                            while (rsMembers.next()) {
                                faction.addMember(UUID.fromString(rsMembers.getString("member_uuid")));
                            }
                        }
                    }
                    
                    // Load generals
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT general_uuid FROM faction_generals WHERE faction_id = ?")) {
                        ps.setString(1, id.toString());
                        try (ResultSet rsGenerals = ps.executeQuery()) {
                            while (rsGenerals.next()) {
                                faction.addGeneral(UUID.fromString(rsGenerals.getString("general_uuid")));
                            }
                        }
                    }
                    
                    // Member permissions are loaded via loadMemberPermissionsAsync into FactionManager.
                    factions.put(id, faction);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading factions: " + e.getMessage());
                throw new RuntimeException("Failed to load factions", e);
            }
            return factions;
        }, loadExecutor);
    }
    
    /**
     * Loads all claims from the database asynchronously.
     * 
     * @return CompletableFuture with map of dimension -> (chunk key -> ChunkInfo)
     */
    public CompletableFuture<Map<String, Map<String, ChunkInfo>>> loadClaimsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, ChunkInfo>> claims = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM claims")) {
                
                while (rs.next()) {
                    String dimension = rs.getString("dimension");
                    ChunkInfo chunk = new ChunkInfo(
                            UUID.fromString(rs.getString("faction_owner")),
                            rs.getInt("chunkX"),
                            rs.getInt("chunkZ")
                    );
                    
                    // Set created tracking
                    ModifiedTracking tracking = new ModifiedTracking(
                            UUID.fromString(rs.getString("created_user_uuid")),
                            rs.getString("created_user_name"),
                            rs.getString("created_date")
                    );
                    chunk.setCreatedTracked(tracking);
                    
                    String chunkKey = ChunkInfo.formatCoordinates(chunk.getChunkX(), chunk.getChunkZ());
                    claims.computeIfAbsent(dimension, k -> new HashMap<>()).put(chunkKey, chunk);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading claims: " + e.getMessage());
                throw new RuntimeException("Failed to load claims", e);
            }
            return claims;
        }, loadExecutor);
    }
    
    /**
     * Loads all prisms from the database asynchronously (startup only).
     * If created_at is null (legacy row), derives it from time_alive so score stays correct.
     *
     * @return CompletableFuture with list of PrismRecord
     */
    public CompletableFuture<List<PrismRecord>> loadPrismsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<PrismRecord> list = new ArrayList<>();
            long nowMs = System.currentTimeMillis();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT world_name, x, y, z, uuid, faction_id, player_uuid, created_at, time_alive FROM prisms")) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    UUID id = uuidStr != null && !uuidStr.isEmpty()
                            ? UUID.fromString(uuidStr)
                            : UUID.randomUUID();
                    long createdMs;
                    long createdAt = rs.getLong("created_at");
                    if (rs.wasNull() || createdAt <= 0) {
                        int timeAlive = rs.getInt("time_alive");
                        createdMs = nowMs - (timeAlive * 60_000L);
                    } else {
                        createdMs = createdAt;
                    }
                    list.add(new PrismRecord(
                            rs.getString("world_name"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            id,
                            UUID.fromString(rs.getString("faction_id")),
                            UUID.fromString(rs.getString("player_uuid")),
                            createdMs
                    ));
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading prisms: " + e.getMessage());
                throw new RuntimeException("Failed to load prisms", e);
            }
            return list;
        }, loadExecutor);
    }
    
    /**
     * Loads all faction points from the database asynchronously (startup only).
     *
     * @return CompletableFuture with map of factionId -> total FP
     */
    public CompletableFuture<Map<UUID, Integer>> loadFactionPointsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Integer> map = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT faction_id, points FROM faction_points")) {
                while (rs.next()) {
                    map.put(UUID.fromString(rs.getString("faction_id")), rs.getInt("points"));
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading faction points: " + e.getMessage());
                throw new RuntimeException("Failed to load faction points", e);
            }
            return map;
        }, loadExecutor);
    }
    
    /**
     * Loads all pending invites from the database asynchronously.
     * 
     * @return CompletableFuture with map of player UUID -> list of pending invites
     */
    public CompletableFuture<Map<UUID, List<PendingInvite>>> loadPendingInvitesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<PendingInvite>> invites = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM pending_invites")) {
                
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    PendingInvite invite = new PendingInvite(
                            playerUuid,
                            UUID.fromString(rs.getString("faction_id")),
                            rs.getString("inviter_name"),
                            rs.getString("invite_date")
                    );
                    invites.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(invite);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading pending invites: " + e.getMessage());
                throw new RuntimeException("Failed to load pending invites", e);
            }
            return invites;
        }, loadExecutor);
    }
    
    /**
     * Loads faction allies from the database asynchronously.
     * @return CompletableFuture with map of factionId -> set of ally faction IDs
     */
    public CompletableFuture<Map<UUID, Set<UUID>>> loadFactionAlliesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Set<UUID>> allies = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM faction_allies")) {
                while (rs.next()) {
                    UUID factionId = UUID.fromString(rs.getString("faction_id"));
                    UUID allyFactionId = UUID.fromString(rs.getString("ally_faction_id"));
                    allies.computeIfAbsent(factionId, k -> new HashSet<>()).add(allyFactionId);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading faction allies: " + e.getMessage());
                throw new RuntimeException("Failed to load faction allies", e);
            }
            return allies;
        }, loadExecutor);
    }
    
    /**
     * Loads faction enemies from the database asynchronously.
     * @return CompletableFuture with map of factionId -> set of enemy faction IDs
     */
    public CompletableFuture<Map<UUID, Set<UUID>>> loadFactionEnemiesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Set<UUID>> enemies = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM faction_enemies")) {
                while (rs.next()) {
                    UUID factionId = UUID.fromString(rs.getString("faction_id"));
                    UUID enemyFactionId = UUID.fromString(rs.getString("enemy_faction_id"));
                    enemies.computeIfAbsent(factionId, k -> new HashSet<>()).add(enemyFactionId);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading faction enemies: " + e.getMessage());
                throw new RuntimeException("Failed to load faction enemies", e);
            }
            return enemies;
        }, loadExecutor);
    }
    
    /**
     * Loads pending ally requests from the database asynchronously.
     * @return CompletableFuture with list of PendingAllyRequest
     */
    public CompletableFuture<List<PendingAllyRequest>> loadPendingAllyRequestsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<PendingAllyRequest> list = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM pending_ally_requests")) {
                while (rs.next()) {
                    PendingAllyRequest req = new PendingAllyRequest(
                            UUID.fromString(rs.getString("requester_faction_id")),
                            UUID.fromString(rs.getString("target_faction_id")),
                            rs.getString("created_date")
                    );
                    list.add(req);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading pending ally requests: " + e.getMessage());
                throw new RuntimeException("Failed to load pending ally requests", e);
            }
            return list;
        }, loadExecutor);
    }
    
    /**
     * Loads ally settings from the database asynchronously.
     * @return CompletableFuture with map keyed by "factionId_allyFactionId" -> AllySettings
     */
    public CompletableFuture<Map<String, AllySettings>> loadAllySettingsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, AllySettings> map = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM ally_settings")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                java.util.Set<String> columns = new HashSet<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnName(i).toLowerCase());
                }
                while (rs.next()) {
                    UUID factionId = UUID.fromString(rs.getString("faction_id"));
                    UUID allyFactionId = UUID.fromString(rs.getString("ally_faction_id"));
                    boolean shareMarkers = rs.getInt("share_markers") != 0;
                    boolean allowInteractions = rs.getInt("allow_interactions") != 0;
                    boolean allowOpenChest = columns.contains("allow_open_chest") && rs.getInt("allow_open_chest") != 0;
                    boolean allowOpenDoor = columns.contains("allow_open_door") && rs.getInt("allow_open_door") != 0;
                    boolean allowOpenBench = columns.contains("allow_open_bench") && rs.getInt("allow_open_bench") != 0;
                    boolean allowUseChair = columns.contains("allow_use_chair") && rs.getInt("allow_use_chair") != 0;
                    boolean allowUsePortal = columns.contains("allow_use_portal") && rs.getInt("allow_use_portal") != 0;
                    boolean allowPlaceBlock = columns.contains("allow_place_block") && rs.getInt("allow_place_block") != 0;
                    boolean allowBreakBlock = columns.contains("allow_break_block") && rs.getInt("allow_break_block") != 0;
                    String key = factionId.toString() + "_" + allyFactionId.toString();
                    map.put(key, new AllySettings(shareMarkers, allowInteractions,
                            allowOpenChest, allowOpenDoor, allowOpenBench, allowUseChair, allowUsePortal,
                            allowPlaceBlock, allowBreakBlock));
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading ally settings: " + e.getMessage());
                throw new RuntimeException("Failed to load ally settings", e);
            }
            return map;
        }, loadExecutor);
    }
    
    /**
     * Loads member permissions from the database asynchronously.
     * @return CompletableFuture with map keyed by "factionId_memberUuid" -> Set of permission strings
     */
    public CompletableFuture<Map<String, Set<String>>> loadMemberPermissionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Set<String>> map = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT faction_id, member_uuid, permission FROM faction_permissions")) {
                while (rs.next()) {
                    String factionId = rs.getString("faction_id");
                    String memberUuid = rs.getString("member_uuid");
                    String permission = rs.getString("permission");
                    String key = factionId + "_" + memberUuid;
                    map.computeIfAbsent(key, k -> new HashSet<>()).add(permission);
                }
            } catch (SQLException e) {
                logger.at(Level.SEVERE).log("Error loading member permissions: " + e.getMessage());
                throw new RuntimeException("Failed to load member permissions", e);
            }
            return map;
        }, loadExecutor);
    }
    
    // ========== Write Operations (Queued) ==========
    
    /**
     * Queues a faction save operation.
     */
    public void saveFaction(FactionInfo faction) {
        writeQueue.offer(new WriteOperation("saveFaction", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO factions (id, owner, name, description, color, created_date, modified_date, home_dimension, home_x, home_y, home_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, faction.getId().toString());
                ps.setString(2, faction.getOwner().toString());
                ps.setString(3, faction.getName());
                ps.setString(4, faction.getDescription());
                ps.setInt(5, faction.getColor());
                ps.setString(6, faction.getCreatedDate() != null ? 
                    faction.getCreatedDate().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));
                ps.setString(7, faction.getModifiedDate() != null ? 
                    faction.getModifiedDate().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));
                if (faction.hasHome()) {
                    ps.setString(8, faction.getHomeDimension());
                    ps.setDouble(9, faction.getHomeX());
                    ps.setDouble(10, faction.getHomeY());
                    ps.setDouble(11, faction.getHomeZ());
                } else {
                    ps.setString(8, null);
                    ps.setDouble(9, 0);
                    ps.setDouble(10, 0);
                    ps.setDouble(11, 0);
                }
                ps.executeUpdate();
            }
            
            // Save members
            try (PreparedStatement deleteMembers = conn.prepareStatement(
                    "DELETE FROM faction_members WHERE faction_id = ?")) {
                deleteMembers.setString(1, faction.getId().toString());
                deleteMembers.executeUpdate();
            }
            try (PreparedStatement insertMember = conn.prepareStatement(
                    "INSERT INTO faction_members (faction_id, member_uuid) VALUES (?, ?)")) {
                for (UUID member : faction.getMembers()) {
                    insertMember.setString(1, faction.getId().toString());
                    insertMember.setString(2, member.toString());
                    insertMember.addBatch();
                }
                insertMember.executeBatch();
            }
            
            // Save generals
            try (PreparedStatement deleteGenerals = conn.prepareStatement(
                    "DELETE FROM faction_generals WHERE faction_id = ?")) {
                deleteGenerals.setString(1, faction.getId().toString());
                deleteGenerals.executeUpdate();
            }
            try (PreparedStatement insertGeneral = conn.prepareStatement(
                    "INSERT INTO faction_generals (faction_id, general_uuid) VALUES (?, ?)")) {
                for (UUID general : faction.getGenerals()) {
                    insertGeneral.setString(1, faction.getId().toString());
                    insertGeneral.setString(2, general.toString());
                    insertGeneral.addBatch();
                }
                insertGeneral.executeBatch();
            }
            // Member permissions are persisted via saveAllMemberPermissions (FactionManager map).
        }));
    }
    
    /**
     * Queues a faction delete operation.
     */
    public void deleteFaction(UUID factionId) {
        writeQueue.offer(new WriteOperation("deleteFaction", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM factions WHERE id = ?")) {
                ps.setString(1, factionId.toString());
                ps.executeUpdate();
            }
            // Cascading deletes handle related tables
        }));
    }
    
    /**
     * Queues a claim save operation.
     */
    public void saveClaim(String dimension, ChunkInfo chunk) {
        writeQueue.offer(new WriteOperation("saveClaim", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO claims (dimension, chunkX, chunkZ, faction_owner, created_user_uuid, created_user_name, created_date) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, dimension);
                ps.setInt(2, chunk.getChunkX());
                ps.setInt(3, chunk.getChunkZ());
                ps.setString(4, chunk.getFactionOwner().toString());
                ModifiedTracking tracking = chunk.getCreatedTracked();
                if (tracking != null) {
                    ps.setString(5, tracking.getUserUuid().toString());
                    ps.setString(6, tracking.getUserName());
                    ps.setString(7, tracking.getDate());
                } else {
                    ps.setString(5, UUID.randomUUID().toString());
                    ps.setString(6, "-");
                    ps.setString(7, LocalDateTime.now().format(DATE_FORMATTER));
                }
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a claim delete operation.
     */
    public void deleteClaim(String dimension, int chunkX, int chunkZ) {
        writeQueue.offer(new WriteOperation("deleteClaim", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM claims WHERE dimension = ? AND chunkX = ? AND chunkZ = ?")) {
                ps.setString(1, dimension);
                ps.setInt(2, chunkX);
                ps.setInt(3, chunkZ);
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a prism insert or replace. Score is computed from created_at on interaction (1 point per minute).
     */
    public void insertOrReplacePrism(String worldName, int x, int y, int z,
                                     UUID prismId, UUID factionId, UUID playerUuid, long createdAtMs) {
        writeQueue.offer(new WriteOperation("insertOrReplacePrism", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO prisms (world_name, x, y, z, uuid, faction_id, player_uuid, created_at, time_alive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
                ps.setString(1, worldName);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setString(5, prismId.toString());
                ps.setString(6, factionId.toString());
                ps.setString(7, playerUuid.toString());
                ps.setLong(8, createdAtMs);
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a prism delete by position.
     */
    public void deletePrism(String worldName, int x, int y, int z) {
        writeQueue.offer(new WriteOperation("deletePrism", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM prisms WHERE world_name = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, worldName);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a faction points save (REPLACE into faction_points).
     */
    public void saveFactionPoints(UUID factionId, int points) {
        writeQueue.offer(new WriteOperation("saveFactionPoints", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO faction_points (faction_id, points) VALUES (?, ?)")) {
                ps.setString(1, factionId.toString());
                ps.setInt(2, points);
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a pending invite save operation.
     */
    public void savePendingInvite(PendingInvite invite) {
        writeQueue.offer(new WriteOperation("savePendingInvite", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO pending_invites (player_uuid, faction_id, inviter_name, invite_date) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, invite.getPlayerUuid().toString());
                ps.setString(2, invite.getFactionId().toString());
                ps.setString(3, invite.getInviterName());
                ps.setString(4, invite.getInviteDate());
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a pending invite delete operation.
     */
    public void deletePendingInvite(UUID playerUuid, UUID factionId) {
        writeQueue.offer(new WriteOperation("deletePendingInvite", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pending_invites WHERE player_uuid = ? AND faction_id = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, factionId.toString());
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues a bulk delete of all pending invites for a player.
     */
    public void deletePendingInvitesForPlayer(UUID playerUuid) {
        writeQueue.offer(new WriteOperation("deletePendingInvitesForPlayer", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pending_invites WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            }
        }));
    }
    
    /**
     * Queues save of all faction allies (replaces all rows).
     */
    public void saveAllFactionAllies(Map<UUID, Set<UUID>> factionAllies) {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : factionAllies.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        Map<UUID, Set<UUID>> snapshot = copy;
        writeQueue.offer(new WriteOperation("saveAllFactionAllies", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM faction_allies");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO faction_allies (faction_id, ally_faction_id) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Set<UUID>> entry : snapshot.entrySet()) {
                    UUID factionId = entry.getKey();
                    for (UUID allyId : entry.getValue()) {
                        ps.setString(1, factionId.toString());
                        ps.setString(2, allyId.toString());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        }));
    }
    
    /**
     * Queues save of all faction enemies (replaces all rows).
     */
    public void saveAllFactionEnemies(Map<UUID, Set<UUID>> factionEnemies) {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : factionEnemies.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        Map<UUID, Set<UUID>> snapshot = copy;
        writeQueue.offer(new WriteOperation("saveAllFactionEnemies", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM faction_enemies");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO faction_enemies (faction_id, enemy_faction_id) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Set<UUID>> entry : snapshot.entrySet()) {
                    UUID factionId = entry.getKey();
                    for (UUID enemyId : entry.getValue()) {
                        ps.setString(1, factionId.toString());
                        ps.setString(2, enemyId.toString());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        }));
    }
    
    /**
     * Queues save of all pending ally requests (replaces all rows).
     */
    public void saveAllPendingAllyRequests(List<PendingAllyRequest> requests) {
        List<PendingAllyRequest> copy = new ArrayList<>(requests);
        writeQueue.offer(new WriteOperation("saveAllPendingAllyRequests", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM pending_ally_requests");
            }
            if (!copy.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO pending_ally_requests (requester_faction_id, target_faction_id, created_date) VALUES (?, ?, ?)")) {
                    for (PendingAllyRequest req : copy) {
                        ps.setString(1, req.getRequesterFactionId().toString());
                        ps.setString(2, req.getTargetFactionId().toString());
                        ps.setString(3, req.getCreatedDate() != null ? req.getCreatedDate() : LocalDateTime.now().format(DATE_FORMATTER));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        }));
    }
    
    /**
     * Queues save of all ally settings (replaces all rows).
     */
    public void saveAllAllySettings(Map<String, AllySettings> allySettings) {
        Map<String, AllySettings> snapshot = new HashMap<>(allySettings);
        writeQueue.offer(new WriteOperation("saveAllAllySettings", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM ally_settings");
            }
            if (!snapshot.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ally_settings (faction_id, ally_faction_id, share_markers, allow_interactions, " +
                        "allow_open_chest, allow_open_door, allow_open_bench, allow_use_chair, allow_use_portal, allow_place_block, allow_break_block) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<String, AllySettings> entry : snapshot.entrySet()) {
                        String[] parts = entry.getKey().split("_", 2);
                        if (parts.length != 2) continue;
                        AllySettings settings = entry.getValue();
                        ps.setString(1, parts[0]);
                        ps.setString(2, parts[1]);
                        ps.setInt(3, settings.isSharePlayerMarkers() ? 1 : 0);
                        ps.setInt(4, settings.isAllowInteractionsInClaimedChunks() ? 1 : 0);
                        ps.setInt(5, settings.isAllowOpenChest() ? 1 : 0);
                        ps.setInt(6, settings.isAllowOpenDoor() ? 1 : 0);
                        ps.setInt(7, settings.isAllowOpenBench() ? 1 : 0);
                        ps.setInt(8, settings.isAllowUseChair() ? 1 : 0);
                        ps.setInt(9, settings.isAllowUsePortal() ? 1 : 0);
                        ps.setInt(10, settings.isAllowPlaceBlock() ? 1 : 0);
                        ps.setInt(11, settings.isAllowBreakBlock() ? 1 : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        }));
    }
    
    /**
     * Queues save of all member permissions (replaces all rows in faction_permissions).
     * Map key format: "factionId_memberUuid" -> Set of permission strings.
     */
    public void saveAllMemberPermissions(Map<String, Set<String>> memberPermissions) {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : memberPermissions.entrySet()) {
            snapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        writeQueue.offer(new WriteOperation("saveAllMemberPermissions", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM faction_permissions");
            }
            if (!snapshot.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO faction_permissions (faction_id, member_uuid, permission) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, Set<String>> entry : snapshot.entrySet()) {
                        String[] parts = entry.getKey().split("_", 2);
                        if (parts.length != 2) continue;
                        for (String permission : entry.getValue()) {
                            ps.setString(1, parts[0]);
                            ps.setString(2, parts[1]);
                            ps.setString(3, permission);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            }
        }));
    }
    
    /**
     * Shuts down the database manager, closing connections and stopping threads.
     */
    public void shutdown() {
        writerRunning.set(false);
        
        // Wait for queue to drain
        while (!writeQueue.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        writerExecutor.shutdown();
        loadExecutor.shutdown();
        
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.at(Level.WARNING).log("Error closing database connection: " + e.getMessage());
        }
    }
    
    /**
     * Internal class representing a write operation.
     */
    private static class WriteOperation {
        private final String name;
        private final SQLOperation operation;
        
        public WriteOperation(String name, SQLOperation operation) {
            this.name = name;
            this.operation = operation;
        }
        
        public String getName() {
            return name;
        }
        
        public void execute(Connection connection) throws SQLException {
            operation.execute(connection);
        }
    }
    
    /**
     * Functional interface for SQL operations.
     */
    @FunctionalInterface
    private interface SQLOperation {
        void execute(Connection connection) throws SQLException;
    }
}
