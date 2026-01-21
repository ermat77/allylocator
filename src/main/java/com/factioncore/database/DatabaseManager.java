package com.factioncore.database;

import com.factioncore.claim.ChunkInfo;
import com.factioncore.claim.tracking.ModifiedTracking;
import com.factioncore.faction.FactionInfo;
import com.factioncore.invite.PendingInvite;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Constants;

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
        
        // Create database path
        Path basePath = Constants.UNIVERSE_PATH.resolve("FactionCore");
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
            
            // Create indexes for performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_faction_id ON faction_members(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_faction_permissions_faction_id ON faction_permissions(faction_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_claims_dimension ON claims(dimension)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pending_invites_player_uuid ON pending_invites(player_uuid)");
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
                    
                    // Load permissions
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT member_uuid, permission FROM faction_permissions WHERE faction_id = ?")) {
                        ps.setString(1, id.toString());
                        try (ResultSet rsPerms = ps.executeQuery()) {
                            while (rsPerms.next()) {
                                UUID memberUuid = UUID.fromString(rsPerms.getString("member_uuid"));
                                String permission = rsPerms.getString("permission");
                                faction.grantMemberPermission(memberUuid, permission);
                            }
                        }
                    }
                    
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
    
    // ========== Write Operations (Queued) ==========
    
    /**
     * Queues a faction save operation.
     */
    public void saveFaction(FactionInfo faction) {
        writeQueue.offer(new WriteOperation("saveFaction", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO factions (id, owner, name, description, color, created_date, modified_date) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, faction.getId().toString());
                ps.setString(2, faction.getOwner().toString());
                ps.setString(3, faction.getName());
                ps.setString(4, faction.getDescription());
                ps.setInt(5, faction.getColor());
                ps.setString(6, faction.getCreatedDate() != null ? 
                    faction.getCreatedDate().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));
                ps.setString(7, faction.getModifiedDate() != null ? 
                    faction.getModifiedDate().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));
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
            
            // Save permissions
            try (PreparedStatement deletePerms = conn.prepareStatement(
                    "DELETE FROM faction_permissions WHERE faction_id = ?")) {
                deletePerms.setString(1, faction.getId().toString());
                deletePerms.executeUpdate();
            }
            try (PreparedStatement insertPerm = conn.prepareStatement(
                    "INSERT INTO faction_permissions (faction_id, member_uuid, permission) VALUES (?, ?, ?)")) {
                for (Map.Entry<UUID, Set<String>> entry : faction.getMemberPermissionsMap().entrySet()) {
                    for (String permission : entry.getValue()) {
                        insertPerm.setString(1, faction.getId().toString());
                        insertPerm.setString(2, entry.getKey().toString());
                        insertPerm.setString(3, permission);
                        insertPerm.addBatch();
                    }
                }
                insertPerm.executeBatch();
            }
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
