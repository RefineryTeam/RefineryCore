plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.3.1"
}

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("org.projectlombok:lombok:1.18.46")

    compileOnly("com.zaxxer:HikariCP:7.0.2")
}

bukkitPluginYaml {
    main = "xyz.refineryteam.refinerycore.plugin.RefineryCorePlugin"
    description = "The core heart plugin of RefineryTeam's projects."
    apiVersion = "1.21.11"

    authors.addAll("RefineryTeam")
    website = "https://refineryteam.xyz"

    libraries.addAll("com.zaxxer:HikariCP:7.0.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}
