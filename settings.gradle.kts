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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Caddie"
include(":app")

// Study applications are research fixtures, not part of the default Caddie build.
// Keep them available for the study workflow without putting them in a normal APK
// build or IDE project unless the caller explicitly opts in.
if (providers.gradleProperty("includeStudyFixtures").map { it.toBoolean() }.getOrElse(false)) {
    include(":research:fixtures:study-bank")
    include(":research:fixtures:study-calendar")
    include(":research:fixtures:study-mail")
    include(":research:fixtures:study-telegram")
    include(":research:fixtures:study-gallery")
    include(":research:fixtures:study-notes")
    include(":research:fixtures:study-music")
    include(":research:fixtures:study-training-sandbox")
}
