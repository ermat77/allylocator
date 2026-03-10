package com.factioncore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and saves prism_attacks.json with Gson so the file has the desired structure:
 * <pre>
 * {
 *   "attackerTimeSeconds": 60,
 *   "timeBeforeAutoDestruction": 600,
 *   "prefabs": [
 *     { "prefabId": "...", "attackersPositions": [{ "x", "y", "z" }, ...], "defendersPositions": [...] }
 *   ]
 * }
 * </pre>
 * Provides the same usage as {@link com.hypixel.hytale.server.core.util.Config} (get, load, save).
 */
public class PrismAttacksConfigHolder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CONFIG_TYPE = new TypeToken<PrismAttacksConfig>() {}.getType();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PrismAttacksConfig-IO");
        t.setDaemon(true);
        return t;
    });

    private final Path configPath;
    private volatile PrismAttacksConfig config;

    public PrismAttacksConfigHolder(@Nonnull Path pluginDataPath) {
        this.configPath = pluginDataPath.resolve("prism_attacks.json");
        this.config = new PrismAttacksConfig();
    }

    /** Returns the current config (never null; defaults if not yet loaded). */
    @Nonnull
    public PrismAttacksConfig get() {
        return config != null ? config : new PrismAttacksConfig();
    }

    /** Loads config from prism_attacks.json. Creates file with defaults if missing. */
    @Nonnull
    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(configPath)) {
                    byte[] bytes = Files.readAllBytes(configPath);
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    PrismAttacksConfig loaded = GSON.fromJson(json, CONFIG_TYPE);
                    this.config = loaded != null ? loaded : new PrismAttacksConfig();
                } else {
                    this.config = new PrismAttacksConfig();
                    Path parent = configPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    String json = GSON.toJson(this.config, CONFIG_TYPE);
                    Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                this.config = new PrismAttacksConfig();
                throw new RuntimeException("Failed to load prism_attacks.json: " + e.getMessage(), e);
            }
        }, IO_EXECUTOR);
    }

    /** Saves current config to prism_attacks.json. */
    @Nonnull
    public CompletableFuture<Void> save() {
        return CompletableFuture.runAsync(() -> {
            try {
                PrismAttacksConfig toSave = this.config != null ? this.config : new PrismAttacksConfig();
                String json = GSON.toJson(toSave, CONFIG_TYPE);
                Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException("Failed to save prism_attacks.json: " + e.getMessage(), e);
            }
        }, IO_EXECUTOR);
    }
}
