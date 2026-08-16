package com.kryptx.app.core.model

/**
 * Robust Result encapsulation type for security, cryptographic, and repository operations.
 */
sealed class KryptxResult<out T> {
    data class Success<out T>(val data: T) : KryptxResult<T>()
    data class Error(
        val type: KryptxErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : KryptxResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> defaultValue
    }

    inline fun onSuccess(action: (T) -> Unit): KryptxResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Error) -> Unit): KryptxResult<T> {
        if (this is Error) action(this)
        return this
    }

    inline fun <R> map(transform: (T) -> R): KryptxResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
}

enum class KryptxErrorType {
    WRONG_PASSWORD,
    VAULT_LOCKED,
    VAULT_NOT_FOUND,
    VAULT_ALREADY_EXISTS,
    KEYSTORE_INVALIDATED,
    BIOMETRICS_NOT_AVAILABLE,
    BIOMETRICS_FAILED,
    CORRUPTED_CIPHERTEXT,
    DECRYPTION_FAILED,
    DATABASE_ERROR,
    IMPORT_PARSE_FAILED,
    EXPORT_FAILED,
    UNKNOWN
}
