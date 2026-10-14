package com.anoop.financetracker.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** App-lock PIN hashing. Only a salted PBKDF2 hash is stored, never the PIN. */
object PinHasher {
    private const val ITER = 120_000
    private val b64 = Base64.getEncoder()
    private val d64 = Base64.getDecoder()

    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(pin: String, iterations: Int = ITER): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "pbkdf2$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(derive(pin, salt, iterations))
    }

    fun verify(pin: String, stored: String): Boolean {
        val p = stored.split('$')
        if (p.size != 4 || p[0] != "pbkdf2") return false
        val it = p[1].toIntOrNull() ?: return false
        val got = derive(pin, d64.decode(p[2]), it)
        return MessageDigest.isEqual(got, d64.decode(p[3]))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

/**
 * Provides the database passphrase. A random 32-byte passphrase is created once, encrypted with an
 * AES-GCM key that lives in the Android Keystore (it never leaves secure hardware), and the encrypted
 * blob is stored in the app's private no-backup folder.
 */
class DatabaseKey(private val context: Context) {
    private val alias = "ft_db_key"
    private val file get() = File(context.noBackupFilesDir, "db.key")

    fun passphrase(): ByteArray {
        val f = file
        if (f.exists()) {
            val bytes = f.readBytes()
            val iv = bytes.copyOfRange(0, 12)
            val ct = bytes.copyOfRange(12, bytes.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
            return c.doFinal(ct)
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val out = c.iv + c.doFinal(pass)
        f.parentFile?.mkdirs()
        f.writeBytes(out)
        return pass
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }
}
