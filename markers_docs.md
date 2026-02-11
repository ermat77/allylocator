# Player Map Markers – Documentation

How Hytale’s **player markers** on the world map work and how FactionCore filters them (faction + ally “share markers” settings).

For **map chunk images** and cache invalidation (claims overlay, etc.), see [worldmap.md](worldmap.md).

---

## Overview

- **Player markers** are the icons that show other players on the map. Hytale decides which players to send to each client using a **per-viewer filter**.
- FactionCore sets that filter so a player sees: **same-faction members** and **ally faction members** only when that ally has enabled “Share faction members' PlayerMarkers on the map” for the viewer’s faction (same pattern as [ProtectionService](src/main/java/com/factioncore/systems/protection/ProtectionService.java) for ally permissions).

---

## How Hytale Creates and Filters Player Markers

### 1. Components involved

| Component | Role |
|-----------|------|
| **WorldMapTracker** | Per-player; from `player.getWorldMapTracker()`. Holds a `MapMarkerTracker` and delegates `setPlayerMapFilter` to it. |
| **MapMarkerTracker** | Stores `Predicate<PlayerRef> playerMapFilter`. Used by marker providers when building the list of markers to send. |
| **WorldMapManager** | Per-world; holds **MarkerProvider**s (e.g. `PlayerIconMarkerProvider`). |
| **PlayerIconMarkerProvider** | Adds player icons to the map. Iterates `world.getPlayerRefs()`, skips self, checks distance, then **consults the viewer’s filter** for each other player. |

### 2. When markers are updated

- Each frame, `WorldMapTracker.tick()` runs.
- **Only when** `world.isCompassUpdating()` is true does it call `markerTracker.updatePointsOfInterest(World, dt, viewRadius, chunkX, chunkZ)`.
- `updatePointsOfInterest` iterates `WorldMapManager.getMarkerProviders()` and calls `provider.update(World, MapMarkerTracker, chunkX, chunkY, radius)`.
- **PlayerIconMarkerProvider.update()** also returns early if `WorldMapConfig.isDisplayPlayers()` is false.

So the filter is only applied when the game is actually updating map points of interest (compass/map active). Setting the filter is enough; Hytale uses it on the next marker update.

### 3. Filter predicate semantics (critical)

The predicate is **“hide when true, show when false”**:

- **`filter == null`** → marker **added** (player shown).
- **`filter.test(otherPlayerRef) == true`** → player **skipped** (marker **not** sent → **hidden**).
- **`filter.test(otherPlayerRef) == false`** → marker **added** (player **shown**).

So when implementing the predicate:

- Return **`true`** to **hide** that player’s marker.
- Return **`false`** to **show** that player’s marker.

Getting this backwards (e.g. “return true to show”) will invert visibility.

---

## FactionCore Implementation

### Entry points

- **Apply filter to one player:**  
  `FactionMarkerFilterService.applyFilterToPlayer(Player player, PlayerRef playerRef)`
- **Apply from event:**  
  `FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent event)` (used on player ready).
- **Refresh all members of a faction:**  
  `FactionMarkerFilterService.updateFiltersForFaction(FactionInfo faction)`

### Filter logic (who sees whom)

For a **viewer** (player whose map we’re filtering) and an **other** player (candidate marker):

1. **Null / self** → hide (`true`).
2. **Viewer has no faction** → hide all others (`true`).
3. **Same faction** (viewer’s faction is owner or has the other as member) → show (`false`).
4. **Ally:**  
   - Resolve other’s faction. If other has no faction or is not an ally of viewer’s faction → hide (`true`).  
   - Otherwise use **the other’s faction’s** ally settings **for the viewer’s faction**:  
     `getAllySettings(otherFaction.getId(), viewerFaction.getId()).isSharePlayerMarkers()`.  
   - If that is true → show (`false`); else → hide (`true`).

So: the faction **whose members are shown** is the one that has the “Share faction members' PlayerMarkers on the map” checkbox enabled **for the viewer’s faction**. Same pattern as ProtectionService (the faction that “grants” the permission owns the setting).

### When to apply or refresh filters

| When | Action |
|------|--------|
| Player ready | `applyFilterToPlayer(PlayerReadyEvent)` (e.g. from Main’s event handler). |
| Periodic | `FactionMarkerFilterSystem` (ECS) calls `applyFilterToPlayer(player, playerRef)` every 200 ticks (~10 s) for each player. |
| Faction membership change | `FactionManager` calls `updateFiltersForFaction(faction)` for the affected faction(s) so members get updated visibility. |
| Ally settings saved | `FactionManager.setAllySettings(...)` calls `updateFiltersForFaction(allyFaction)` so the **ally faction’s** members see or stop seeing our members when “Share markers” is toggled. |

### Refreshing filters for a whole faction

When something changes that affects what a faction’s members should see (e.g. membership change, or ally settings for that faction):

```java
FactionInfo faction = factionManager.getFactionById(factionId);
if (faction != null) {
    FactionMarkerFilterService.updateFiltersForFaction(faction);
}
```

This updates the filter for every online member of that faction (owner + members) by resolving each to a `Player` in a world and calling `applyFilterToPlayer(player, playerRef)`.

---

## Ally settings and “Share markers”

- **AllySettings** are per (faction, ally): “our faction’s settings for this ally”.
- **Share markers:** “We share our faction members’ map markers with this ally.”
- So when **faction B** enables “Share faction members' PlayerMarkers on the map” for **ally A**, **A’s members** will see **B’s members** on the map. B’s members do not automatically see A’s unless A also enables the same for B.
- Stored in DB; edited in UI: Diplomacy → ally → Ally settings → “Share faction members' PlayerMarkers on the map”.
- When ally settings are saved, `FactionManager.setAllySettings(factionId, allyFactionId, settings)` refreshes the **ally faction’s** filters so the change takes effect without rejoin.

---

## Files reference

| File | Purpose |
|------|---------|
| [FactionMarkerFilterService.java](src/main/java/com/factioncore/systems/marker/FactionMarkerFilterService.java) | Filter logic; `applyFilterToPlayer`, `updateFiltersForFaction`, `updateFilterForPlayer`. |
| [FactionMarkerFilterSystem.java](src/main/java/com/factioncore/systems/marker/FactionMarkerFilterSystem.java) | ECS system that periodically reapplies the filter to each player. |
| [AllySettings](src/main/java/com/factioncore/diplomacy/AllySettings.java) | `isSharePlayerMarkers()` for the “share markers” checkbox. |
| [FactionManager](src/main/java/com/factioncore/faction/FactionManager.java) | `getAllySettings`, `getAllies`, `setAllySettings` (calls `updateFiltersForFaction(allyFaction)`). |
| [worldmap.md](worldmap.md) | Map chunk images, cache invalidation, claim overlays. |
