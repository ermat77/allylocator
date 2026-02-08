package com.factioncore.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Cache of player UUID to display name, loaded from universe/players JSON files at startup.
 * Used as a fallback when a player is offline (Universe.getPlayer(uuid) returns null).
 * Thread-safe; can be updated when online players are seen so the cache stays fresh.
 */
public final class PlayerNameCache {

    private static final ConcurrentMap<UUID, String> CACHE = new ConcurrentHashMap<>();

    private PlayerNameCache() {}

    /**
     * Loads UUID -> displayName from JSON files in the given directory (e.g. universe/players).
     * Supports Hytale player entity JSON structure:
     * - UUID: from filename ({@code <uuid>.json}) since UUID in JSON is stored as BSON binary.
     * - Display name: from {@code Components.DisplayName.DisplayName.RawText}.
     * Also supports flat JSON with root-level "uuid"/"displayName"/"username"/"display_name".
     *
     * @param playersDir Path to the players directory (e.g. Constants.UNIVERSE_PATH.resolve("players"))
     * @return Number of entries loaded
     */
    public static int loadFrom(Path playersDir) {
        if (playersDir == null || !Files.isDirectory(playersDir)) {
            ConfigUtil.debug("PlayerNameCache: players dir not found or not a directory: " + playersDir);
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String content = Files.readString(file);
                    JsonElement el = JsonParser.parseString(content);
                    if (el == null || !el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    UUID uuid = null;
                    String name = null;
                    // UUID: prefer filename (Hytale stores UUID as BSON binary in JSON)
                    String baseName = file.getFileName().toString();
                    if (baseName.endsWith(".json")) {
                        uuid = parseUuid(baseName.substring(0, baseName.length() - 5));
                    }
                    if (uuid == null && obj.has("uuid")) {
                        uuid = parseUuid(obj.get("uuid").getAsString());
                    }
                    if (uuid == null && obj.has("Uuid")) {
                        uuid = parseUuid(obj.get("Uuid").getAsString());
                    }
                    // Display name: Hytale structure Components.DisplayName.DisplayName.RawText
                    name = getStringPath(obj, "Components", "DisplayName", "DisplayName", "RawText");
                    if (name == null || name.isEmpty()) {
                        if (obj.has("displayName")) name = obj.get("displayName").getAsString();
                    }
                    if ((name == null || name.isEmpty()) && obj.has("username")) {
                        name = obj.get("username").getAsString();
                    }
                    if ((name == null || name.isEmpty()) && obj.has("display_name")) {
                        name = obj.get("display_name").getAsString();
                    }
                    if (uuid != null && name != null && !name.isEmpty()) {
                        CACHE.put(uuid, name);
                        count++;
                    }
                } catch (Exception e) {
                    ConfigUtil.debug("PlayerNameCache: skip file " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ConfigUtil.debug("PlayerNameCache: failed to read players dir " + playersDir + ": " + e.getMessage());
        }
        ConfigUtil.debug("PlayerNameCache: loaded " + count + " player names from " + playersDir);
        return count;
    }

    /** Gets a string at path key1.key2.key3... (each key is a nested object; last key is the string value). */
    private static String getStringPath(JsonObject root, String key1, String key2, String key3, String key4) {
        if (root == null || !root.has(key1)) return null;
        JsonElement e1 = root.get(key1);
        if (e1 == null || !e1.isJsonObject()) return null;
        JsonObject o1 = e1.getAsJsonObject();
        if (!o1.has(key2)) return null;
        JsonElement e2 = o1.get(key2);
        if (e2 == null || !e2.isJsonObject()) return null;
        JsonObject o2 = e2.getAsJsonObject();
        if (!o2.has(key3)) return null;
        JsonElement e3 = o2.get(key3);
        if (e3 == null || !e3.isJsonObject()) return null;
        JsonObject o3 = e3.getAsJsonObject();
        if (!o3.has(key4)) return null;
        JsonElement e4 = o3.get(key4);
        return e4 != null && e4.isJsonPrimitive() ? e4.getAsString() : null;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the cached display name for the given UUID, or null if not in cache.
     */
    public static String get(UUID uuid) {
        return uuid == null ? null : CACHE.get(uuid);
    }

    /**
     * Puts or updates the display name for the given UUID (e.g. when an online player is seen).
     */
    public static void put(UUID uuid, String displayName) {
        if (uuid != null && displayName != null && !displayName.isEmpty()) {
            CACHE.put(uuid, displayName);
        }
    }

    /**
     * Returns the current number of cached entries (for tests/debug).
     */
    public static int size() {
        return CACHE.size();
    }
}
