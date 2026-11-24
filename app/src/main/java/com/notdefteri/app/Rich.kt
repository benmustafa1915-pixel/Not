package com.notdefteri.app

import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray

/**
 * Zengin metin yardımcıları. Biçimler hafif bir JSON olarak saklanır:
 * [["b",başlangıç,bitiş,""], ...]  (b kalın, i italik, u altı çizgi, s üstü çizgi,
 * c renk, z boyut, f yazı tipi)
 */
object Rich {
    const val BULLET = "• "
    const val BOX = "☐ "
    const val BOX_DONE = "☑ "
    val FLAG = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    val FONTS = listOf(
        "sans-serif" to "Normal",
        "serif" to "Serif",
        "monospace" to "Daktilo",
        "cursive" to "El yazısı",
        "casual" to "Günlük",
        "sans-serif-condensed" to "Dar"
    )
    val SIZES = listOf(14, 16, 18, 20, 24, 28, 34)
    val COLORS = listOf(
        "Kırmızı" to 0xFFE53935.toInt(),
        "Turuncu" to 0xFFFB8C00.toInt(),
        "Sarı" to 0xFFFDD835.toInt(),
        "Yeşil" to 0xFF43A047.toInt(),
        "Mavi" to 0xFF1E88E5.toInt(),
        "Mor" to 0xFF8E24AA.toInt(),
        "Kahverengi" to 0xFF6D4C41.toInt(),
        "Gri" to 0xFF9E9E9E.toInt()
    )

    fun typeOf(o: Any): String? = when (o) {
        is StyleSpan -> when (o.style) {
            Typeface.BOLD -> "b"
            Typeface.ITALIC -> "i"
            else -> null
        }
        is UnderlineSpan -> "u"
        is StrikethroughSpan -> "s"
        is ForegroundColorSpan -> "c"
        is AbsoluteSizeSpan -> "z"
        is TypefaceSpan -> "f"
        else -> null
    }

    fun valueOf(o: Any): String = when (o) {
        is ForegroundColorSpan -> o.foregroundColor.toString()
        is AbsoluteSizeSpan -> o.size.toString()
        is TypefaceSpan -> o.family ?: ""
        else -> ""
    }

    fun make(t: String, v: String): Any? = when (t) {
        "b" -> StyleSpan(Typeface.BOLD)
        "i" -> StyleSpan(Typeface.ITALIC)
        "u" -> UnderlineSpan()
        "s" -> StrikethroughSpan()
        "c" -> v.toIntOrNull()?.let { ForegroundColorSpan(it) }
        "z" -> v.toIntOrNull()?.let { AbsoluteSizeSpan(it, true) }
        "f" -> TypefaceSpan(v)
        else -> null
    }

    fun serialize(sp: Spanned): String {
        val arr = JSONArray()
        for (o in sp.getSpans(0, sp.length, Any::class.java)) {
            val t = typeOf(o) ?: continue
            val s = sp.getSpanStart(o)
            val e = sp.getSpanEnd(o)
            if (s < 0 || e <= s) continue
            arr.put(JSONArray().put(t).put(s).put(e).put(valueOf(o)))
        }
        return arr.toString()
    }

    fun deserialize(text: String, spansJson: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        if (spansJson.isNotEmpty()) {
            try {
                val arr = JSONArray(spansJson)
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONArray(i)
                    val s = a.getInt(1)
                    val e = a.getInt(2)
                    if (s < 0 || e > sb.length || s >= e) continue
                    val o = make(a.getString(0), a.optString(3)) ?: continue
                    sb.setSpan(o, s, e, FLAG)
                }
            } catch (e: Exception) {
                // bozuk biçim verisi: metin yine de açılır
            }
        }
        return sb
    }

    /** Bir span'ın [s, en) aralığına denk gelen kısmını keser, kalan uçları korur. */
    private fun cut(e: Editable, o: Any, t: String, s: Int, en: Int) {
        val a = e.getSpanStart(o)
        val b = e.getSpanEnd(o)
        val v = valueOf(o)
        e.removeSpan(o)
        if (a < s) make(t, v)?.let { e.setSpan(it, a, s, FLAG) }
        if (b > en) make(t, v)?.let { e.setSpan(it, en, b, FLAG) }
    }

    fun clear(e: Editable, t: String, s: Int, en: Int) {
        if (s >= en) return
        for (o in e.getSpans(s, en, Any::class.java)) {
            if (typeOf(o) == t) cut(e, o, t, s, en)
        }
    }

    fun apply(e: Editable, t: String, v: String, s: Int, en: Int) {
        if (s >= en) return
        var covered = false
        for (o in e.getSpans(s, en, Any::class.java)) {
            if (typeOf(o) != t) continue
            if (valueOf(o) != v) {
                cut(e, o, t, s, en)
            } else if (e.getSpanStart(o) <= s && e.getSpanEnd(o) >= en) {
                covered = true
            }
        }
        if (covered) return
        // Yanındaki aynı biçimi uzat: harf harf yazarken yüzlerce küçük span oluşmasın
        if (s > 0) {
            for (o in e.getSpans(s - 1, s, Any::class.java)) {
                if (typeOf(o) == t && valueOf(o) == v && e.getSpanEnd(o) == s) {
                    e.setSpan(o, e.getSpanStart(o), en, FLAG)
                    return
                }
            }
        }
        make(t, v)?.let { e.setSpan(it, s, en, FLAG) }
    }

    /** Yeni yazılan [s, en) aralığına, o an açık olan yazım biçimlerini uygular. */
    fun applyTyping(e: Editable, typing: Map<String, String>, s: Int, en: Int) {
        if (s >= en) return
        for (o in e.getSpans(s, en, Any::class.java)) {
            val t = typeOf(o) ?: continue
            if (!typing.containsKey(t)) cut(e, o, t, s, en)
        }
        for ((t, v) in typing) apply(e, t, v, s, en)
    }

    /** İmleçte (ya da seçimin tamamında) geçerli olan biçimler. */
    fun activeAt(e: Spanned, a: Int, b: Int): Map<String, String> {
        val m = HashMap<String, String>()
        val from = if (a == b) maxOf(0, a - 1) else a
        val to = b
        if (to <= from) return m
        for (o in e.getSpans(from, to, Any::class.java)) {
            val t = typeOf(o) ?: continue
            if (e.getSpanStart(o) <= from && e.getSpanEnd(o) >= to) m[t] = valueOf(o)
        }
        return m
    }

    private val numRe = Regex("^\\d{1,3}\\. ")

    /** Satır başındaki liste işaretinin uzunluğu (yoksa 0). */
    fun prefixLen(line: CharSequence): Int {
        if (line.startsWith(BULLET) || line.startsWith(BOX) || line.startsWith(BOX_DONE)) return 2
        return numRe.find(line)?.value?.length ?: 0
    }

    fun lineStart(e: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, e.length)
        while (i > 0 && e[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(e: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, e.length)
        while (i < e.length && e[i] != '\n') i++
        return i
    }

    fun words(s: CharSequence): Int {
        var n = 0
        var inWord = false
        for (ch in s) {
            if (ch.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                n++
            }
        }
        return n
    }
}
