package com.notdefteri.app

import java.util.Locale

/**
 * Cihaz içi, kural tabanlı Türkçe düzeltici. İnternet gerekmez.
 * Anlama bakan kuralları (de/da, ki) yakalayamaz; bu yüzden her öneri onaya sunulur.
 */
object Corrector {
    class Fix(
        val start: Int,
        val end: Int,
        val old: String,
        val new: String,
        val reason: String,
        /** true ise varsayılan olarak işaretli gelmez (kasıtlı olabilir). */
        val optional: Boolean = false
    )

    private val tr = Locale.forLanguageTag("tr-TR")

    private val common: Map<String, Pair<String, String>> = mapOf(
        "yanlız" to ("yalnız" to "Doğrusu “yalnız”"),
        "herkez" to ("herkes" to "Doğrusu “herkes”"),
        "yada" to ("ya da" to "“ya da” ayrı yazılır"),
        "herşey" to ("her şey" to "“her şey” ayrı yazılır"),
        "hersey" to ("her şey" to "“her şey” ayrı yazılır"),
        "birşey" to ("bir şey" to "“bir şey” ayrı yazılır"),
        "bişey" to ("bir şey" to "“bir şey” ayrı yazılır"),
        "hiçbirşey" to ("hiçbir şey" to "“hiçbir şey” ayrı yazılır"),
        "şuanda" to ("şu anda" to "“şu anda” ayrı yazılır"),
        "şuan" to ("şu an" to "“şu an” ayrı yazılır"),
        "hergün" to ("her gün" to "“her gün” ayrı yazılır"),
        "herzaman" to ("her zaman" to "“her zaman” ayrı yazılır"),
        "yinede" to ("yine de" to "“de/da” bağlacı ayrı yazılır"),
        "belkide" to ("belki de" to "“de/da” bağlacı ayrı yazılır"),
        "gerçektende" to ("gerçekten de" to "“de/da” bağlacı ayrı yazılır"),
        "pekçok" to ("pek çok" to "“pek çok” ayrı yazılır"),
        "orjinal" to ("orijinal" to "Doğrusu “orijinal”"),
        "malesef" to ("maalesef" to "Doğrusu “maalesef”")
    )

    private val reWord = Regex("\\p{L}+")
    private val reDoubleSpace = Regex("(?<=\\S) {2,}(?=\\S)")
    private val reSpaceBeforePunct = Regex("(?<=\\p{L}) +(?=[,;!?])")
    private val reNoSpaceAfter = Regex("(?<=\\p{L})[,;!?](?=\\p{L})")
    private val reSentenceStart = Regex("(?m)^\\p{Ll}|(?<=[.!?…]\\s{1,3})\\p{Ll}")
    private val reRepeat = Regex("(?<!\\p{L})(\\p{L}+)\\s+\\1(?!\\p{L})", RegexOption.IGNORE_CASE)

    fun find(text: String): List<Fix> {
        val out = ArrayList<Fix>()

        for (m in reWord.findAll(text)) {
            val hit = common[m.value.lowercase(tr)] ?: continue
            val w = m.value
            val repl = when {
                w.length > 1 && w.all { it.isUpperCase() } -> hit.first.uppercase(tr)
                w[0].isUpperCase() -> hit.first.replaceFirstChar { it.uppercase(tr) }
                else -> hit.first
            }
            out.add(Fix(m.range.first, m.range.last + 1, w, repl, hit.second))
        }

        for (m in reDoubleSpace.findAll(text)) {
            out.add(Fix(m.range.first, m.range.last + 1, m.value, " ", "Fazladan boşluk"))
        }
        for (m in reSpaceBeforePunct.findAll(text)) {
            out.add(Fix(m.range.first, m.range.last + 1, m.value, "", "Noktalama işaretinden önce boşluk olmaz"))
        }
        for (m in reNoSpaceAfter.findAll(text)) {
            out.add(Fix(m.range.first, m.range.last + 1, m.value, m.value + " ", "Noktalama işaretinden sonra boşluk gerekir"))
        }
        for (m in reSentenceStart.findAll(text)) {
            val i = m.range.first
            if (isAbbreviationBefore(text, i)) continue
            out.add(Fix(i, i + 1, m.value, m.value.uppercase(tr), "Cümle büyük harfle başlar"))
        }
        for (m in reRepeat.findAll(text)) {
            val first = m.groupValues[1]
            out.add(
                Fix(
                    m.range.first, m.range.last + 1, m.value, first,
                    "Tekrar eden kelime (kasıtlıysa işaretleme)", optional = true
                )
            )
        }

        // Çakışanları ele: soldaki kazanır
        out.sortBy { it.start }
        val res = ArrayList<Fix>()
        var lastEnd = -1
        for (f in out) {
            if (f.start < lastEnd) continue
            res.add(f)
            lastEnd = f.end
            if (res.size >= 300) break
        }
        return res
    }

    /** "vb. gibi", "Dr. ali" gibi kısaltmalardan sonra büyük harf önermemek için. */
    private fun isAbbreviationBefore(t: String, idx: Int): Boolean {
        var i = idx - 1
        while (i >= 0 && t[i].isWhitespace()) i--
        if (i < 0 || t[i] != '.') return false
        var j = i - 1
        var letters = 0
        while (j >= 0 && t[j].isLetter()) {
            letters++
            j--
        }
        return letters in 1..3
    }
}
