package com.kryptx.app.core.database

/**
 * Unified interface for Kryptx Vault operations, composing focused domain
 * interfaces for Authentication & Keys, CRUD Data Storage, Trash Lifecycle,
 * and Security Auditing in adherence to the Interface Segregation Principle.
 */
interface VaultRepository : VaultAuthRepository, VaultCrudRepository, VaultTrashRepository, VaultAuditRepository
