package com.notdefteri.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bir notun kayıt numarası. İlk kayıtta numara atanır, sonraki kayıtlar aynı notu günceller. */
class Holder(@Volatile var id: Long, @Volatile var created: Long = 0)

class Draft(
    val title: String,
    val text: String,
    val spans: String,
    val categoryId: Long,
    val pinned: Boolean,
    val favorite: Boolean,
    val locked: Boolean,
    val key: Crypto.NoteKey?
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val db = AppDb.get(app)
    private val noteDao = db.notes()
    private val catDao = db.categories()
    private val scDao = db.shortcuts()
    private val tr = Locale.forLanguageTag("tr-TR")
    private val saveLock = Mutex()

    val query = MutableStateFlow("")
    /** -1 = tümü, 0 = kategorisiz, diğerleri kategori numarası */
    val category = MutableStateFlow(-1L)
    val sortBy = MutableStateFlow(prefs.sortBy)
    val sortDesc = MutableStateFlow(prefs.sortDesc)

    val categories: StateFlow<List<Category>> =
        catDao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shortcuts: StateFlow<List<Shortcut>> =
        scDao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Yazarken hızlı arama için: küçük harfli kısayol → kısayol. */
    val shortcutMap: StateFlow<Map<String, Shortcut>> =
        shortcuts.map { l -> l.associateBy { Shortcuts.key(it.trig) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Hazır kısayollar ve emoji sözlüğü ilk açılışta bir kez eklenir
        if (!prefs.seeded) {
            viewModelScope.launch(Dispatchers.IO) {
                scDao.insertAll(Shortcuts.seeds())
                prefs.seeded = true
            }
        }
    }

    val notes: StateFlow<List<NoteItem>> =
        combine(category, query.map { it.trim() }) { c, q -> c to q }
            .flatMapLatest { (c, q) -> noteDao.items(c, q) }
            .combine(sortBy) { l, s -> l to s }
            .combine(sortDesc) { (l, s), d -> sort(l, s, d) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun sort(l: List<NoteItem>, by: Int, desc: Boolean): List<NoteItem> {
        val cmp: Comparator<NoteItem> = when (by) {
            1 -> compareBy<NoteItem> { it.createdAt }
            2 -> compareBy<NoteItem> { it.title.lowercase(tr) }
            3 -> compareBy<NoteItem> { it.size }
            else -> compareBy<NoteItem> { it.updatedAt }
        }
        val c = if (desc) cmp.reversed() else cmp
        // Sabitliler en başta, sonra favoriler, sonra seçilen sıralama
        return l.sortedWith(
            compareByDescending<NoteItem> { it.pinned }
                .thenByDescending { it.favorite }
                .then(c)
        )
    }

    fun setSort(by: Int, desc: Boolean) {
        prefs.sortBy = by
        prefs.sortDesc = desc
        sortBy.value = by
        sortDesc.value = desc
    }

    // ---------- kategoriler ----------

    fun addCategory(name: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) { catDao.upsert(Category(name = name, color = color)) }
    }

    fun updateCategory(c: Category) {
        viewModelScope.launch(Dispatchers.IO) { catDao.upsert(c) }
    }

    fun pinCategory(c: Category) {
        viewModelScope.launch(Dispatchers.IO) { catDao.setPinned(c.id, !c.pinned) }
    }

    fun deleteCategory(c: Category) {
        if (category.value == c.id) category.value = -1
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.clearCategory(c.id)
            catDao.delete(c)
        }
    }

    // ---------- notlar ----------

    fun pinNote(n: NoteItem) {
        viewModelScope.launch(Dispatchers.IO) { noteDao.setPinned(n.id, !n.pinned) }
    }

    fun favNote(n: NoteItem) {
        viewModelScope.launch(Dispatchers.IO) { noteDao.setFavorite(n.id, !n.favorite) }
    }

    fun moveNote(id: Long, cat: Long) {
        viewModelScope.launch(Dispatchers.IO) { noteDao.setCategory(id, cat) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { noteDao.delete(id) }
    }

    suspend fun load(id: Long): Note? = withContext(Dispatchers.IO) { noteDao.get(id) }

    suspend fun decrypt(cipher: String, password: String): Pair<String, Crypto.NoteKey>? =
        withContext(Dispatchers.Default) { Crypto.decrypt(cipher, password) }

    suspend fun newKey(password: String): Crypto.NoteKey =
        withContext(Dispatchers.Default) { Crypto.newKey(password) }

    /** Kaydı arka planda sırayla yapar; ekranı bekletmez. */
    fun save(h: Holder, d: Draft) {
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { doSave(h, d) } }
    }

    private val listRe = Regex("^(• |☐ |☑ |\\d{1,3}\\. )")

    private fun firstLine(t: String): String {
        for (l in t.lineSequence()) {
            val s = l.trim().replace(listRe, "").trim()
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    private suspend fun doSave(h: Holder, d: Draft) {
        if (d.locked && d.key == null) return
        if (h.id == 0L && d.title.isBlank() && d.text.isBlank()) return
        val now = System.currentTimeMillis()
        if (h.created == 0L) h.created = now
        val title = d.title.ifBlank {
            if (d.locked) "Kilitli not" else firstLine(d.text).take(40).ifEmpty { "Adsız not" }
        }
        val note = if (d.locked && d.key != null) {
            val payload = JSONObject().put("b", d.text).put("s", d.spans).toString()
            Note(
                id = h.id, title = title, cipher = Crypto.encrypt(payload, d.key), locked = true,
                categoryId = d.categoryId, pinned = d.pinned, favorite = d.favorite,
                createdAt = h.created, updatedAt = now, size = d.text.length
            )
        } else {
            Note(
                id = h.id, title = title, body = d.text, spans = d.spans,
                categoryId = d.categoryId, pinned = d.pinned, favorite = d.favorite,
                createdAt = h.created, updatedAt = now, size = d.text.length,
                preview = d.text.take(300).replace('\n', ' ').trim().take(140)
            )
        }
        val id = noteDao.upsert(note)
        if (h.id == 0L) h.id = id
    }

    // ---------- kısayollar ----------

    fun saveShortcut(s: Shortcut) {
        viewModelScope.launch(Dispatchers.IO) { scDao.upsert(s) }
    }

    fun deleteShortcut(s: Shortcut) {
        viewModelScope.launch(Dispatchers.IO) { scDao.delete(s) }
    }

    fun toggleCategoryEmoji(c: Category) {
        viewModelScope.launch(Dispatchers.IO) { catDao.setEmoji(c.id, !c.emojiOn) }
    }

    // ---------- yedek ----------

    /** Tüm notları, kategorileri ve kısayolları tek JSON olarak verir. Kilitli notlar şifreli kalır. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val cats = JSONArray()
        for (c in catDao.list()) {
            cats.put(
                JSONObject().put("id", c.id).put("name", c.name).put("color", c.color)
                    .put("pinned", c.pinned).put("emojiOn", c.emojiOn)
            )
        }
        val notes = JSONArray()
        for (n in noteDao.all()) {
            notes.put(
                JSONObject().put("title", n.title).put("body", n.body).put("spans", n.spans)
                    .put("cipher", n.cipher).put("locked", n.locked).put("categoryId", n.categoryId)
                    .put("pinned", n.pinned).put("favorite", n.favorite)
                    .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)
                    .put("size", n.size).put("preview", n.preview)
            )
        }
        val scs = JSONArray()
        for (s in scDao.list()) {
            scs.put(
                JSONObject().put("trig", s.trig).put("expansion", s.expansion)
                    .put("append", s.appendMode).put("builtin", s.builtin)
            )
        }
        JSONObject().put("app", "NotDefterim").put("v", 1)
            .put("categories", cats).put("notes", notes).put("shortcuts", scs).toString()
    }

    /** Yedeği geri yükler. Mevcut notlara dokunmaz, yedektekileri yeni not olarak ekler. */
    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        val byName = catDao.list().associateBy { it.name.lowercase(tr) }
        val idMap = HashMap<Long, Long>()
        val cats = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until cats.length()) {
            val o = cats.getJSONObject(i)
            val name = o.getString("name")
            val found = byName[name.lowercase(tr)]
            val id = found?.id ?: catDao.upsert(
                Category(
                    name = name,
                    color = o.optInt("color", CAT_COLORS[0]),
                    pinned = o.optBoolean("pinned", false),
                    emojiOn = o.optBoolean("emojiOn", true)
                )
            )
            idMap[o.optLong("id")] = id
        }
        var count = 0
        val notes = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            noteDao.upsert(
                Note(
                    title = o.optString("title"),
                    body = o.optString("body"),
                    spans = o.optString("spans"),
                    cipher = o.optString("cipher"),
                    locked = o.optBoolean("locked", false),
                    categoryId = idMap[o.optLong("categoryId")] ?: 0L,
                    pinned = o.optBoolean("pinned", false),
                    favorite = o.optBoolean("favorite", false),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    size = o.optInt("size"),
                    preview = o.optString("preview")
                )
            )
            count++
        }
        val scs = root.optJSONArray("shortcuts") ?: JSONArray()
        val list = ArrayList<Shortcut>()
        for (i in 0 until scs.length()) {
            val o = scs.getJSONObject(i)
            val t = o.optString("trig")
            if (t.isEmpty()) continue
            list.add(
                Shortcut(
                    trig = t, expansion = o.optString("expansion"),
                    appendMode = o.optBoolean("append", false), builtin = o.optBoolean("builtin", false)
                )
            )
        }
        scDao.insertAll(list)
        count
    }

    // ---------- uygulama kilidi ----------

    suspend fun setLock(type: String, secret: String) =
        withContext(Dispatchers.Default) { prefs.setSecret(type, secret) }

    suspend fun checkLock(secret: String): Boolean =
        withContext(Dispatchers.Default) { prefs.checkSecret(secret) }
}
