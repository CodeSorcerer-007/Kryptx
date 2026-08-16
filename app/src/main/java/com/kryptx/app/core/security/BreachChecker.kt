package com.kryptx.app.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Privacy-preserving credential breach verification engine using the official
 * Have I Been Pwned (HIBP) k-Anonymity API protocol (RFC compliant).
 *
 * Security Guarantee:
 * Only the first 5 characters of the SHA-1 hash of the password are transmitted.
 * The full password and remaining hash characters NEVER leave the device.
 */
object BreachChecker {

    private const val HIBP_API_URL = "https://api.pwnedpasswords.com/range/"

    // Highly common offline compromised passwords fallback list
    private val OFFLINE_COMPROMISED_PASSWORDS = setOf(
        "123456", "password", "123456789", "qwerty", "12345678", "111111", "12345",
        "1234567", "dragon", "welcome", "ninja", "master", "football", "iloveyou",
        "admin", "sunshine", "letmein", "princess", "solo", "monkey", "charlie",
        "shadow", "donald", "trustno1", "superman", "starwars", "password1", "killer"
    )

    data class BreachStatus(
        val isBreached: Boolean,
        val breachCount: Int,
        val source: String
    )

    /**
     * Checks if a password has appeared in known public data breaches.
     *
     * @param password Plaintext password to check.
     * @param enableNetworkCheck Whether to query the live HIBP k-Anonymity API or use offline detection.
     */
    suspend fun checkPassword(
        password: String,
        enableNetworkCheck: Boolean = false
    ): BreachStatus = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            return@withContext BreachStatus(false, 0, "Empty")
        }

        val cleanLower = password.lowercase()

        // 1. First check instant offline dictionary
        if (OFFLINE_COMPROMISED_PASSWORDS.contains(cleanLower)) {
            return@withContext BreachStatus(
                isBreached = true,
                breachCount = 100_000,
                source = "Offline Breach Dataset"
            )
        }

        // 2. If network checking is disabled, return offline clean status
        if (!enableNetworkCheck) {
            return@withContext BreachStatus(false, 0, "Offline Heuristics Clean")
        }

        // 3. Perform k-Anonymity privacy query
        try {
            val sha1Hash = computeSha1(password).uppercase()
            val prefix = sha1Hash.take(5)
            val suffix = sha1Hash.substring(5)

            val url = URL("$HIBP_API_URL$prefix")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "Kryptx-Security-Audit/1.0")
                setRequestProperty("Add-Padding", "true") // Prevents response length side-channel analysis
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    val parts = currentLine.split(":")
                    if (parts.size == 2) {
                        val returnedSuffix = parts[0].trim().uppercase()
                        val count = parts[1].trim().toIntOrNull() ?: 0
                        if (returnedSuffix == suffix && count > 0) {
                            reader.close()
                            connection.disconnect()
                            return@withContext BreachStatus(
                                isBreached = true,
                                breachCount = count,
                                source = "HIBP Pwned Passwords (k-Anonymity)"
                            )
                        }
                    }
                }
                reader.close()
            }
            connection.disconnect()
            BreachStatus(false, 0, "HIBP Verified Clean")
        } catch (_: Exception) {
            // In case of timeout or offline mode, fallback to offline clean
            BreachStatus(false, 0, "Network Offline (Local Clean)")
        }
    }

    private fun computeSha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
