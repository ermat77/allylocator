# World Map Public API

How the Hytale world map works and how FactionCore interacts with it (custom claim overlays and cache invalidation).

---

## Overview

The world map is **cached** on both server and client. When a player opens the map, the server returns cached chunk images when available and only calls your custom generator when a chunk is **not** in the cache. So when claim/unclaim (or any custom data) changes, you must **invalidate** that cache so the next open triggers regeneration.

Hytale exposes a **public API** for invalidation. Use it; do not rely on reflection on internal fields.

---

## Public API for Cache Invalidation

### 1. Server-side: `WorldMapManager.clearImagesInChunks(LongSet chunks)`

- **Where**: `world.getWorldMapManager().clearImagesInChunks(chunks)`
- **What**: Removes cached map images for the given chunk indices on the **server**.
- **Effect**: The next time the map needs those chunks, the server will call your `IWorldMap.generate(...)` again for them.
- **Thread**: Must be called on the **world thread** (e.g. inside `world.execute(() -> { ... })`).

**Parameters:**

- `chunks`: `LongSet` of chunk indices (e.g. `it.unimi.dsi.fastutil.longs.LongSet`).
- Chunk index: use `ChunkUtil.indexChunk(chunkX, chunkZ)` from `com.hypixel.hytale.math.util.ChunkUtil`.

**Null safety:** Check `world.getWorldMapManager() != null` before calling.

---

### 2. Client-side: `WorldMapTracker.clearChunks(LongSet chunks)`

- **Where**: `player.getWorldMapTracker().clearChunks(chunks)`
- **What**: Clears the **client’s** cached map data for those chunk indices so the client will request fresh data from the server.
- **Who**: Call for **each player** in the world (e.g. iterate `world.getPlayerRefs()` and get each `Player`, then `player.getWorldMapTracker().clearChunks(chunks)`).
- **Thread**: Same as above — run on the world thread when you call `clearImagesInChunks`.

**Null safety:** Check `player != null` and `player.getWorldMapTracker() != null` before calling.

---

## Chunk Indices

- Chunk coordinates are **chunk X** and **chunk Z** (block coordinates divided by chunk size, e.g. 32).
- A single **chunk index** is a `long` produced by:
  - `ChunkUtil.indexChunk(chunkX, chunkZ)`
- To go back: `ChunkUtil.xOfChunkIndex(index)`, `ChunkUtil.zOfChunkIndex(index)`.
- Use a **LongSet** (e.g. `LongOpenHashSet`) to pass multiple chunks to `clearImagesInChunks` and `clearChunks`.

---

## How to Interact: Queue + Ticking System

Invalidation must run on the **world thread**. Claim/unclaim often run elsewhere (commands, UI). So:

1. **Queue** chunk indices per world when something changes (e.g. claim/unclaim).
2. A **ticking system** registered on the **ChunkStore** runs periodically; for each world that has queued chunks, it runs on the world thread and:
   - Calls `world.getWorldMapManager().clearImagesInChunks(chunks)`
   - For each player in the world, calls `player.getWorldMapTracker().clearChunks(chunks)`
   - Removes that world’s entry from the queue.

### 1. Queue (e.g. in your manager)

- Store: `Map<String, LongSet>` keyed by world name (e.g. `world.getName()`).
- When a chunk at `(chunkX, chunkZ)` changes, add to the set for that world:
  - The chunk itself: `ChunkUtil.indexChunk(chunkX, chunkZ)`
  - Its four neighbors (so borders update):  
    `(chunkX±1, chunkZ)`, `(chunkX, chunkZ±1)`.

Example:

```java
// e.g. in FactionManager
public void queueMapUpdate(World world, int chunkX, int chunkZ) {
    if (world == null) return;
    mapUpdateQueue
        .computeIfAbsent(world.getName(), k -> new LongOpenHashSet())
        .add(ChunkUtil.indexChunk(chunkX, chunkZ));
    mapUpdateQueue.get(world.getName()).add(ChunkUtil.indexChunk(chunkX + 1, chunkZ));
    mapUpdateQueue.get(world.getName()).add(ChunkUtil.indexChunk(chunkX - 1, chunkZ));
    mapUpdateQueue.get(world.getName()).add(ChunkUtil.indexChunk(chunkX, chunkZ + 1));
    mapUpdateQueue.get(world.getName()).add(ChunkUtil.indexChunk(chunkX, chunkZ - 1));
}
```

Call `queueMapUpdate(world, chunkX, chunkZ)` from:

- Commands (e.g. after claim/unclaim).
- UI handlers (e.g. Chunk Visualizer claim/unclaim).
- Any other place that changes data that the map displays.

When **faction name** (or other data drawn on the coloured chunks) changes, clear all chunk images for that faction so they regenerate with the new label:

- Call `FactionManager.getInstance().queueMapUpdateForFaction(faction)` after renaming (e.g. in FactionSettingsPage after `faction.setName(...)`). This queues every chunk claimed by that faction (and their neighbors) for invalidation; the ticking system then clears server and client caches so the next map open shows the new name.

### 2. Ticking system (ChunkStore, delayed tick)

- Extend `DelayedSystem<ChunkStore>` (e.g. delay 3 ticks).
- In `delayedTick`, get the world from the store: `World world = store.getExternalData().getWorld()`.
- If your queue has no entry for `world.getName()`, return.
- Take the `LongSet` for that world, then run on the world thread:

```java
world.execute(() -> {
    if (world.getWorldMapManager() != null) {
        world.getWorldMapManager().clearImagesInChunks(chunks);
    }
    for (PlayerRef playerRef : world.getPlayerRefs()) {
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) continue;
        Player player = world.getEntityStore().getStore().getComponent(ref, Player.getComponentType());
        if (player != null && player.getWorldMapTracker() != null) {
            player.getWorldMapTracker().clearChunks(chunks);
        }
    }
    // Remove this world from the queue so we don't process again until next change
    getMapUpdateQueue().remove(world.getName());
});
```

### 3. Registration

- Register the system on the **ChunkStore** registry (not EntityStore):

```java
getChunkStoreRegistry().registerSystem(new WorldMapUpdateTickingSystem());
```

---

## Custom Map Content (Generator)

To draw custom content (e.g. claim overlays):

1. **Provider**: Implement `IWorldMapProvider`; in `getGenerator(World world)` return your `IWorldMap` (e.g. a singleton).
2. **Generator**: Implement `IWorldMap`:
   - `getWorldMapSettings()`: e.g. delegate to `ChunkWorldMap.INSTANCE.getWorldMapSettings()`.
   - `generate(World world, int imageWidth, int imageHeight, LongSet chunksToGenerate)`: for each chunk index, build a `MapImage` (e.g. terrain + your overlay), then return a `WorldMap` that maps chunk index → image.
   - `generatePointsOfInterest(World world)`: optional; return markers if needed.
3. **Registration**:
   - Register the provider codec: `IWorldMapProvider.CODEC.register(ID, YourProviderClass.class, CODEC)`.
   - For each world (after it’s loaded), set the generator:  
     `world.getWorldMapManager().setGenerator(yourProvider.getGenerator(world))`.  
     Do this in `AddWorldEvent` and when the universe becomes ready for existing worlds. Check `world.getWorldMapManager() != null` before calling.

When a chunk is **not** in the server cache, the manager will call your `generate(...)` for that chunk (and others in the same request). Your generator can then read current claim (or other) data and draw it into the image.

---

## FactionCore Flow (Summary)

| Step | Component | Action |
|------|-----------|--------|
| 1 | Command / UI | After claim or unclaim, call `FactionManager.getInstance().queueMapUpdate(world, chunkX, chunkZ)`. After faction rename, call `queueMapUpdateForFaction(faction)` so chunk images (which show the faction name) are cleared. |
| 2 | FactionManager | Adds the chunk and its four neighbors (as chunk indices) to `mapUpdateQueue` for that world. |
| 3 | WorldMapUpdateTickingSystem | Every 3 ticks, for each ChunkStore (world), if that world has queued chunks, runs `world.execute(() -> { ... })`. |
| 4 | Same runnable | Calls `world.getWorldMapManager().clearImagesInChunks(chunks)` then, for each player, `player.getWorldMapTracker().clearChunks(chunks)`; then removes the world from the queue. |
| 5 | Next map open | Server and client no longer have those chunks cached; manager calls `FactionChunkWorldMap.generate(...)`; `CustomImageBuilder` runs and reads current claim data, so the map shows up-to-date claims. |

---

## Important Notes

- **Always use the public API** (`clearImagesInChunks`, `clearChunks`). Do not rely on reflection on `WorldMapManager` or client internals.
- **Run invalidation on the world thread** (e.g. via a ChunkStore ticking system and `world.execute(...)`).
- **Invalidate the chunk and its neighbors** so borders between claims update correctly.
- **Clear both server and client caches**: `WorldMapManager.clearImagesInChunks` and each `WorldMapTracker.clearChunks`.
- **Register the ticking system** with `getChunkStoreRegistry().registerSystem(...)` so it runs per world.
- **Register your generator** per world with `world.getWorldMapManager().setGenerator(...)` after worlds are available (e.g. `Universe.getUniverseReady()` and `AddWorldEvent`).

This matches the approach used by SimpleClaims and keeps the world map in sync with FactionCore’s claim data.
