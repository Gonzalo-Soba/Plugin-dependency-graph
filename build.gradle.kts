plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.taskgraph"
version = "0.5.0"

// The dev server runs the same Paper build the plugin compiles against.
val minecraftVersion = "26.3"
val paperBuild = 222
val paperVersion = "$minecraftVersion.build.$paperBuild-stable"
val uaapiVersion = "3.0.0-beta-4"
// Optional at runtime: only the "+" tabs need it (see dev.taskgraph.packet).
val packetEventsVersion = "2.14.0"
val junitVersion = "6.1.3"
// Must match the Gson version exposed by paper-api, which provides it at runtime.
val gsonVersion = "2.14.0"
// Adventure version exposed by paper-api; only used to fetch its sources for API lookups.
val adventureVersion = "5.2.0"
// Brigadier version exposed by paper-api; only used to fetch its sources for API lookups.
val brigadierVersion = "1.3.11"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://nexus.frengor.com/repository/public/") {
        name = "frengor"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "codemc"
    }
}

val apiSources = configurations.create("apiSources") {
    isTransitive = false
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.frengor:ultimateadvancementapi:$uaapiVersion")
    compileOnly("com.github.retrooper:packetevents-spigot:$packetEventsVersion")

    apiSources("io.papermc.paper:paper-api:$paperVersion:sources")
    apiSources("com.frengor:ultimateadvancementapi:$uaapiVersion:sources")
    apiSources("com.github.retrooper:packetevents-api:$packetEventsVersion:sources")
    apiSources("com.github.retrooper:packetevents-spigot:$packetEventsVersion:sources")
    apiSources("net.kyori:adventure-api:$adventureVersion:sources")
    apiSources("net.kyori:adventure-text-serializer-legacy:$adventureVersion:sources")
    apiSources("com.mojang:brigadier:$brigadierVersion:sources")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:$gsonVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }
}

// Copies the sources jars of the compile-time APIs to build/api-sources for signature lookups.
tasks.register<Sync>("downloadApiSources") {
    from(apiSources)
    into(layout.buildDirectory.dir("api-sources"))
}

// Seeds the local test server with dev-server/ files. Existing files are kept, so local edits survive.
val prepareDevServer = tasks.register<Copy>("prepareDevServer") {
    from(layout.projectDirectory.dir("dev-server")) {
        // Sample data is copied by hand when a test needs it (see README).
        exclude("samples/**")
    }
    into(layout.projectDirectory.dir("run"))
    eachFile {
        if (destinationDir.resolve(path).exists()) {
            exclude()
        }
    }
}

// The plugin jar from the jar task is added automatically by run-paper.
// `-PnoPacketEvents` starts it without PacketEvents, to try the plugin without the "+" tabs.
tasks.runServer {
    dependsOn(prepareDevServer)
    minecraftVersion(minecraftVersion)
    build(paperBuild)
    downloadPlugins {
        modrinth("ultimateadvancementapi", uaapiVersion)
        if (!project.hasProperty("noPacketEvents")) {
            modrinth("packetevents", "$packetEventsVersion+spigot")
        }
    }
    // Logs every advancement tab packet (see PacketEventsTabPackets).
    systemProperty("taskgraph.debugTabs", "true")
}
