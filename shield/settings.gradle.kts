pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-external.rustore.ru/repository/maven-rustore-exposed") {
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "family-shield"

include(":shared")
include(":server")
// The server Docker image is built without the Android SDK.
if (System.getenv("SHIELD_SERVER_ONLY") != "true") include(":app")
