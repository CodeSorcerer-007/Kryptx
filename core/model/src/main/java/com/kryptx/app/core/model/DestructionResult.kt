package com.kryptx.app.core.model

/**
 * Result of an emergency or panic self-destruct cryptographic erasure sequence.
 */
sealed class DestructionResult {
    /**
     * All cryptographic keys (volatile in-memory and hardware Android Keystore aliases),
     * encrypted database files, SQLite transaction logs, attachments, and preferences
     * were verified as permanently and irreversibly purged.
     */
    object Success : DestructionResult()

    /**
     * One or more phases of the destruction pipeline failed or could not be verified.
     *
     * @param keystorePurged Whether all hardware Keystore aliases were confirmed absent.
     * @param databasesPurged Whether database files and transaction journals were removed.
     * @param preferencesPurged Whether SharedPreferences metadata was purged.
     * @param errorMessage Detailed diagnostic message.
     */
    data class Incomplete(
        val keystorePurged: Boolean,
        val databasesPurged: Boolean,
        val preferencesPurged: Boolean,
        val errorMessage: String
    ) : DestructionResult()
}
