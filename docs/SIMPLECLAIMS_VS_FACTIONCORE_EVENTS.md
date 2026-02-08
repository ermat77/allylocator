# SimpleClaims vs FactionCore: Event Handling Comparison

Analysis of how SimpleClaims handles protection events in `sources/SimpleClaims-main(1)/SimpleClaims-main/src/main/java/com/buuz135/simpleclaims/systems/events/` versus our FactionCore implementation.

---

## 1. Event Systems Overview

### SimpleClaims (8 event systems)

| System | Event Type | Base Class | Purpose |
|--------|------------|------------|---------|
| **BreakBlockEventSystem** | `BreakBlockEvent` | EntityEventSystem | Block player block breaks in claimed chunks |
| **DamageBlockEventSystem** | `DamageBlockEvent` | EntityEventSystem | Block block damage (crack progress) so break never completes |
| **PlaceBlockEventSystem** | `PlaceBlockEvent` | EntityEventSystem | Block block placement |
| **InteractEventSystem** | `UseBlockEvent.Pre` | EntityEventSystem | Block block use (chests, doors, benches, etc.) + bench extra resources |
| **PickupInteractEventSystem** | `InteractivelyPickupItemEvent` | EntityEventSystem | Block item pickup in claims (note: comment says "Doesnt currently work") |
| **GlobalBreakBlockEventSystem** | `BreakBlockEvent` | **WorldEventSystem** | Block break events **without entity context** (explosions, etc.) |
| **GlobalDamageBlockEventSystem** | `DamageBlockEvent` | **WorldEventSystem** | Block damage events **without entity context** |
| **CustomDamageEventSystem** | `Damage` | DamageEventSystem | PVP in claims (friendly fire, PVP enabled per party) |

### FactionCore (6 protection systems, after refactor)

| System | Event Type | Base Class | Purpose |
|--------|------------|------------|---------|
| **BreakBlockProtectionSystem** | `BreakBlockEvent` | EntityEventSystem | Block player block breaks |
| **DamageBlockProtectionSystem** | `DamageBlockEvent` | EntityEventSystem | Block block damage |
| **PlaceBlockProtectionSystem** | `PlaceBlockEvent` | EntityEventSystem | Block block placement |
| **UseBlockProtectionSystem** | `UseBlockEvent.Pre` | EntityEventSystem | Block block use |
| **GlobalBreakBlockProtectionSystem** | `BreakBlockEvent` | **WorldEventSystem** | Block break without entity (explosions, etc.) |
| **GlobalDamageBlockProtectionSystem** | `DamageBlockEvent` | **WorldEventSystem** | Block damage without entity |

---

## 2. Differences

### 2.1 Global (world-level) event systems — **MISSING in FactionCore**

SimpleClaims registers **WorldEventSystem** handlers for break/damage **without entity context**:

- **GlobalBreakBlockEventSystem** – handles `BreakBlockEvent` when there is no player entity (e.g. explosions, custom items, other sources). It gets world from `store.getExternalData().getWorld()` and blocks if `isAllowedToInteract(null, worldName, x, z, ...)` returns false. SimpleClaims’ `ClaimManager.isAllowedToInteract(null, ...)` returns `false` for claimed chunks (deny when no player).
- **GlobalDamageBlockEventSystem** – same idea for `DamageBlockEvent` without entity context.

**FactionCore:** We do **not** have any `WorldEventSystem` for break/damage. So:

- Explosions (TNT, creeper, etc.) in claimed chunks are **not** blocked.
- Any break/damage triggered without a player entity (e.g. some custom items or mechanics) are **not** blocked.

**Recommendation:** Add `GlobalBreakBlockProtectionSystem` and `GlobalDamageBlockProtectionSystem` (WorldEventSystem), and ensure `ProtectionService.isAllowedToInteract(null, dimension, blockX, blockZ, ...)` denies interaction in claimed chunks (return false when playerUuid is null and chunk is claimed).

---

### 2.2 Query and dependencies — **Partially aligned**

| Aspect | SimpleClaims | FactionCore |
|--------|--------------|-------------|
| **Query** | All entity systems use `PlayerRef.getComponentType()` | Break/Damage: `PlayerRef.getComponentType()`. **Place** and **Use**: still `Query.any()` |
| **Dependencies** | All use `RootDependency.first()` | Break/Damage: `RootDependency.first()`. **Place** and **Use**: no `getDependencies()` |

So:

- **BreakBlock** and **DamageBlock** in FactionCore are aligned (player-only query, run first).
- **PlaceBlockProtectionSystem** and **UseBlockProtectionSystem** should be updated to:
  - `getQuery() = PlayerRef.getComponentType()`
  - `getDependencies() = Collections.singleton(RootDependency.first())`
  for consistency and to run early.

---

### 2.3 “Blocks that ignore restriction” — **MISSING in FactionCore**

SimpleClaims:

- **BreakBlockEventSystem** and **DamageBlockEventSystem** (and global variants): before the claim check, they skip cancellation if the block type is in `Main.CONFIG.get().getBlocksThatIgnoreInteractRestrictions()` (e.g. certain blocks always breakable).
- **InteractEventSystem**: same list used to set `ignored = true` so that for those blocks, the claim check does not cancel the interaction.

**FactionCore:** We have no config list of blocks that ignore break/interact restrictions. Every block is subject to claim checks.

If you want parity: add a config option (e.g. in ClaimConfig) and, in Break/Damage (and optionally Use) protection systems, skip cancelling when the block type matches that list.

---

### 2.4 Use block / interact — **Simpler in FactionCore**

SimpleClaims **InteractEventSystem** (`UseBlockEvent.Pre`):

- Uses a **block-type-specific** policy:
  - Chest → `PartyInfo::isChestInteractEnabled`, `PARTY_PROTECTION_INTERACT_CHEST`
  - Bench (not furniture) → `PartyInfo::isBenchInteractEnabled`, `PARTY_PROTECTION_INTERACT_BENCH` + bench extra resources (BenchChestCache, CraftingUiQuantitiesSystem, etc.)
  - Door → `PartyInfo::isDoorInteractEnabled`, `PARTY_PROTECTION_INTERACT_DOOR`
  - Chair/stool/furniture bench → `PartyInfo::isChairInteractEnabled`, `PARTY_PROTECTION_INTERACT_CHAIR`
  - Portal/teleporter → `PartyInfo::isPortalInteractEnabled`, `PARTY_PROTECTION_INTERACT_PORTAL`
  - Default → `PartyInfo::isBlockInteractEnabled`, `PARTY_PROTECTION_INTERACT`
- Uses a **permission** string per type (`PartyOverrides.PARTY_PROTECTION_*`) in `isAllowedToInteract(..., permission)` (member/ally override).
- Integrates with **WindowExtraResourcesState** and bench UI.

FactionCore **UseBlockProtectionSystem**:

- Single policy: `ProtectionService::isBlockInteractEnabled`, no block-type-specific settings (no separate chest/door/bench/chair/portal).
- No per-interaction-type permission override (no FactionCore equivalent of PartyOverrides).
- No bench extra resources or window state.

So: we match the “generic block interact” behavior; we do not replicate chest/bench/door/chair/portal granularity or bench UI.

---

### 2.5 Pickup and PVP — **MISSING in FactionCore**

- **PickupInteractEventSystem:** SimpleClaims listens to `InteractivelyPickupItemEvent` and cancels if the player is not allowed to interact at their position (e.g. in another party’s claim). They note in code it “Doesnt currently work”.
- **CustomDamageEventSystem:** SimpleClaims extends `DamageEventSystem` to enforce PVP rules in claims (chunk PVP enabled/disabled, friendly fire). We have no equivalent.

FactionCore has no pickup protection and no claim-based PVP system.

---

### 2.6 Registration order (SimpleClaims)

```text
BreakBlockEventSystem
DamageBlockEventSystem
PlaceBlockEventSystem
InteractEventSystem
PickupInteractEventSystem
... (ticking, etc.)
CustomDamageEventSystem
...
GlobalDamageBlockEventSystem
GlobalBreakBlockEventSystem
```

Global systems are registered **after** entity-level systems. We currently register only entity-level protection systems; adding global systems would mirror this order (entity first, then global).

---

### 2.7 World/dimension and entity access

- **SimpleClaims** (entity systems): gets world via `player.getWorld().getName()` (Player from store).
- **FactionCore** (Break/Place): gets world via `(EntityStore) store.getExternalData()` then `entityStore.getWorld()`.
- **FactionCore** (Use): gets world via `context.getCommandBuffer().getExternalData()` then `entityStore.getWorld()`.

Functionally both resolve the world for the current context; only the API usage differs.

---

## 3. Summary Table

| Feature | SimpleClaims | FactionCore |
|---------|--------------|-------------|
| Entity BreakBlockEvent | ✅ BreakBlockEventSystem | ✅ BreakBlockProtectionSystem |
| Entity DamageBlockEvent | ✅ DamageBlockEventSystem | ✅ DamageBlockProtectionSystem |
| Entity PlaceBlockEvent | ✅ PlaceBlockEventSystem | ✅ PlaceBlockProtectionSystem |
| Entity UseBlockEvent.Pre | ✅ InteractEventSystem | ✅ UseBlockProtectionSystem |
| Global BreakBlockEvent (no entity) | ✅ GlobalBreakBlockEventSystem | ✅ GlobalBreakBlockProtectionSystem |
| Global DamageBlockEvent (no entity) | ✅ GlobalDamageBlockEventSystem | ✅ GlobalDamageBlockProtectionSystem |
| Query = PlayerRef | ✅ All entity protection | ✅ All entity protection |
| RootDependency.first() | ✅ All | ✅ All |
| Blocks-ignore-restriction list | ✅ Config + used in break/damage/interact | ✅ ClaimConfig.BlocksThatIgnoreRestriction |
| Block-type-specific use (chest/door/bench/…) | ✅ + permission overrides | ❌ Single “block interact” |
| Pickup in claims | ✅ (non-working in their code) | ❌ Missing |
| PVP in claims (DamageEventSystem) | ✅ CustomDamageEventSystem | ❌ Missing |

---

## 4. Recommended next steps for FactionCore

1. **Add global protection (high impact)**  
   Implement `GlobalBreakBlockProtectionSystem` and `GlobalDamageBlockProtectionSystem` (WorldEventSystem), and ensure `ProtectionService.isAllowedToInteract(null, ...)` denies in claimed chunks when `playerUuid == null`.

2. **Align Place and Use systems**  
   Use `PlayerRef.getComponentType()` and `RootDependency.first()` for PlaceBlockProtectionSystem and UseBlockProtectionSystem.

3. **Optional:** Add configurable “blocks that ignore break/interact restriction” and use it in Break/Damage (and optionally Use) systems.

4. **Optional:** Add pickup protection (`InteractivelyPickupItemEvent`) and/or claim-based PVP (custom DamageEventSystem) if you want feature parity with SimpleClaims.

This document reflects the code in `sources/SimpleClaims-main(1)/SimpleClaims-main/src/main/java/com/buuz135/simpleclaims/systems/events/` and `src/main/java/com/factioncore/systems/protection/`.
