pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.bg-software.com/repository/api/")
        maven("https://jitpack.io")
        maven("https://repo.extendedclip.com/releases/")
    }
}

rootProject.name = "SatisSkyFactory"
