[![Version](https://repository.reaudacity.online/api/badge/latest/releases/xyz/refineryteam/refinerycore/?color=40c14a&name=RefineryCore&prefix=v)](https://modrinth.com/projects/refinerycore)
[![Build](https://github.com/RefineryTeam/RefineryCore/actions/workflows/build.yml/badge.svg)](https://github.com/RefineryTeam/RefineryCore/actions/workflows/build.yml)
[![Publish](https://github.com/RefineryTeam/RefineryCore/actions/workflows/publish.yml/badge.svg)](https://github.com/RefineryTeam/RefineryCore/actions/workflows/publish.yml)

# RefineryCore

A modern framework for developing Paper plugins with annotation-driven commands, GUIs, configuration, scoreboards, database utilities, and more.

📖 **Full documentation:** https://wiki.refineryteam.xyz

---

## Installation

### Maven

```xml
<repository>
    <id>reaudacity-releases</id>
    <url>https://repository.reaudacity.online/releases</url>
</repository>

<dependency>
    <groupId>xyz.refineryteam</groupId>
    <artifactId>refinerycore</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

### Gradle (Kotlin)

```kotlin
repositories {
    maven("https://repository.reaudacity.online/releases")
}

dependencies {
    compileOnly("xyz.refineryteam:refinerycore:VERSION")
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url "https://repository.reaudacity.online/releases" }
}

dependencies {
    compileOnly "xyz.refineryteam:refinerycore:VERSION"
}
```

> [!IMPORTANT]
> If your plugin depends on RefineryCore at runtime, add it to the `dependencies` section of your `plugin.yml`.

---

## Requirements

- Java 21+
- Paper 1.21+

---

## Documentation

Everything else is covered in the wiki, including:

- Getting Started
- Commands
- GUIs
- Configuration
- Scoreboards
- Database
- Event Bus
- ItemBuilder
- Plugin API
- Scheduler
- Server Implementation
- Storage

➡️ **Wiki:** https://wiki.refineryteam.xyz

---

## Support

- Documentation: https://wiki.refineryteam.xyz
- Issues: https://github.com/RefineryTeam/RefineryCore/issues

---

## License

© Refinery Team. All rights reserved.  
> Licensed under the MIT license.