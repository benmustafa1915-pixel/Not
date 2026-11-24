package com.notdefteri.app

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Şifre türetme (PBKDF2) ve AES-GCM şifreleme. Sadece kilitli notlar şifrelenir. */
object Crypto {
    private const val ITER = 120_000
    private val rnd = SecureRandom()

    class NoteKey(val salt: ByteArray, val key: ByteArray)

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }
    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITER, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Uygulama kilidi için şifre özeti. */
    fun hash(secret: String, saltB64: String): String = b64(derive(secret, unb64(saltB64)))

    /** Not şifresinden anahtar üretir. Yavaş olduğu için oturum başına bir kez çağrılır. */
    fun newKey(password: String): NoteKey {
        val salt = randomBytes(16)
        return NoteKey(salt, derive(password, salt))
    }

    fun encrypt(plain: String, k: NoteKey): String {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(k.salt + iv + ct)
    }

    /** Şifre yanlışsa null döner. */
    fun decrypt(data: String, password: String): Pair<String, NoteKey>? = try {
        val all = unb64(data)
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val ct = all.copyOfRange(28, all.size)
        val key = derive(password, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8) to NoteKey(salt, key)
    } catch (e: Exception) {
        null
    }
}
