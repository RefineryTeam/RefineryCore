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

---

## License

© Refinery Team. All rights reserved.