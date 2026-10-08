// :core:model — Domain entities, value objects, and vault item representations.
//
// Zero external dependencies by design: this module is a pure Kotlin data model
// layer with no Android framework classes, no crypto, no DI.  Every other module
// depends on :core:model; :core:model depends on nothing.
//
// Migration status: source currently lives in :app.
//   Target path: core/model/src/main/java/com/kryptx/app/core/model/
//   Current path: app/src/main/java/com/kryptx/app/core/model/  (still in :app)
//
// To complete the migration for this module:
//   1. Move the source tree here.
//   2. Remove the classes from :app/src/main/java/com/kryptx/app/core/model/.
//   3. Add implementation(project(":core:model")) to :app/build.gradle.kts.

// plugins {
//     alias(libs.plugins.kotlin.jvm) apply false   // applied when source is migrated
// }

// Placeholder — Gradle resolves this project path so :app and sibling modules
// can declare project(":core:model") dependencies without build errors.
// The build tasks become active once the `plugins {}` block is uncommented and
// source files are moved here.
