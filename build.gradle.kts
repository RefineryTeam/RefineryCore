plugins {
    id("java-library")
    id("maven-publish")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.3.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Plain Paper API — NOT paperweight.userdev. This plugin only uses public Bukkit/Paper
    // API (no NMS), so compiling against a mapped server jar isn't necessary and only serves
    // to lock the build to one exact version. Compiling against the lowest supported API
    // version keeps the plugin loadable on any Paper build >= apiVersion below.
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("org.jspecify:jspecify:1.0.0")

    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("org.projectlombok:lombok:1.18.46")

    compileOnly("com.zaxxer:HikariCP:7.0.2")
}

bukkitPluginYaml {
    main = "xyz.refineryteam.refinerycore.plugin.RefineryCorePlugin"
    description = "The core heart plugin of RefineryTeam's projects."
    // The lowest supported Paper API version — 1.20 means "1.20 and every version after it".
    // Combined with the plain paper-api dependency above (instead of paperweight.userdev),
    // this plugin's jar will load on any Paper build from 1.20 through the latest 1.21.x.
    apiVersion = "1.20"

    authors.addAll("RefineryTeam")
    website = "https://refineryteam.xyz"

    libraries.addAll("com.zaxxer:HikariCP:7.0.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)

    withSourcesJar()
}

// maven-publish (and the default jar task) names the produced artifact after
// this, lowercased, so the file that actually lands in Reposilite is
// "refinerycore-<version>.jar" regardless of casing anywhere else (project
// dir name, GitHub repo name, etc). This is the one setting that controls
// the *published* filename — renaming a copy in build/libs after the fact
// has no effect on what `publish` uploads, since it re-resolves the
// artifact from this name, not from whatever sits in build/libs.
base.archivesName = project.name.lowercase()

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = project.name.lowercase()
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "Reposilite"
            url = uri("https://repository.reaudacity.online/releases")

            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
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