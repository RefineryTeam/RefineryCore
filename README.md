# RefineryCore

A modern Paper plugin framework providing annotation-driven commands, GUIs, and configuration — built for the Refinery Team.

---

## Installation

**Maven**

```xml
<repository>
    <id>reaudacity-releases</id>
    <url>https://repository.reaudacity.online/releases</url>
</repository>

<dependency>
<groupId>xyz.refineryteam</groupId>
<artifactId>refinerycore</artifactId>
<version>0.0.2</version>
<scope>provided</scope>
</dependency>
```

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    maven("https://repository.reaudacity.online/releases")
}

dependencies {
    compileOnly("xyz.refineryteam:refinerycore:0.0.2")
}
```

**Gradle (Groovy)**

```groovy
repositories {
    maven { url "https://repository.reaudacity.online/releases" }
}

dependencies {
    compileOnly "xyz.refineryteam:refinerycore:0.0.2"
}
```

**Note:** Make sure that if you're using this plugin, you add it to your dependencies field in your plugin.yml file!

---

## Requirements

- Java 21+
- Paper 1.21+

---

## Features

### Commands

Register commands without touching `plugin.yml`. Annotate a class with `@Command` and methods with `@Subcommand`.

```java
@Command(name = "staff", description = "Staff management", permission = "refinery.staff")
public class StaffCommand extends RefineryCommand {

    @DefaultHandler
    public void onDefault(CommandContext ctx) {
        ctx.reply("<gray>Use /staff help for a list of commands.");
    }

    @Subcommand(value = "teleport", description = "Teleport to a player.", completions = { "<player>" })
    @PlayerOnly
    public void onTeleport(CommandContext ctx) {
        ctx.argPlayer(0).ifPresent(target -> ctx.player().teleport(target));
    }
}
```

Register in your plugin:

```java
getCommandRegistry().register(new StaffCommand());
```

`/staff help` is available automatically on every command. Hovering a command in chat shows its description and permission node.

**Argument parsing** — `CommandContext` provides typed helpers:

```java
ctx.arg(0)          // Optional<String>
ctx.argInt(0)       // Optional<Integer>
ctx.argPlayer(0)    // Optional<Player>
ctx.argEnum(0, MyEnum.class)
ctx.joinArgs(1)     // joins remaining args into a string
```

**Cooldowns** — annotate a `@Subcommand` or `@DefaultHandler` method with `@Cooldown` to rate-limit it per player. Enforced automatically before the method runs; no manual checks needed.

```java
@Cooldown(value = 30, unit = TimeUnit.SECONDS, bypassPermission = "refinery.staff.bypass")
@Subcommand(value = "heal", description = "Heal yourself.")
@PlayerOnly
public void onHeal(CommandContext ctx) {
    ctx.player().setHealth(20.0);
}
```

| Attribute | Description |
|---|---|
| `value` / `unit` | Cooldown duration |
| `bypassPermission` | Senders with this permission skip the cooldown entirely |
| `message` | MiniMessage sent when on cooldown; supports `%time%` (seconds remaining) |

Cooldowns are keyed per command class + method, so two different commands never collide even if a method name is reused. For cooldowns outside of commands (abilities, kits, custom mechanics), use `CooldownManager` directly:

```java
CooldownManager cooldowns = new CooldownManager();

if (cooldowns.tryAcquire("kits", "starter", player.getUniqueId(), Duration.ofMinutes(30))) {
    giveKit(player);
} else {
    long remaining = cooldowns.remainingSeconds("kits", "starter", player.getUniqueId());
    player.sendMessage("Wait " + remaining + "s.");
}
```

---

### GUIs

Extend `RefineryGUI` and annotate methods to define items and click handlers.

```java
public class ExampleGUI extends RefineryGUI {

    public ExampleGUI() {
        super(27, "<bold>Example</bold>");
    }

    @Override
    public GUILayout[] layouts() {
        return new GUILayout[]{ GUILayout.border(3, fillerItem) };
    }

    @SlotItem(slots = { 13 })
    public ItemStack centerItem() {
        return ItemBuilder.of(Material.DIAMOND).name("<aqua>Click me").build();
    }

    @SlotAction(slots = { 13 })
    public void onCenterClick(Player player) {
        player.sendMessage("Clicked!");
    }

    @Override
    public void onInitialize(Player player) {}
}
```

Open a GUI:

```java
RefineryGUI.open(player, new ExampleGUI());
```

For paginated GUIs, extend `PaginatedGUI<T>`:

```java
public class PlayerListGUI extends PaginatedGUI<Player> {

    public PlayerListGUI(List<Player> players) {
        super(54, "Player List", players, 45);
    }

    @Override
    protected int[] contentSlots() { /* define page item slots */ }

    @Override
    protected ItemStack renderItem(Player viewer, Player entry, int index) { /* build item */ }

    @Override
    protected void onItemClick(Player viewer, Player entry, int index, InventoryClickEvent event) { /* handle click */ }
}
```

**Layout helpers:**

```java
GUILayout.border(3, fillerItem)   // fills the border of a 3-row GUI
GUILayout.fill(27, fillerItem)    // fills all slots
layout.merge(otherLayout)         // combine layouts
```

---

### Items

`ItemBuilder` provides a fluent API for building `ItemStack`s.

```java
ItemBuilder.of(Material.PLAYER_HEAD)
    .name("<yellow>Abdullah")
    .lore("<gray>Click to view profile", "<dark_gray>Staff member")
    .skullOwner(player.getUniqueId())
        .build();
```

Key methods:

| Method | Description |
|---|---|
| `name(String)` | MiniMessage display name |
| `lore(String...)` | MiniMessage lore lines |
| `appendLore(String...)` | Append lines to existing lore |
| `enchant(Enchantment, int)` | Add enchantment |
| `glint()` | Invisible enchantment glow |
| `hideAll()` | Hide all item flags |
| `unbreakable()` | Set unbreakable |
| `customModelData(int)` | Set custom model data |
| `skullOwner(UUID)` | Set skull owner by UUID |
| `skullTexture(String)` | Set skull texture by URL |
| `specificMeta(Class, Consumer)` | Cast and modify specific meta types |

---

### Scoreboards

`Scoreboard` wraps a per-player sidebar so you push MiniMessage lines instead of juggling `Objective`/`Team`/score plumbing yourself.

```java
Scoreboard board = Scoreboard.builder(player)
    .title("<gradient:blue:aqua><b>MyServer")
    .line("")
    .line("<gray>Rank: <white>VIP")
    .line("<gray>Coins: <gold>1,250")
    .line("")
    .build();

board.line(2, "<gray>Coins: <gold>" + newBalance); // update a single line later
board.destroy(); // restore the player's main scoreboard, e.g. on quit
```

For live-updating boards (timers, stats), use `ScoreboardManager` to avoid writing your own `BukkitRunnable`:

```java
ScoreboardManager boards = new ScoreboardManager(this);

boards.create(player, sb -> sb
    .title("<gradient:blue:aqua><b>MyServer")
    .line("<gray>Rank: <white>%rank%")
    .line("<gray>Coins: <gold>%coins%"));

boards.startUpdating(player, 20L, board -> board
    .line(1, "<gray>Rank: <white>" + getRank(player))
    .line(2, "<gray>Coins: <gold>" + getCoins(player)));

// on quit:
boards.remove(player);

// on plugin disable:
boards.shutdown();
```

Max 15 lines (vanilla sidebar limit); index `0` is the top line.

---

### Database

`RefineryDatabase` wraps HikariCP so you get a pooled connection with one factory call per backend — no manual `HikariConfig` setup.

```java
RefineryDatabase db = RefineryDatabase.sqlite(this, "data.db");
// or: RefineryDatabase.h2(this, "data.db")
// or: RefineryDatabase.mysql(this, host, port, database, username, password)
// or: RefineryDatabase.memory(this)  — no SQL connection, useful for tests

db.execute("INSERT INTO players (uuid, coins) VALUES (?, ?)", stmt -> {
    stmt.setString(1, player.getUniqueId().toString());
    stmt.setInt(2, 100);
});

List<Integer> coins = db.query(
    "SELECT coins FROM players WHERE uuid = ?",
    stmt -> stmt.setString(1, player.getUniqueId().toString()),
    rs -> rs.getInt("coins")
);
```

Async variants (`executeAsync`, `queryAsync`) return `CompletableFuture` and run off the main thread. Call `db.close()` in `onDisable()`.

---

### Event Bus

`RefineryBus` is a process-wide, string-channel pub/sub bus for cross-plugin communication that doesn't require a hard compile-time dependency. One plugin publishes on a channel name; any other plugin can subscribe to it without importing the publisher's classes.

```java
// Publisher (e.g. an economy plugin)
RefineryBus.get().publish("economy:balance-changed", new BalanceChangedPayload(uuid, newBalance));

// Subscriber (e.g. a cosmetics plugin reacting to it)
RefineryBus.get().subscribe("economy:balance-changed", BalanceChangedPayload.class, payload -> {
    // react without depending on the economy plugin's jar
});
```

Both sides just need to agree on the payload shape (a record works well) — document it per channel, or share it via a small common API module.

Use `BusSubscriptions` to group everything a plugin subscribes to, so it can be cleaned up in one call:

```java
public final class MyPlugin extends JavaPlugin {
    private final BusSubscriptions bus = new BusSubscriptions();

    @Override
    public void onEnable() {
        bus.on("economy:balance-changed", BalanceChangedPayload.class, payload -> { /* ... */ });
    }

    @Override
    public void onDisable() {
        bus.unsubscribeAll();
    }
}
```

Delivery is synchronous on the publishing thread, in subscriber priority order (highest first via the optional `priority` argument to `subscribe`). This is meant for lightweight signaling (notify, refresh, sync) — for anything that needs to be cancellable or intercepted mid-flow, use a real Bukkit event instead.

---

### Configuration

Extend `RefineryConfiguration` and annotate fields to define config entries. Nested sections are inner classes annotated with `@ConfigSection`.

```java
@ConfigFile("config.yml")
public class RefineryConfig extends RefineryConfiguration {

    @ConfigEntry(key = "debug")
    public boolean debug = false;

    public Database database = new Database();

    @ConfigSection("database")
    public static class Database {
        @ConfigEntry(key = "host")
        public String host = "localhost";

        @ConfigEntry(key = "port")
        public int port = 3306;
    }

    public RefineryConfig(JavaPlugin plugin) {
        super(plugin);
    }
}
```

Produces:

```yaml
debug: false
database:
  host: localhost
  port: 3306
```

Lifecycle:

```java
config.saveDefault()  // creates file with defaults, never overwrites existing values
config.load()         // reads file into live fields
config.reload()       // hot-swaps values without restart
config.save()         // writes current field values to disk
```

---

## Plugin API

Implement `RefineryPluginImplementation` to get convenience methods on your plugin:

```java
public final class MyPlugin extends JavaPlugin implements RefineryPluginImplementation {

    @Override
    public void onEnable() {
        logMessage("<green>MyPlugin has started!");
        getCommandRegistry().register(new MyCommand());
        getPluginManager().registerEvents(new MyListener(), this);
    }
}
```

| Method | Description |
|---|---|
| `logMessage(String)` | MiniMessage-formatted console log |
| `getCommandRegistry()` | Cached `CommandRegistry` instance |
| `getPluginManager()` | Bukkit plugin manager |
| `getConsoleSender()` | Console sender |
| `getServerImplementation()` | Version-specific `ServerImplementation` for the running server |

---

## License

© Refinery Team. All rights reserved.