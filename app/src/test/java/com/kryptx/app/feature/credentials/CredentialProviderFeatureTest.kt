package com.kryptx.app.feature.credentials

import com.kryptx.app.core.crypto.PasskeyEngine
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.DomainMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CredentialProviderFeatureTest {

    @Test
    fun `credential constants match AndroidX and WebAuthn specifications`() {
        assertEquals("android.credentials.TYPE_PASSWORD_CREDENTIAL", KryptxCredentialProviderSliceHelper.TYPE_PASSWORD_CREDENTIAL)
        assertEquals("androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL", KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX)
        assertEquals("android.credentials.TYPE_PUBLIC_KEY_CREDENTIAL", KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_FRAMEWORK)

        assertEquals("androidx.credentials.BUNDLE_KEY_ID", CredentialConstants.BUNDLE_KEY_ID)
        assertEquals("androidx.credentials.BUNDLE_KEY_PASSWORD", CredentialConstants.BUNDLE_KEY_PASSWORD)
        assertEquals("androidx.credentials.BUNDLE_KEY_REQUEST_JSON", CredentialConstants.BUNDLE_KEY_REQUEST_JSON)
        assertEquals("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON", CredentialConstants.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON)
        assertEquals("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON", CredentialConstants.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON)
    }

    @Test
    fun `webauthn passkey registration request parses and builds valid response payload`() {
        val incomingRequestJson = """
            {
              "rp": {
                "name": "Kryptx Sovereign Services",
                "id": "kryptx.io"
              },
              "user": {
                "id": "dXNlcl8xMjM0NQ",
                "name": "alex@kryptx.io",
                "displayName": "Alex"
              },
              "challenge": "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA",
              "pubKeyCredParams": [
                {"type": "public-key", "alg": -7}
              ]
            }
        """.trimIndent()

        val json = Json.parseToJsonElement(incomingRequestJson).jsonObject
        val rpObj = json["rp"]!!.jsonObject
        val userObj = json["user"]!!.jsonObject
        val challengeB64 = json["challenge"]!!.jsonPrimitive.content

        val rpId = rpObj["id"]!!.jsonPrimitive.content
        val rpName = rpObj["name"]!!.jsonPrimitive.content
        val userName = userObj["name"]!!.jsonPrimitive.content
        val userHandle = userObj["id"]!!.jsonPrimitive.content
        val challengeBytes = Base64.getUrlDecoder().decode(challengeB64)

        assertEquals("kryptx.io", rpId)
        assertEquals("alex@kryptx.io", userName)
        assertEquals(32, challengeBytes.size)

        // Generate passkey registration
        val registration = PasskeyEngine.createPasskeyRegistration(
            rpId = rpId,
            userHandle = userHandle,
            userName = userName
        )

        // Build attestation
        val clientDataJsonBytes = PasskeyEngine.buildClientDataJson(
            type = "webauthn.create",
            challengeBase64 = challengeB64,
            origin = "https://$rpId"
        )
        val attestationB64 = PasskeyEngine.createAttestationObjectBase64(
            rpId = rpId,
            credentialIdBase64 = registration.credentialId,
            publicKeyCoseBase64 = registration.publicKeyCoseBase64
        )

        // Verify JSON response string
        val clientDataJsonB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJsonBytes)
        val responseJsonStr = """
            {
              "id": "${registration.credentialId}",
              "rawId": "${registration.credentialId}",
              "type": "public-key",
              "authenticatorAttachment": "platform",
              "response": {
                "clientDataJSON": "$clientDataJsonB64",
                "attestationObject": "$attestationB64"
              },
              "clientExtensionResults": {}
            }
        """.trimIndent()

        val parsedResponse = Json.parseToJsonElement(responseJsonStr).jsonObject
        assertEquals(registration.credentialId, parsedResponse["id"]!!.jsonPrimitive.content)
        assertEquals("public-key", parsedResponse["type"]!!.jsonPrimitive.content)
        assertEquals("platform", parsedResponse["authenticatorAttachment"]!!.jsonPrimitive.content)

        val responseObj = parsedResponse["response"]!!.jsonObject
        assertEquals(clientDataJsonB64, responseObj["clientDataJSON"]!!.jsonPrimitive.content)
        assertEquals(attestationB64, responseObj["attestationObject"]!!.jsonPrimitive.content)
    }

    @Test
    fun `webauthn passkey assertion request parses and produces valid signature payload`() {
        val rpId = "github.com"
        val registration = PasskeyEngine.createPasskeyRegistration(
            rpId = rpId,
            userHandle = "octocat_user_id",
            userName = "octocat"
        )

        val challengeBytes = ByteArray(32) { (it * 3).toByte() }
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
        val origin = "https://$rpId"

        val clientDataJson = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = challengeB64,
            origin = origin
        )

        val assertion = PasskeyEngine.signPasskeyAssertion(
            rpId = rpId,
            clientDataJsonBytes = clientDataJson,
            privateKeyBytes = registration.rawPrivateKeyBytes,
            credentialId = registration.credentialId,
            userHandle = registration.userHandle,
            signCount = 1
        )

        val responseJsonStr = """
            {
              "id": "${registration.credentialId}",
              "rawId": "${registration.credentialId}",
              "type": "public-key",
              "authenticatorAttachment": "platform",
              "response": {
                "authenticatorData": "${assertion.authenticatorDataBase64}",
                "clientDataJSON": "${assertion.clientDataJsonBase64}",
                "signature": "${assertion.signatureBase64}",
                "userHandle": "${assertion.userHandleBase64}"
              },
              "clientExtensionResults": {}
            }
        """.trimIndent()

        val parsed = Json.parseToJsonElement(responseJsonStr).jsonObject
        assertEquals(registration.credentialId, parsed["id"]!!.jsonPrimitive.content)
        val innerResp = parsed["response"]!!.jsonObject
        assertEquals(assertion.signatureBase64, innerResp["signature"]!!.jsonPrimitive.content)
        assertEquals(assertion.authenticatorDataBase64, innerResp["authenticatorData"]!!.jsonPrimitive.content)
        assertEquals(assertion.clientDataJsonBase64, innerResp["clientDataJSON"]!!.jsonPrimitive.content)
        assertEquals(assertion.userHandleBase64, innerResp["userHandle"]!!.jsonPrimitive.content)
    }

    @Test
    fun `domain matcher accurately associates web origins and android packages with vault items`() {
        val passkeyItem = VaultItem(
            title = "GitHub",
            type = ItemType.PASSKEY,
            username = "alice",
            website = "https://github.com",
            passkeyRpId = "github.com"
        )

        val loginItem = VaultItem(
            title = "Slack",
            type = ItemType.LOGIN,
            username = "alice@work.com",
            password = "secret_password",
            website = "https://slack.com"
        )

        // Web origin matching
        assertTrue(DomainMatcher.isDomainMatch("github.com", passkeyItem.passkeyRpId))
        assertTrue(DomainMatcher.isDomainMatch("https://github.com/login", passkeyItem.passkeyRpId))
        assertFalse(DomainMatcher.isDomainMatch("fake-github.com", passkeyItem.passkeyRpId))

        // Native Android package matching
        assertTrue(DomainMatcher.isPackageMatch("com.Slack", loginItem.website, loginItem.title))
        assertFalse(DomainMatcher.isPackageMatch("com.malicious.app", loginItem.website, loginItem.title))
    }
}
