package com.buct.course.login

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * App 内置 WebAuthn/FIDO2 软件认证器。
 * 学校未部署 assetlinks.json 时，系统通行密钥提供方会拒绝该 RP；
 * 此实现按 WebAuthn 标准在 App 内生成 EC P-256 密钥并构造 attestation/assertion。
 */
object BuctSoftwarePasskey {
    const val RP_ID = "sidportal.buct.edu.cn"
    const val ORIGIN = "https://sidportal.buct.edu.cn"
    private const val PREFS = "buct_software_passkey"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_PREFIX = "buct_passkey_"

    class NotRegisteredException : IllegalStateException("该学号在当前设备上未创建通行密钥")

    data class AssertionSession internal constructor(
        internal val username: String,
        internal val credentialId: ByteArray,
        internal val authenticatorData: ByteArray,
        internal val clientDataJson: ByteArray,
        internal val signCount: Int,
        internal val signature: Signature
    )

    fun hasCredential(context: Context, username: String): Boolean =
        prefs(context).contains(credentialKey(username))

    fun createCredential(context: Context, username: String, creationJson: String): String {
        val options = JSONObject(creationJson)
        val rpId = options.optJSONObject("rp")?.optString("id")?.takeIf { it.isNotBlank() } ?: RP_ID
        val challenge = options.getString("challenge")
        val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val alias = KEY_PREFIX + UUID.randomUUID().toString()
        val publicKey = generateKeyPair(alias).public as ECPublicKey
        val coseKey = encodeCoseEc2Key(publicKey)
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val authData = ByteArrayOutputStream().apply {
            write(rpIdHash)
            write(0x45) // UP + UV + AT
            write(ByteArray(4))
            write(ByteArray(16))
            write(byteArrayOf((credentialId.size ushr 8).toByte(), credentialId.size.toByte()))
            write(credentialId)
            write(coseKey)
        }.toByteArray()
        val attestationObject = CborWriter().apply {
            writeMapHeader(3)
            writeText("fmt"); writeText("none")
            writeText("attStmt"); writeMapHeader(0)
            writeText("authData"); writeBytes(authData)
        }.toByteArray()
        val clientDataJson =
            "{\"type\":\"webauthn.create\",\"challenge\":\"$challenge\",\"origin\":\"$ORIGIN\",\"crossOrigin\":false}"
                .toByteArray(Charsets.UTF_8)
        val response = JSONObject()
            .put("type", "public-key")
            .put("id", b64Url(credentialId))
            .put("response", JSONObject()
                .put("attestationObject", b64Url(attestationObject))
                .put("clientDataJSON", b64Url(clientDataJson)))
            .put("clientExtensionResults", JSONObject())
        prefs(context).edit()
            .putString(credentialKey(username), b64Url(credentialId))
            .putString(aliasKey(username), alias)
            .putInt(signCountKey(username), 0)
            .putString(rpKey(username), rpId)
            .apply()
        return response.toString()
    }

    fun beginAuthentication(context: Context, username: String, requestJson: String): AssertionSession {
        val prefs = prefs(context)
        val credIdText = prefs.getString(credentialKey(username), null)
            ?: throw NotRegisteredException()
        val alias = prefs.getString(aliasKey(username), null)
            ?: throw NotRegisteredException()
        val credentialId = try {
            Base64.decode(credIdText, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            throw NotRegisteredException()
        }
        val options = JSONObject(requestJson)
        val rpId = options.optString("rpId").ifBlank { RP_ID }
        val challenge = options.getString("challenge")
        val allow = options.optJSONArray("allowCredentials")
        if (allow != null) {
            var matched = false
            for (i in 0 until allow.length()) {
                val id = allow.getJSONObject(i).optString("id")
                if (id == credIdText) { matched = true; break }
            }
            if (!matched) throw NotRegisteredException()
        }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
            ?: throw NotRegisteredException()
        val clientDataJson =
            "{\"type\":\"webauthn.get\",\"challenge\":\"$challenge\",\"origin\":\"$ORIGIN\",\"crossOrigin\":false}"
                .toByteArray(Charsets.UTF_8)
        val signCount = prefs.getInt(signCountKey(username), 0) + 1
        val authenticatorData = ByteArrayOutputStream().apply {
            write(sha256(rpId.toByteArray(Charsets.UTF_8)))
            write(0x05) // UP + UV
            write(byteArrayOf(
                ((signCount ushr 24) and 0xff).toByte(),
                ((signCount ushr 16) and 0xff).toByte(),
                ((signCount ushr 8) and 0xff).toByte(),
                (signCount and 0xff).toByte()
            ))
        }.toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(authenticatorData)
        signature.update(sha256(clientDataJson))
        return AssertionSession(username, credentialId, authenticatorData, clientDataJson, signCount, signature)
    }

    fun completeAssertion(context: Context, session: AssertionSession): String {
        val signatureBytes = session.signature.sign()
        prefs(context).edit().putInt(signCountKey(session.username), session.signCount).apply()
        return JSONObject()
            .put("type", "public-key")
            .put("id", b64Url(session.credentialId))
            .put("response", JSONObject()
                .put("authenticatorData", b64Url(session.authenticatorData))
                .put("clientDataJSON", b64Url(session.clientDataJson))
                .put("signature", b64Url(signatureBytes))
                .put("userHandle", JSONObject.NULL))
            .put("clientExtensionResults", JSONObject())
            .toString()
    }

    fun clearCredential(context: Context, username: String) {
        val prefs = prefs(context)
        val alias = prefs.getString(aliasKey(username), null)
        if (!alias.isNullOrBlank()) {
            try { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias) } catch (_: Exception) {}
        }
        prefs.edit()
            .remove(credentialKey(username))
            .remove(aliasKey(username))
            .remove(signCountKey(username))
            .remove(rpKey(username))
            .apply()
    }

    private fun generateKeyPair(alias: String): java.security.KeyPair {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        generator.initialize(builder.build())
        return generator.generateKeyPair()
    }

    private fun encodeCoseEc2Key(publicKey: ECPublicKey): ByteArray {
        fun fixed32(value: java.math.BigInteger): ByteArray {
            val raw = value.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
                else -> ByteArray(32 - raw.size) + raw
            }
        }
        val x = fixed32(publicKey.w.affineX)
        val y = fixed32(publicKey.w.affineY)
        return CborWriter().apply {
            writeMapHeader(5)
            writeInt(1); writeInt(2)
            writeInt(3); writeInt(-7)
            writeInt(-1); writeInt(1)
            writeInt(-2); writeBytes(x)
            writeInt(-3); writeBytes(y)
        }.toByteArray()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun credentialKey(username: String) = "credential_id_$username"
    private fun aliasKey(username: String) = "key_alias_$username"
    private fun signCountKey(username: String) = "sign_count_$username"
    private fun rpKey(username: String) = "rp_id_$username"
    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    private fun b64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private class CborWriter {
        private val out = ByteArrayOutputStream()
        fun writeMapHeader(size: Int) = writeHead(5, size.toLong())
        fun writeBytes(value: ByteArray) {
            writeHead(2, value.size.toLong()); out.write(value)
        }
        fun writeText(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeHead(3, bytes.size.toLong()); out.write(bytes)
        }
        fun writeInt(value: Int) {
            if (value >= 0) writeHead(0, value.toLong()) else writeHead(1, (-1L - value))
        }
        fun toByteArray(): ByteArray = out.toByteArray()
        private fun writeHead(major: Int, value: Long) {
            when {
                value < 24 -> out.write((major shl 5) or value.toInt())
                value < 256 -> { out.write((major shl 5) or 24); out.write(value.toInt()) }
                value < 65536 -> {
                    out.write((major shl 5) or 25)
                    out.write((value ushr 8).toInt() and 0xff)
                    out.write(value.toInt() and 0xff)
                }
                else -> {
                    out.write((major shl 5) or 26)
                    out.write((value ushr 24).toInt() and 0xff)
                    out.write((value ushr 16).toInt() and 0xff)
                    out.write((value ushr 8).toInt() and 0xff)
                    out.write(value.toInt() and 0xff)
                }
            }
        }
    }
}



