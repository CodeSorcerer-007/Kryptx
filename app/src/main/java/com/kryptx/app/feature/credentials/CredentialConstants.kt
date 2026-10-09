package com.kryptx.app.feature.credentials

/**
 * Shared action constants, extra identifiers, and WebAuthn/AndroidX bundle keys
 * for Kryptx Credential Provider integration.
 */
object CredentialConstants {
    const val ACTION_ASSERT_PASSKEY = "com.kryptx.credentials.ACTION_ASSERT_PASSKEY"
    const val ACTION_GET_PASSWORD = "com.kryptx.credentials.ACTION_GET_PASSWORD"
    const val ACTION_CREATE_CREDENTIAL = "com.kryptx.credentials.ACTION_CREATE_CREDENTIAL"
    const val ACTION_UNLOCK_VAULT = "com.kryptx.credentials.ACTION_UNLOCK_VAULT"
    const val ACTION_SEARCH_VAULT = "com.kryptx.credentials.ACTION_SEARCH_VAULT"

    const val EXTRA_ITEM_ID = "com.kryptx.credentials.EXTRA_ITEM_ID"
    const val EXTRA_OPTION_ID = "com.kryptx.credentials.EXTRA_OPTION_ID"
    const val EXTRA_RP_ID = "com.kryptx.credentials.EXTRA_RP_ID"
    const val EXTRA_CREDENTIAL_TYPE = "com.kryptx.credentials.EXTRA_CREDENTIAL_TYPE"
    const val EXTRA_ORIGIN = "com.kryptx.credentials.EXTRA_ORIGIN"
    const val EXTRA_PACKAGE_NAME = "com.kryptx.credentials.EXTRA_PACKAGE_NAME"
    const val EXTRA_AUTH_TOKEN = "com.kryptx.credentials.EXTRA_AUTH_TOKEN"

    // Standard AndroidX / WebAuthn Bundle Keys
    const val BUNDLE_KEY_ID = "androidx.credentials.BUNDLE_KEY_ID"
    const val BUNDLE_KEY_PASSWORD = "androidx.credentials.BUNDLE_KEY_PASSWORD"
    const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
    const val BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON = "androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON"
    const val BUNDLE_KEY_REGISTRATION_RESPONSE_JSON = "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
    const val BUNDLE_KEY_CLIENT_DATA_HASH = "androidx.credentials.BUNDLE_KEY_CLIENT_DATA_HASH"
}
