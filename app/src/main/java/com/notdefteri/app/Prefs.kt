package com.notdefteri.app

import android.content.Context
import java.security.MessageDigest

/** Ayarlar. SharedPreferences eşzamanlı okunur, bu yüzden açılışı yavaşlatmaz. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun str(k: String) = sp.getString(k, "") ?: ""

    var lockEnabled: Boolean
        get() = bool("lock", false)
        set(v) { sp.edit().putBoolean("lock", v).apply() }

    /** "pin" veya "password" */
    var lockType: String
        get() = str("lockType").ifEmpty { "pin" }
        set(v) { sp.edit().putString("lockType", v).apply() }

    var lockHash: String
        get() = str("lockHash")
        set(v) { sp.edit().putString("lockHash", v).apply() }

    var lockSalt: String
        get() = str("lockSalt")
        set(v) { sp.edit().putString("lockSalt", v).apply() }

    var pinLength: Int
        get() = int("pinLen", 4)
        set(v) { sp.edit().putInt("pinLen", v).apply() }

    var biometric: Boolean
        get() = bool("bio", false)
        set(v) { sp.edit().putBoolean("bio", v).apply() }

    /** PIN doğru girilince "Giriş"e basmadan otomatik açılır. */
    var autoUnlock: Boolean
        get() = bool("auto", true)
        set(v) { sp.edit().putBoolean("auto", v).apply() }

    /** Uygulamadan çıkıp bu süre içinde dönersen şifre sormaz. 0 = her seferinde sor. */
    var trustMinutes: Int
        get() = int("trust", 0)
        set(v) { sp.edit().putInt("trust", v).apply() }

    var hideRecents: Boolean
        get() = bool("hideRecents", true)
        set(v) { sp.edit().putBoolean("hideRecents", v).apply() }

    /** 0 güncelleme, 1 oluşturma, 2 başlık, 3 boyut */
    var sortBy: Int
        get() = int("sortBy", 0)
        set(v) { sp.edit().putInt("sortBy", v).apply() }

    var sortDesc: Boolean
        get() = bool("sortDesc", true)
        set(v) { sp.edit().putBoolean("sortDesc", v).apply() }

    /** 0 sistem, 1 açık, 2 koyu, 3 sepya, 4 fütüristik */
    var theme: Int
        get() = int("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    /** Kısayollar (scs gibi) çalışsın mı. */
    var shortcutsOn: Boolean
        get() = bool("shortcutsOn", true)
        set(v) { sp.edit().putBoolean("shortcutsOn", v).apply() }

    /** Hazır kelime-emoji sözlüğü çalışsın mı. */
    var emojiOn: Boolean
        get() = bool("emojiOn", true)
        set(v) { sp.edit().putBoolean("emojiOn", v).apply() }

    /** Hazır kısayollar bir kez eklendi mi (silinenler geri gelmesin). */
    var seeded: Boolean
        get() = bool("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }

    /** Uygulama açılınca direkt boş not açılsın. */
    var openToNew: Boolean
        get() = bool("openToNew", false)
        set(v) { sp.edit().putBoolean("openToNew", v).apply() }

    fun setSecret(type: String, secret: String) {
        val salt = Crypto.b64(Crypto.randomBytes(16))
        lockSalt = salt
        lockHash = Crypto.hash(secret, salt)
        lockType = type
        pinLength = if (type == "pin") secret.length else 0
        lockEnabled = true
    }

    fun checkSecret(secret: String): Boolean {
        val salt = lockSalt
        val h = lockHash
        if (salt.isEmpty() || h.isEmpty()) return false
        return MessageDigest.isEqual(Crypto.hash(secret, salt).toByteArray(), h.toByteArray())
    }

    fun clearLock() {
        lockEnabled = false
        lockHash = ""
        lockSalt = ""
        biometric = false
    }
}
