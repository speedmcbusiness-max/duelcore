plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.duelcore"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://m2.dv8tion.net/releases")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Paper API — 1.21 is the api-version, works across the 26.x year-based
    // releases (26.1, 26.2, ...) since Mojang kept api-version pinned at 1.21.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Discord bot
    implementation("net.dv8tion:JDA:5.2.1") {
        exclude(module = "opus-java")
    }

    // Database
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    // Config / text
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Optional soft-dependency: only needed to compile the placeholder expansion
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Relocate shaded libs to avoid clashing with other plugins on the server
        relocate("net.dv8tion", "net.duelcore.libs.dv8tion")
        relocate("com.zaxxer.hikari", "net.duelcore.libs.hikari")
        relocate("com.mysql", "net.duelcore.libs.mysql")
        relocate("org.spongepowered.configurate", "net.duelcore.libs.configurate")
        relocate("com.google.gson", "net.duelcore.libs.gson")
        relocate("com.google.protobuf", "net.duelcore.libs.protobuf")
        relocate("org.slf4j", "net.duelcore.libs.slf4j")
        minimize()
    }
    build {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = "UTF-8"
    }
}
