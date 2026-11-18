package com.kryptx.app.core.database

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files

class DatabaseStorageCleanupTest {

    @Test
    fun testDeleteDatabaseFileOverwritesAndPurgesSidecarsAndPrefs() {
        val tempDir = Files.createTempDirectory("db_cleanup_test").toFile()
        try {
            val dbName = "test_vault.db"
            val dbFile = File(tempDir, dbName)
            dbFile.writeBytes(ByteArray(1024) { 1 })

            val walFile = File(tempDir, "$dbName-wal")
            walFile.writeBytes(ByteArray(512) { 2 })

            val shmFile = File(tempDir, "$dbName-shm")
            shmFile.writeBytes(ByteArray(256) { 3 })

            val journalFile = File(tempDir, "$dbName-journal")
            journalFile.writeBytes(ByteArray(128) { 4 })

            val sharedPrefsDir = File(tempDir, "shared_prefs")
            sharedPrefsDir.mkdirs()
            val metaFile = File(sharedPrefsDir, "kryptx_metadata_prefs.xml")
            metaFile.writeText("<map><string name=\"test\">value</string></map>")

            val mockContext = Mockito.mock(Context::class.java)
            val appInfo = ApplicationInfo().apply { dataDir = tempDir.absolutePath }
            Mockito.`when`(mockContext.applicationInfo).thenReturn(appInfo)
            Mockito.`when`(mockContext.getDatabasePath(dbName)).thenReturn(dbFile)
            Mockito.`when`(mockContext.deleteDatabase(dbName)).thenAnswer {
                dbFile.delete()
            }

            val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
            Mockito.`when`(mockEditor.clear()).thenReturn(mockEditor)
            Mockito.`when`(mockEditor.commit()).thenReturn(true)

            val mockPrefs = Mockito.mock(SharedPreferences::class.java)
            Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
            Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockPrefs)

            val helper = KryptxDatabaseHelper(mockContext, dbName)
            val result = helper.deleteDatabaseFile()

            assertTrue("Result must report success", result.isSuccess)
            assertTrue("Main DB must be reported deleted", result.mainDbDeleted)
            assertTrue("WAL must be reported deleted", result.walDeleted)
            assertTrue("SHM must be reported deleted", result.shmDeleted)
            assertTrue("Journal must be reported deleted", result.journalDeleted)
            assertTrue("Secure prefs must be reported cleared", result.securePrefsCleared)
            assertTrue("Metadata XML file must be reported deleted", result.metadataFileDeleted)

            assertFalse("WAL file must physically not exist", walFile.exists())
            assertFalse("SHM file must physically not exist", shmFile.exists())
            assertFalse("Journal file must physically not exist", journalFile.exists())
            assertFalse("Metadata file must physically not exist", metaFile.exists())
            assertFalse("DB file must physically not exist", dbFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteDatabaseFileReportsIncompleteWhenSidecarCannotBeDeleted() {
        val tempDir = Files.createTempDirectory("db_cleanup_fail_test").toFile()
        try {
            val dbName = "test_locked_vault.db"
            val dbFile = File(tempDir, dbName)
            dbFile.writeBytes(ByteArray(512) { 1 })

            // Create a non-empty directory in place of wal file so File.delete() fails
            val walDir = File(tempDir, "$dbName-wal")
            walDir.mkdirs()
            File(walDir, "lock.bin").writeBytes(byteArrayOf(1, 2, 3))

            val mockContext = Mockito.mock(Context::class.java)
            val appInfo = ApplicationInfo().apply { dataDir = tempDir.absolutePath }
            Mockito.`when`(mockContext.applicationInfo).thenReturn(appInfo)
            Mockito.`when`(mockContext.getDatabasePath(dbName)).thenReturn(dbFile)
            Mockito.`when`(mockContext.deleteDatabase(dbName)).thenAnswer {
                dbFile.delete()
            }

            val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
            Mockito.`when`(mockEditor.clear()).thenReturn(mockEditor)
            Mockito.`when`(mockEditor.commit()).thenReturn(true)

            val mockPrefs = Mockito.mock(SharedPreferences::class.java)
            Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
            Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockPrefs)

            val helper = KryptxDatabaseHelper(mockContext, dbName)
            val result = helper.deleteDatabaseFile()

            assertFalse("Result must report failure when wal cannot be deleted", result.isSuccess)
            assertFalse("walDeleted must be false", result.walDeleted)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteDatabaseFileReportsFailureWhenSidecarCannotBeOverwritten() {
        val tempDir = Files.createTempDirectory("db_overwrite_fail_test").toFile()
        try {
            val dbName = "test_readonly_vault.db"
            val dbFile = File(tempDir, dbName)
            dbFile.writeBytes(ByteArray(512) { 1 })

            val walFile = File(tempDir, "$dbName-wal")
            walFile.writeBytes(ByteArray(256) { 2 })
            walFile.setReadOnly()

            val mockContext = Mockito.mock(Context::class.java)
            val appInfo = ApplicationInfo().apply { dataDir = tempDir.absolutePath }
            Mockito.`when`(mockContext.applicationInfo).thenReturn(appInfo)
            Mockito.`when`(mockContext.getDatabasePath(dbName)).thenReturn(dbFile)
            Mockito.`when`(mockContext.deleteDatabase(dbName)).thenAnswer {
                dbFile.delete()
            }

            val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
            Mockito.`when`(mockEditor.clear()).thenReturn(mockEditor)
            Mockito.`when`(mockEditor.commit()).thenReturn(true)

            val mockPrefs = Mockito.mock(SharedPreferences::class.java)
            Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
            Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockPrefs)

            val helper = KryptxDatabaseHelper(mockContext, dbName)
            val result = helper.deleteDatabaseFile()

            assertFalse("Result must report failure when wal cannot be securely overwritten", result.isSuccess)
            assertFalse("walDeleted must be false", result.walDeleted)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
