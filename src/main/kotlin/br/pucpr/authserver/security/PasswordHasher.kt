package br.pucpr.authserver.security

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Aisly deviation from the professor's baseline: passwords are never stored in
 * plain text. This hashes with PBKDF2 (SHA-256, 120k iterations, a per-password
 * random salt) and stores the result as `base64(salt):base64(hash)`.
 */
@Component
class PasswordHasher {
    private val secureRandom = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(password, salt)
        return "${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(hash)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split(":")
        if (parts.size != 2) return false
        val salt = Base64.getDecoder().decode(parts[0])
        val expected = Base64.getDecoder().decode(parts[1])
        val actual = pbkdf2(password, salt)
        return expected.contentEquals(actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
