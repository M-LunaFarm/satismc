plugins {
    java
}

group = "kr.seungmin"
version = "1.0.0"

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21.1-R0.1-SNAPSHOT")
val superiorSkyblockApiVersion = providers.gradleProperty("superiorSkyblockApiVersion").orElse("2024.1")
val placeholderApiVersion = providers.gradleProperty("placeholderApiVersion").orElse("2.12.2")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:${superiorSkyblockApiVersion.get()}")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:${placeholderApiVersion.get()}")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
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

tasks.test {
    useJUnitPlatform()
}
