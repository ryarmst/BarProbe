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
    }
}

rootProject.name = "barcode-workbench"

// Feature modules depend on :barcode:engine-api, never on a concrete engine, so
// either engine can be swapped by changing a single Hilt binding.
include(":app")
include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":barcode:engine-api")
include(":barcode:zint")
include(":barcode:reader")
include(":barcode:render")
include(":feature:generator")
include(":feature:scanner")
include(":feature:catalogue")
include(":feature:configpacks")
include(":feature:learn")
