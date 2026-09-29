package com.kryptx.app.core.database

import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.security.VaultSessionManager

/**
 * High-performance, modular implementation of [VaultRepository].
 *
 * Deconstructs the former monolithic repository God object into specialized,
 * cohesive domain repositories ([VaultAuthRepository], [VaultCrudRepository],
 * [VaultTrashRepository], and [VaultAuditRepository]) via Kotlin interface delegation.
 */
class VaultRepositoryImpl(
    authRepo: VaultAuthRepository,
    crudRepo: VaultCrudRepository,
    trashRepo: VaultTrashRepository,
    auditRepo: VaultAuditRepository
) : VaultRepository,
    VaultAuthRepository by authRepo,
    VaultCrudRepository by crudRepo,
    VaultTrashRepository by trashRepo,
    VaultAuditRepository by auditRepo {

    constructor(
        dbHelper: KryptxDatabaseHelper,
        decoyDbHelper: KryptxDatabaseHelper,
        sessionManager: VaultSessionManager,
        keystoreManager: KeystoreManager,
        preferencesRepository: IPreferencesRepository? = null
    ) : this(
        auditRepo = VaultAuditRepositoryImpl(dbHelper, sessionManager),
        dbHelper = dbHelper,
        decoyDbHelper = decoyDbHelper,
        sessionManager = sessionManager,
        keystoreManager = keystoreManager,
        preferencesRepository = preferencesRepository
    )

    private constructor(
        auditRepo: VaultAuditRepositoryImpl,
        dbHelper: KryptxDatabaseHelper,
        decoyDbHelper: KryptxDatabaseHelper,
        sessionManager: VaultSessionManager,
        keystoreManager: KeystoreManager,
        preferencesRepository: IPreferencesRepository?
    ) : this(
        authRepo = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onAuditInvalidated = { auditRepo.invalidateAuditCache() }
        ),
        crudRepo = VaultCrudRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            onAuditInvalidated = { auditRepo.invalidateAuditCache() }
        ),
        trashRepo = VaultTrashRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            onAuditInvalidated = { auditRepo.invalidateAuditCache() }
        ),
        auditRepo = auditRepo
    )
}