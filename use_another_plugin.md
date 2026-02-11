# Using Another Plugin from FactionCore

This document describes how FactionCore discovers and uses optional plugins (e.g. **DelayTp**) in a way that aligns with Hytale’s recommended patterns and works regardless of plugin load order.

---

## 1. Overview

- **Required dependencies** (e.g. LuckPerms): Declared in `Dependencies` in `manifest.json`. The server fails to load your plugin if they are missing. You resolve them at setup and can fail fast.
- **Optional dependencies** (e.g. DelayTp): Declared in `OptionalDependencies`. Your plugin loads even when they are absent. You must resolve them at runtime and handle “not present” (e.g. fallback behavior).

FactionCore uses **DelayTp** as an optional dependency for delayed teleport on `/f home`. If DelayTp is not installed, `/f home` teleports immediately.

---

## 2. Declare the Optional Dependency (Manifest)

In your plugin’s `manifest.json`, use the **plugin identifier** format `Group:Name` (per Hytale docs). Do **not** use the display name alone.

**Example (FactionCore):**

```json
{
  "Group": "com.factioncore",
  "Name": "FactionCore",
  "Main": "com.factioncore.Main",
  "Version": "0.0.2",
  "OptionalDependencies": {
    "com.delaytp:DelayTp": "*"
  }
}
```

- The other plugin’s identifier is built from its own manifest: `Group` + `:` + `Name` (e.g. DelayTp uses `"Group":"com.delaytp","Name":"DelayTp"` → `com.delaytp:DelayTp`).
- Using only `"DelayTp"` would be invalid and can cause server startup errors (“String does not match &lt;group&gt;:&lt;name&gt;”).

---

## 3. How the Other Plugin Should Expose Itself (Plugin Authors)

So that other plugins can use yours without depending on PluginManager internals, expose a **static singleton** as in Hytale’s plugin examples:

1. Hold the plugin instance in a static field.
2. Set it in `setup()`: `instance = this;`
3. Expose it via a static getter: `public static MyPlugin get() { return instance; }`

**Example (DelayTp):**

```java
public class Main extends JavaPlugin {
    private static Main pluginInstance;

    public static Main get() {
        return pluginInstance;
    }

    @Override
    protected void setup() {
        pluginInstance = this;
        // ... register components, systems, etc.
    }
}
```

Consumers can then resolve your plugin by **loading the class** and calling `Main.get()`, without relying on plugin name or PluginManager APIs.

---

## 4. How to Resolve and Use the Other Plugin (Consumer Side)

Do **not** rely on PluginManager’s `getPlugin(name)` or iterating `getPlugins()` by display name: those are not guaranteed by the docs and can fail (e.g. no plugin “named” DelayTp in the list, or load order issues).

Use the **classloading pattern** (same idea as LuckPerms):

1. **Find a ClassLoader** that can load the other plugin’s main class (e.g. `com.delaytp.Main`).
2. **Load the class** with that loader: `Class.forName("com.delaytp.Main", false, loader)`.
3. **Get the plugin instance** by calling the static getter: `clazz.getMethod("get").invoke(null)`.
4. **Call the API** on that instance via reflection (or direct cast if you have the API on classpath).

### 4.1 Finding a ClassLoader

Try, in order:

1. **PluginBridgeClassLoader** – from `PluginManager.get().getBridgeClassLoader()`. This is the preferred way for plugin-to-plugin access and can see all loaded plugins.
2. **Context classloader** – `Thread.currentThread().getContextClassLoader()`.
3. **System classloader** – `ClassLoader.getSystemClassLoader()`.
4. **Default** – your plugin’s classloader (e.g. `Main.class.getClassLoader()`).

For each candidate, try `Class.forName("com.delaytp.Main", false, loader)`. Use the first loader that successfully loads the class.

### 4.2 Getting the Plugin Instance

```java
Class<?> mainClass = Class.forName("com.delaytp.Main", false, loader);
Method getMethod = mainClass.getMethod("get");
Object pluginInstance = getMethod.invoke(null);
```

If `pluginInstance` is non-null, the plugin is loaded and its `setup()` has run. Cache this instance for reuse (e.g. in a field like `delayTpApi`).

### 4.3 Calling the API via Reflection

FactionCore avoids a compile-time dependency on DelayTp, so it calls the API via reflection (see `DelayTpHelper.requestDelayedTeleport`): get the method on the plugin instance and invoke it with the required arguments.

---

## 5. Lazy Resolution (Load Order)

Optional plugins may load **after** your plugin. If you resolve only during your `setup()`, the other plugin’s main class might not be loaded yet and you get no instance.

**Approach:** Resolve **lazily on first use** as well as at setup:

- In the getter that exposes the optional plugin (e.g. `getDelayTpApi()`):
  - If the cached instance is null and you haven’t run the “lazy” resolution yet, call your resolve method once and set a “lazy resolution done” flag.
  - Return the cached instance (possibly still null).
- So: first use (e.g. first `/f home`) triggers a second resolution attempt; by then all plugins are loaded and the classloading pattern can succeed.

This way you don’t depend on Hytale’s plugin load order.

---

## 6. FactionCore’s DelayTp Integration (Summary)

| Piece | Role |
|-------|------|
| **manifest.json** | `OptionalDependencies`: `"com.delaytp:DelayTp": "*"` |
| **getDelayTpApi()** | Returns cached DelayTp instance; runs lazy resolution once if cache is null. |
| **resolveDelayTp()** | Gets a ClassLoader via `getDelayTpClassLoader()`, loads `com.delaytp.Main`, calls `Main.get()`, caches result. |
| **getDelayTpClassLoader()** | Tries PluginBridgeClassLoader, then context, system, and default classloader; returns first that can load `com.delaytp.Main`. |
| **DelayTpHelper** | Calls `requestDelayedTeleport(...)` on the cached instance via reflection. |
| **HomeFactionCommand** | Uses `Main.get().getDelayTpApi()`; if non-null, requests delayed teleport via DelayTpHelper, otherwise teleports immediately. |

---

## 7. Comparison: LuckPerms vs DelayTp

| | LuckPerms (required) | DelayTp (optional) |
|---|----------------------|--------------------|
| **Declared** | Required; plugin fails to start if missing | OptionalDependencies in manifest |
| **Resolution** | ClassLoader that can load `net.luckperms.api.LuckPermsProvider` | ClassLoader that can load `com.delaytp.Main`, then `Main.get()` |
| **Cached** | ClassLoader (in PermissionUtil) | Plugin instance (in Main.delayTpApi) |
| **API usage** | Reflection via that ClassLoader (e.g. LuckPermsProvider.get()) | Reflection on cached instance (e.g. requestDelayedTeleport) |
| **Lazy** | ClassLoader resolved on first permission check | Instance resolved at setup + once lazily on first getDelayTpApi() |

Same pattern: **find ClassLoader → load API/entry class → get singleton (or API) → call methods**. No dependency on PluginManager or plugin display name.

---

## 8. What to Avoid

- **Don’t** use only the plugin’s display name in `OptionalDependencies` (e.g. `"DelayTp": "*"`). Use `Group:Name` (e.g. `com.delaytp:DelayTp`).
- **Don’t** rely on `PluginManager.getPlugin("DelayTp")` or iterating `getPlugins()` and matching by name; this is not the documented contract and is fragile (names, load order).
- **Don’t** resolve only in `setup()` if the other plugin is optional and might load after yours; add lazy resolution on first use.

---

## 9. References

- Hytale plugin docs: plugin identifier = `Group:Name`; singleton `get()` pattern for the main class.
- FactionCore: `Main.resolveDelayTp()`, `Main.getDelayTpClassLoader()`, `Main.getDelayTpApi()`, `DelayTpHelper.requestDelayedTeleport`.
- LuckPerms integration: `PermissionUtil.getLuckPermsClassLoader()` (same classloading order; required dependency).
