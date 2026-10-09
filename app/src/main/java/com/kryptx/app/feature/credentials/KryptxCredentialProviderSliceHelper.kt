package com.kryptx.app.feature.credentials

import android.app.PendingIntent
import android.app.slice.Slice
import android.app.slice.SliceSpec
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.kryptx.app.R
import java.util.Collections

/**
 * Sovereign framework Slice constructor for Android 14+ Credential Manager.
 * Builds compliant Slices for CredentialEntry, CreateEntry, and Action without requiring
 * external cloud or Play Services dependencies.
 */
@RequiresApi(Build.VERSION_CODES.P)
object KryptxCredentialProviderSliceHelper {

    // Credential Types
    const val TYPE_PASSWORD_CREDENTIAL = "android.credentials.TYPE_PASSWORD_CREDENTIAL"
    const val TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
    const val TYPE_PUBLIC_KEY_CREDENTIAL_FRAMEWORK = "android.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"

    // CredentialEntry Slice hints (androidx.credentials.provider.credentialEntry)
    private const val SPEC_TYPE_CREDENTIAL_ENTRY = "CredentialEntry"
    private const val SPEC_REVISION_CREDENTIAL_ENTRY = 1
    private const val HINT_OPTION_ID = "androidx.credentials.provider.credentialEntry.SLICE_HINT_OPTION_ID"
    private const val HINT_DEDUPLICATION_ID = "androidx.credentials.provider.credentialEntry.SLICE_HINT_DEDUPLICATION_ID"
    private const val HINT_TYPE_DISPLAY_NAME = "androidx.credentials.provider.credentialEntry.SLICE_HINT_TYPE_DISPLAY_NAME"
    private const val HINT_TITLE = "androidx.credentials.provider.credentialEntry.SLICE_HINT_USER_NAME"
    private const val HINT_SUBTITLE = "androidx.credentials.provider.credentialEntry.SLICE_HINT_CREDENTIAL_TYPE_DISPLAY_NAME"
    private const val HINT_ICON = "androidx.credentials.provider.credentialEntry.SLICE_HINT_PROFILE_ICON"
    private const val HINT_PENDING_INTENT = "androidx.credentials.provider.credentialEntry.SLICE_HINT_PENDING_INTENT"
    private const val HINT_AUTO_ALLOWED = "androidx.credentials.provider.credentialEntry.SLICE_HINT_AUTO_ALLOWED"
    private const val HINT_LAST_USED_TIME_MILLIS = "androidx.credentials.provider.credentialEntry.SLICE_HINT_LAST_USED_TIME_MILLIS"

    // CreateEntry Slice hints (androidx.credentials.provider.createEntry)
    private const val SPEC_TYPE_CREATE_ENTRY = "CreateEntry"
    private const val SPEC_REVISION_CREATE_ENTRY = 1
    private const val CREATE_HINT_ACCOUNT_NAME = "androidx.credentials.provider.createEntry.SLICE_HINT_USER_PROVIDER_ACCOUNT_NAME"
    private const val CREATE_HINT_NOTE = "androidx.credentials.provider.createEntry.SLICE_HINT_NOTE"
    private const val CREATE_HINT_ICON = "androidx.credentials.provider.createEntry.SLICE_HINT_PROFILE_ICON"
    private const val CREATE_HINT_PENDING_INTENT = "androidx.credentials.provider.createEntry.SLICE_HINT_PENDING_INTENT"
    private const val CREATE_HINT_AUTO_SELECT_ALLOWED = "androidx.credentials.provider.createEntry.SLICE_HINT_AUTO_SELECT_ALLOWED"

    // Action Slice hints (androidx.credentials.provider.action)
    private const val SPEC_TYPE_ACTION = "Action"
    private const val SPEC_REVISION_ACTION = 0
    private const val ACTION_HINT_TITLE = "androidx.credentials.provider.action.HINT_ACTION_TITLE"
    private const val ACTION_HINT_SUBTITLE = "androidx.credentials.provider.action.HINT_ACTION_SUBTEXT"
    private const val ACTION_HINT_PENDING_INTENT = "androidx.credentials.provider.action.SLICE_HINT_PENDING_INTENT"

    /**
     * Builds a framework [Slice] for a [android.service.credentials.CredentialEntry].
     */
    fun createCredentialEntrySlice(
        context: Context,
        optionId: String,
        type: String,
        title: String,
        subtitle: String,
        typeDisplayName: String,
        pendingIntent: PendingIntent,
        lastUsedTimeMillis: Long? = null
    ): Slice {
        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        val sliceSpec = SliceSpec(SPEC_TYPE_CREDENTIAL_ENTRY, SPEC_REVISION_CREDENTIAL_ENTRY)
        val builder = Slice.Builder(Uri.EMPTY, sliceSpec)
            .addText(optionId, null, listOf(HINT_OPTION_ID))
            .addText(title, null, listOf(HINT_DEDUPLICATION_ID))
            .addText(typeDisplayName, null, listOf(HINT_TYPE_DISPLAY_NAME))
            .addText(title, null, listOf(HINT_TITLE))
            .addText(subtitle, null, listOf(HINT_SUBTITLE))
            .addText("false", null, listOf(HINT_AUTO_ALLOWED))
            .addIcon(icon, null, listOf(HINT_ICON))

        if (lastUsedTimeMillis != null && lastUsedTimeMillis > 0L) {
            builder.addLong(lastUsedTimeMillis, null, listOf(HINT_LAST_USED_TIME_MILLIS))
        }

        builder.addAction(
            pendingIntent,
            Slice.Builder(builder)
                .addHints(Collections.singletonList(HINT_PENDING_INTENT))
                .build(),
            null
        )

        return builder.build()
    }

    /**
     * Builds a framework [Slice] for a [android.service.credentials.CreateEntry].
     */
    fun createCreateEntrySlice(
        context: Context,
        accountName: String,
        description: String,
        pendingIntent: PendingIntent
    ): Slice {
        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        val sliceSpec = SliceSpec(SPEC_TYPE_CREATE_ENTRY, SPEC_REVISION_CREATE_ENTRY)
        val builder = Slice.Builder(Uri.EMPTY, sliceSpec)
            .addText(accountName, null, listOf(CREATE_HINT_ACCOUNT_NAME))
            .addText(description.take(300), null, listOf(CREATE_HINT_NOTE))
            .addIcon(icon, null, listOf(CREATE_HINT_ICON))
            .addText("false", null, listOf(CREATE_HINT_AUTO_SELECT_ALLOWED))

        builder.addAction(
            pendingIntent,
            Slice.Builder(builder)
                .addHints(Collections.singletonList(CREATE_HINT_PENDING_INTENT))
                .build(),
            null
        )

        return builder.build()
    }

    /**
     * Builds a framework [Slice] for a [android.service.credentials.Action].
     */
    fun createActionSlice(
        title: String,
        subtitle: String?,
        pendingIntent: PendingIntent
    ): Slice {
        val sliceSpec = SliceSpec(SPEC_TYPE_ACTION, SPEC_REVISION_ACTION)
        val builder = Slice.Builder(Uri.EMPTY, sliceSpec)
            .addText(title, null, listOf(ACTION_HINT_TITLE))

        if (!subtitle.isNullOrBlank()) {
            builder.addText(subtitle, null, listOf(ACTION_HINT_SUBTITLE))
        }

        builder.addAction(
            pendingIntent,
            Slice.Builder(builder)
                .addHints(Collections.singletonList(ACTION_HINT_PENDING_INTENT))
                .build(),
            null
        )

        return builder.build()
    }
}
