package com.focuslock.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2-HMAC-SHA256. Stored format: v1:<iterations>:<salt b64>:<hash b64>. */
object PhraseHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun normalize(phrase: String): String = phrase.trim()

    fun hash(phrase: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = derive(normalize(phrase), salt, iterations)
        val enc = Base64.getEncoder()
        return "v1:$iterations:${enc.encodeToString(salt)}:${enc.encodeToString(key)}"
    }

    fun verify(phrase: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != "v1") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        return try {
            val dec = Base64.getDecoder()
            val salt = dec.decode(parts[2])
            val expected = dec.decode(parts[3])
            MessageDigest.isEqual(expected, derive(normalize(phrase), salt, iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(phrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(phrase.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
