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
        // Tesseract4Android (Texterkennung) gibt es nur über JitPack.
        maven("https://jitpack.io") { content { includeGroup("cz.adaptech.tesseract4android") } }
    }
}

rootProject.name = "Cashdy"
include(":app")
