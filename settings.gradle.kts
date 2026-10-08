pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Kryptx"
include(":app")

// ─────────────────────────────────────────────────────────────────────────────
// Multi-Module Architecture (Phase 1 — boundary declaration)
//
// These modules carve the monolithic :app into independently compilable,
// independently testable Gradle modules.  Each module exposes only its public
// API surface; internal classes are not visible to sibling modules.
//
// Migration strategy (zero breakage):
//  1. Modules are declared here and given empty build.gradle.kts stubs so
//     Gradle resolves the project paths.
//  2. Source is still physically in app/src/main/java — the module build
//     files point their sourceSets at the same directories so compilation
//     continues to work while the migration proceeds incrementally.
//  3. Once a module is fully migrated (imports resolved, tests green), its
//     source tree moves from app/src/main/java/com/kryptx/app/core/<pkg>
//     to <module>/src/main/java/com/kryptx/app/core/<pkg>.
//  4. :app then replaces its own copy with a project(":core:crypto") dep.
//
// Dependency graph (arrows = "depends on"):
//   :core:model   ← no deps
//   :core:crypto  ← :core:model
//   :core:database← :core:crypto, :core:model
//   :core:security← :core:crypto, :core:database, :core:model
//   :app          ← all :core:* modules + :feature:*
// ─────────────────────────────────────────────────────────────────────────────
include(":core:model")
include(":core:crypto")
include(":core:database")
include(":core:security")
