package com.notdefteri.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/**
 * Hızlı ve hafif zengin metin düzenleyici: kalın/italik/altı çizili/üstü çizili, renk, boyut,
 * yazı tipi, madde/numara/tikli listeler, geri al / ileri al.
 */
class RichEdit(ctx: Context) : EditText(ctx) {

    class Snap(val text: String, val spans: String, val sel: Int)

    private var ready = false
    private var busy = false

    /** Kısayollar ve emoji sözlüğü (ViewModel'den gelir). */
    var shortcuts: Map<String, Shortcut> = emptyMap()
    var shortcutsOn = true
    var emojiOn = true

    private class Auto(val start: Int, val word: String, val exp: String, val append: Boolean, val cursorAfter: Int)

    private var lastAuto: Auto? = null

    /** Yeni yazılacak metne uygulanacak açık biçimler. */
    val typing = HashMap<String, String>()
    var onActive: ((Set<String>) -> Unit)? = null
    var onEdited: (() -> Unit)? = null

    private val undoStack = ArrayList<Snap>()
    private val redoStack = ArrayList<Snap>()
    private var last: Snap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val snapTask = Runnable { pushSnap() }

    private val tap = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onTap(e)
            return false
        }
    })

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setLineSpacing(0f, 1.25f)
        val p = (16 * resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, p)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        hint = "Yazmaya başla…"
        isVerticalScrollBarEnabled = true

        addTextChangedListener(object : TextWatcher {
            var s0 = 0
            var c0 = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s0 = start
                c0 = count
            }

            override fun afterTextChanged(e: Editable) {
                if (busy) return
                busy = true
                try {
                    handleChange(e, s0, c0)
                } finally {
                    busy = false
                }
                scheduleSnap()
                onEdited?.invoke()
            }
        })
        ready = true
    }

    // ---------- içerik yükleme ----------

    fun load(text: String, spans: String) {
        busy = true
        setText(Rich.deserialize(text, spans))
        setSelection(length())
        busy = false
        undoStack.clear()
        redoStack.clear()
        last = snapshot()
        refreshTyping()
    }

    // ---------- kısayollar ve emoji ----------

    private fun isDelim(c: Char): Boolean =
        c == ' ' || c == '\n' || c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')'

    /** Ayraçtan önceki kelime bir kısayolsa metni değiştirir. Ayracın kayma miktarını döndürür. */
    private fun tryExpand(e: Editable, pos: Int): Int {
        if (!shortcutsOn || shortcuts.isEmpty()) return 0
        var ws = pos
        while (ws > 0 && (e[ws - 1].isLetterOrDigit() || e[ws - 1] == '/' || e[ws - 1] == '_')) ws--
        if (ws == pos) return 0
        val word = e.subSequence(ws, pos).toString()
        val sc = shortcuts[Shortcuts.key(word)] ?: return 0
        if (sc.builtin && !emojiOn) return 0
        val exp = Shortcuts.expandTokens(sc.expansion)
        val delta: Int
        if (sc.appendMode) {
            e.insert(pos, " $exp")
            delta = exp.length + 1
        } else {
            e.replace(ws, pos, exp)
            delta = exp.length - word.length
        }
        lastAuto = Auto(ws, word, exp, sc.appendMode, pos + delta + 1)
        return delta
    }

    /** Kısayol az önce çalıştıysa geri tuşu onu iptal eder, kelime geri gelir. */
    private fun revertAuto(): Boolean {
        val a = lastAuto ?: return false
        if (selectionStart != selectionEnd || selectionStart != a.cursorAfter) return false
        val e = text
        val delim = a.cursorAfter - 1
        busy = true
        val ok = try {
            if (a.append) {
                e.delete(a.start + a.word.length, delim)
            } else {
                e.replace(a.start, a.start + a.exp.length, a.word)
            }
            true
        } catch (ex: Exception) {
            false
        } finally {
            busy = false
        }
        lastAuto = null
        if (!ok) return false
        setSelection((a.start + a.word.length + 1).coerceIn(0, length()))
        edited()
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(ic, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && revertAuto()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL && revertAuto()) {
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    // ---------- düzeltme önerilerini uygula ----------

    fun applyFixes(base: Int, fixes: List<Corrector.Fix>) {
        val e = text
        busy = true
        for (f in fixes.sortedByDescending { it.start }) {
            val s = base + f.start
            val en = base + f.end
            if (s < 0 || en > e.length || s > en) continue
            e.replace(s, en, f.new)
        }
        busy = false
        edited()
    }

    // ---------- yazarken ----------

    private fun handleChange(e: Editable, st: Int, count: Int) {
        lastAuto = null
        if (count <= 0 || count > 2000) return
        if (st >= e.length) return
        var d = 0
        val lastIdx = minOf(st + count, e.length) - 1
        if (count <= 200 && lastIdx >= st && isDelim(e[lastIdx])) {
            d = tryExpand(e, lastIdx)
        }
        if (count == 1) {
            val p = st + d
            if (p < e.length && e[p] == '\n' && continueList(e, p)) return
            Rich.applyTyping(e, typing, p, minOf(p + 1, e.length))
        } else {
            Rich.applyTyping(e, typing, st, minOf(st + count + d, e.length))
        }
    }

    private fun continueList(e: Editable, nl: Int): Boolean {
        val ls = Rich.lineStart(e, nl)
        val line = e.subSequence(ls, minOf(nl, ls + 8)).toString()
        val pl = Rich.prefixLen(line)
        if (pl == 0) return false
        if (nl - ls == pl) {
            // boş madde: listeyi bitir
            e.delete(ls, nl + 1)
            return true
        }
        val next = when {
            line.startsWith(Rich.BULLET) -> Rich.BULLET
            line.startsWith(Rich.BOX) || line.startsWith(Rich.BOX_DONE) -> Rich.BOX
            else -> "${(line.substring(0, pl - 2).toIntOrNull() ?: 0) + 1}. "
        }
        e.insert(nl + 1, next)
        val pos = nl + 1 + next.length
        post { setSelection(minOf(pos, length())) }
        return true
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready || busy) return
        refreshTyping()
    }

    private fun refreshTyping() {
        typing.clear()
        val a = selectionStart
        val b = selectionEnd
        if (a >= 0 && a == b) typing.putAll(Rich.activeAt(text, a, b))
        notifyActive()
    }

    private fun notifyActive() {
        val a = selectionStart
        val b = selectionEnd
        val set: Set<String> = when {
            a < 0 -> emptySet()
            a == b -> typing.keys.toSet()
            else -> Rich.activeAt(text, a, b).keys.toSet()
        }
        onActive?.invoke(set)
    }

    // ---------- araç çubuğu işlemleri ----------

    fun toggleFlag(t: String) {
        val a = selectionStart
        val b = selectionEnd
        if (a < 0) return
        if (a != b) {
            val on = Rich.activeAt(text, a, b).containsKey(t)
            busy = true
            if (on) Rich.clear(text, t, a, b) else Rich.apply(text, t, "", a, b)
            busy = false
            edited()
        } else if (typing.containsKey(t)) {
            typing.remove(t)
        } else {
            typing[t] = ""
        }
        notifyActive()
    }

    /** Renk (c), boyut (z), yazı tipi (f). v == null varsayılana döner. */
    fun setValue(t: String, v: String?) {
        val a = selectionStart
        val b = selectionEnd
        if (a < 0) return
        if (a != b) {
            busy = true
            if (v == null) Rich.clear(text, t, a, b) else Rich.apply(text, t, v, a, b)
            busy = false
            edited()
        } else if (v == null) {
            typing.remove(t)
        } else {
            typing[t] = v
        }
        notifyActive()
    }

    /** kind: 0 madde işareti, 1 numaralı, 2 tikli liste */
    fun toggleList(kind: Int) {
        val e = text
        val a = maxOf(selectionStart, 0)
        var b = maxOf(selectionEnd, a)
        if (b > a && b > 0 && e[b - 1] == '\n') b--
        val starts = ArrayList<Int>()
        var p = Rich.lineStart(e, a)
        while (true) {
            starts.add(p)
            val nl = Rich.lineEnd(e, p)
            if (nl >= e.length || nl >= b) break
            p = nl + 1
        }
        fun has(st: Int): Boolean {
            val line = e.subSequence(st, minOf(Rich.lineEnd(e, st), st + 8)).toString()
            return when (kind) {
                0 -> line.startsWith(Rich.BULLET)
                2 -> line.startsWith(Rich.BOX) || line.startsWith(Rich.BOX_DONE)
                else -> Rich.prefixLen(line) > 2 || (Rich.prefixLen(line) == 2 && line[0].isDigit())
            }
        }
        val all = starts.all { has(it) }
        busy = true
        for (i in starts.indices.reversed()) {
            val st = starts[i]
            val line = e.subSequence(st, minOf(Rich.lineEnd(e, st), st + 8)).toString()
            val pl = Rich.prefixLen(line)
            if (pl > 0) {
                if (line.startsWith(Rich.BOX_DONE)) {
                    Rich.clear(e, "s", st + pl, Rich.lineEnd(e, st))
                }
                e.delete(st, st + pl)
            }
            if (!all) {
                val prefix = when (kind) {
                    0 -> Rich.BULLET
                    2 -> Rich.BOX
                    else -> "${i + 1}. "
                }
                e.insert(st, prefix)
            }
        }
        busy = false
        edited()
        refreshTyping()
    }

    private fun edited() {
        scheduleSnap()
        onEdited?.invoke()
    }

    // ---------- tikli listeye dokununca işaretle ----------

    private fun onTap(ev: MotionEvent) {
        val e = text
        if (e.isEmpty()) return
        val off = getOffsetForPosition(ev.x, ev.y)
        val ls = Rich.lineStart(e, off)
        if (off > ls + 1 || ls >= e.length) return
        val c = e[ls]
        if (c != '☐' && c != '☑') return
        toggleBox(ls)
    }

    private fun toggleBox(ls: Int) {
        val e = text
        if (ls + 2 > e.length) return
        val becomingDone = e[ls] == '☐'
        val le = Rich.lineEnd(e, ls)
        busy = true
        e.replace(ls, ls + 1, if (becomingDone) "☑" else "☐")
        if (becomingDone) Rich.apply(e, "s", "", ls + 2, le) else Rich.clear(e, "s", ls + 2, le)
        busy = false
        post { setSelection(minOf(ls + 2, length())) }
        edited()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tap.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    // ---------- geri al / ileri al ----------

    private fun snapshot() = Snap(text.toString(), Rich.serialize(text), maxOf(selectionStart, 0))

    private fun scheduleSnap() {
        handler.removeCallbacks(snapTask)
        handler.postDelayed(snapTask, 600)
    }

    private fun pushSnap() {
        val cur = snapshot()
        val l = last
        if (l != null && l.text == cur.text && l.spans == cur.spans) return
        if (l != null) {
            undoStack.add(l)
            val max = if (cur.text.length > 200_000) 10 else 40
            while (undoStack.size > max) undoStack.removeAt(0)
        }
        last = cur
        redoStack.clear()
    }

    private fun flushSnap() {
        handler.removeCallbacks(snapTask)
        pushSnap()
    }

    fun undo() {
        flushSnap()
        if (undoStack.isEmpty()) return
        val cur = last ?: snapshot()
        val prev = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(cur)
        restore(prev)
    }

    fun redo() {
        flushSnap()
        if (redoStack.isEmpty()) return
        val cur = last ?: snapshot()
        val next = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(cur)
        restore(next)
    }

    private fun restore(s: Snap) {
        busy = true
        setText(Rich.deserialize(s.text, s.spans))
        setSelection(s.sel.coerceIn(0, length()))
        busy = false
        last = s
        refreshTyping()
        onEdited?.invoke()
    }
}
