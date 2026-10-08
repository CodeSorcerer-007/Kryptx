package com.kryptx.app.core.security

/**
 * Documents that the annotated function requires an active, unlocked vault session.
 *
 * Functions bearing this annotation will throw [IllegalStateException] (via
 * [com.kryptx.app.core.database.KryptxDatabaseHelper.writableDatabase] /
 * [com.kryptx.app.core.database.KryptxDatabaseHelper.readableDatabase]) or return null / an error
 * result (via [VaultSessionManager.withVaultKey]) if the vault is locked when they are called.
 *
 * ## Contract
 * - Callers MUST ensure the vault is unlocked before invoking.
 * - ViewModels that call annotated functions should guard on [VaultSessionManager.isUnlocked].
 * - All key material passed to or returned from annotated functions MUST be zeroized immediately
 *   after use using [com.kryptx.app.core.crypto.SecureMemory.wipe].
 *
 * ## Usage
 * ```kotlin
 * @RequiresVaultKey
 * suspend fun saveItem(item: VaultItem, vaultKey: ByteArray): Boolean
 * ```
 *
 * This is a documentation-only annotation with `@Retention(SOURCE)` — it generates no bytecode
 * and carries zero runtime overhead. It is read by the IDE, code review tools, and any future
 * static analysis rules.
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class RequiresVaultKey
