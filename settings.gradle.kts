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
// Multi-Module Architecture (Phase 2 — Physical Module Extraction Complete)
//
// These modules carve the monolithic :app into independently compilable,
// independently testable Gradle modules with physical source boundaries.
// Each module exposes only its public API surface; internal classes are not
// visible to sibling modules.
//
// Completed Module Extraction:
//  - :core:model    <- core data models, payloads, activity events (no deps)
//  - :core:crypto   <- Argon2id, AES-GCM, XChaCha20, UniFFI Rust bindings, breach check (depends on :core:model)
//  - :core:database <- SQLCipher, schema, migrations, repositories (depends on :core:crypto, :core:model)
//  - :core:security <- biometric auth, clipboard wipe, watchdog, anti-tampering (depends on :core:crypto, :core:database, :core:model)
//  - :app           <- Compose UI screens, viewmodels, design system, DI, navigation (depends on all core modules)
//
// Dependency graph (arrows = "depends on"):
//   :core:model    <- (zero dependencies)
//   :core:crypto   <- :core:model
//   :core:database <- :core:crypto, :core:model
//   :core:security <- :core:crypto, :core:database, :core:model
//   :app           <- all :core:* modules + :feature:*
// ─────────────────────────────────────────────────────────────────────────────
include(":core:model")
include(":core:crypto")
include(":core:database")
include(":core:security")
