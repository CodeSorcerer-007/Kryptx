package com.kryptx.app.core.security

/**
 * Indicates that the annotated element (return value, parameter, or property) contains sensitive
 * cryptographic material (such as plaintext passwords, decrypted keys, or master entropy)
 * that the consumer is strictly obligated to zeroize immediately after use
 * using [com.kryptx.app.core.security.SecureMemory.wipe] or equivalent in-place clearing.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.EXPRESSION
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class MustBeWiped(
    val reason: String = "Contains sensitive cryptographic material that caller must wipe"
)
