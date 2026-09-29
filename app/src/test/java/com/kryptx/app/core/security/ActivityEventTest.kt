package com.kryptx.app.core.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActivityEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testActivityEventSerializationAndDeserialization() {
        val event = ActivityEvent(
            timestamp = 1727610000000L,
            type = "Security",
            description = "Vault unlocked via Hardware Key"
        )

        val serialized = json.encodeToString(event)
        assertNotNull(serialized)

        val deserialized = json.decodeFromString<ActivityEvent>(serialized)
        assertEquals(event.timestamp, deserialized.timestamp)
        assertEquals(event.type, deserialized.type)
        assertEquals(event.description, deserialized.description)
    }

    @Test
    fun testEventTypesIntegrity() {
        val types = listOf("Unlock", "Lock", "Security", "Autofill", "Backup")
        for (t in types) {
            val event = ActivityEvent(
                timestamp = System.currentTimeMillis(),
                type = t,
                description = "Event of type $t executed"
            )
            assertEquals(t, event.type)
        }
    }
}
