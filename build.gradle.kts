plugins {
    java
}

group = "kr.example"
version = "1.0.0"

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21.1-R0.1-SNAPSHOT")
val superiorSkyblockApiVersion = providers.gradleProperty("superiorSkyblockApiVersion").orElse("2024.1")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:${superiorSkyblockApiVersion.get()}")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("SatisSkyFactory")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
