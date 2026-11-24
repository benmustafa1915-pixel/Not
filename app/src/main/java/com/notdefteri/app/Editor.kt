package com.notdefteri.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Düzenleyici ekranının durumu ve kaydetme mantığı. */
class EditorModel(val vm: NotesViewModel, val editor: RichEdit, noteId: Long, newCat: Long) {
    val holder = Holder(noteId)
    var title by mutableStateOf("")
    var catId by mutableLongStateOf(newCat)
    var pinned by mutableStateOf(false)
    var fav by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var pending by mutableStateOf<Note?>(null)
    var active by mutableStateOf(setOf<String>())
    var key: Crypto.NoteKey? = null
    var loaded = noteId == 0L
    var dirty = false
    var deleted = false

    private val handler = Handler(Looper.getMainLooper())
    private val saver = Runnable { save() }

    init {
        editor.onActive = { active = it }
        editor.onEdited = { touch() }
    }

    fun touch() {
        dirty = true
        handler.removeCallbacks(saver)
        handler.postDelayed(saver, 1200)
    }

    fun save() {
        handler.removeCallbacks(saver)
        if (!dirty || !loaded || deleted) return
        if (locked && key == null) return
        dirty = false
        vm.save(
            holder,
            Draft(
                title, editor.text.toString(), Rich.serialize(editor.text),
                catId, pinned, fav, locked, key
            )
        )
    }

    suspend fun load() {
        if (holder.id == 0L) return
        val n = vm.load(holder.id)
        if (n == null) {
            loaded = true
            return
        }
        title = n.title
        catId = n.categoryId
        pinned = n.pinned
        fav = n.favorite
        holder.created = n.createdAt
        if (n.locked) {
            locked = true
            pending = n
        } else {
            editor.load(n.body, n.spans)
            loaded = true
        }
    }

    suspend fun unlock(pw: String): Boolean {
        val n = pending ?: return false
        val r = vm.decrypt(n.cipher, pw) ?: return false
        val o = JSONObject(r.first)
        key = r.second
        editor.load(o.optString("b"), o.optString("s"))
        loaded = true
        pending = null
        return true
    }
}

private fun focusEditor(ctx: Context, ed: RichEdit) {
    ed.post {
        ed.requestFocus()
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(ed, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: NotesViewModel, noteId: Long, newCat: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cats by vm.categories.collectAsStateWithLifecycle()
    val m = remember { EditorModel(vm, RichEdit(ctx), noteId, newCat) }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var showSetPw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pwError by remember { mutableStateOf<String?>(null) }
    var fixes by remember { mutableStateOf<List<Corrector.Fix>?>(null) }
    var fixBase by remember { mutableIntStateOf(0) }
    val smap by vm.shortcutMap.collectAsStateWithLifecycle()
    SideEffect {
        m.editor.shortcuts = smap
        m.editor.shortcutsOn = vm.prefs.shortcutsOn
        m.editor.emojiOn = vm.prefs.emojiOn && (cats.firstOrNull { it.id == m.catId }?.emojiOn ?: true)
    }

    LaunchedEffect(m) {
        m.load()
        if (noteId == 0L) focusEditor(ctx, m.editor)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { m.save() }
    DisposableEffect(m) { onDispose { m.save() } }
    BackHandler {
        m.save()
        onBack()
    }

    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).toArgb()

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { m.save(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = { TitleField(m) },
                actions = {
                    IconButton(onClick = { m.pinned = !m.pinned; m.dirty = true; m.save() }) {
                        Text("📌", modifier = Modifier.alpha(if (m.pinned) 1f else 0.3f))
                    }
                    IconButton(onClick = { m.fav = !m.fav; m.dirty = true; m.save() }) {
                        Icon(
                            Icons.Filled.Star, contentDescription = "Favori",
                            tint = if (m.fav) Color(0xFFEF9F27) else MaterialTheme.colorScheme.outline
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val words = remember(menu) { if (menu) Rich.words(m.editor.text) else 0 }
                            val chars = remember(menu) { if (menu) m.editor.text.length else 0 }
                            DropdownMenuItem(
                                text = { Text("$words kelime · $chars karakter") },
                                enabled = false, onClick = {}
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (m.catId == 0L) "✓ Kategorisiz" else "Kategorisiz") },
                                onClick = { m.catId = 0; m.dirty = true; m.save(); menu = false }
                            )
                            for (c in cats) {
                                DropdownMenuItem(
                                    text = { Text(if (m.catId == c.id) "✓ ${c.name}" else c.name) },
                                    onClick = { m.catId = c.id; m.dirty = true; m.save(); menu = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Yazımı düzelt…") },
                                onClick = {
                                    menu = false
                                    val ed = m.editor
                                    val a = minOf(ed.selectionStart, ed.selectionEnd).coerceAtLeast(0)
                                    val b = maxOf(ed.selectionStart, ed.selectionEnd).coerceAtLeast(0)
                                    val base = if (b > a) a else 0
                                    val seg = if (b > a) ed.text.substring(a, b) else ed.text.toString()
                                    val found = Corrector.find(seg)
                                    if (found.isEmpty()) {
                                        Toast.makeText(ctx, "Düzeltilecek bir şey bulunamadı", Toast.LENGTH_SHORT).show()
                                    } else {
                                        fixBase = base
                                        fixes = found
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (m.locked) "Şifreyi değiştir" else "Şifre koy") },
                                onClick = { menu = false; showSetPw = true }
                            )
                            if (m.locked) {
                                DropdownMenuItem(
                                    text = { Text("Şifreyi kaldır") },
                                    onClick = {
                                        menu = false
                                        m.locked = false
                                        m.key = null
                                        m.dirty = true
                                        m.save()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { FormatBar(m) }
    ) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = { m.editor },
            update = {
                it.setTextColor(textColor)
                it.setHintTextColor(hintColor)
            }
        )
    }

    // Kilitli not açılırken şifre sor
    if (m.pending != null) {
        PasswordDialog(
            title = "Not şifresi", askConfirm = false, error = pwError,
            onCancel = { onBack() },
            onOk = { pw ->
                scope.launch {
                    pwError = if (m.unlock(pw)) null else "Şifre yanlış"
                }
            }
        )
    }
    if (showSetPw) {
        PasswordDialog(
            title = "Not için şifre", askConfirm = true, error = null,
            onCancel = { showSetPw = false },
            onOk = { pw ->
                scope.launch {
                    m.key = vm.newKey(pw)
                    m.locked = true
                    m.dirty = true
                    showSetPw = false
                    m.save()
                }
            }
        )
    }
    fixes?.let { list ->
        CorrectDialog(
            fixes = list,
            onCancel = { fixes = null },
            onApply = { chosen ->
                m.editor.applyFixes(fixBase, chosen)
                fixes = null
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Not silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    m.deleted = true
                    if (m.holder.id != 0L) vm.deleteNote(m.holder.id)
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun TitleField(m: EditorModel) {
    val tc = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = m.title,
        onValueChange = { m.title = it; m.touch() },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = tc),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.Sentences
        ),
        keyboardActions = KeyboardActions(onNext = { m.editor.requestFocus() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (m.title.isEmpty()) {
                    Text(
                        "Başlık", style = MaterialTheme.typography.titleMedium,
                        color = tc.copy(alpha = 0.4f)
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun FormatBar(m: EditorModel) {
    val ed = m.editor
    var colorMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var fontMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tool("↶", false) { ed.undo(); ed.requestFocus() }
            Tool("↷", false) { ed.redo(); ed.requestFocus() }
            Tool("B", "b" in m.active, weight = FontWeight.Bold) { ed.toggleFlag("b"); ed.requestFocus() }
            Tool("I", "i" in m.active, style = FontStyle.Italic) { ed.toggleFlag("i"); ed.requestFocus() }
            Tool("U", "u" in m.active, deco = TextDecoration.Underline) { ed.toggleFlag("u"); ed.requestFocus() }
            Tool("S", "s" in m.active, deco = TextDecoration.LineThrough) { ed.toggleFlag("s"); ed.requestFocus() }

            Box {
                Tool("A", "c" in m.active) { colorMenu = true }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Varsayılan renk") },
                        onClick = { colorMenu = false; ed.setValue("c", null); ed.requestFocus() }
                    )
                    for ((name, c) in Rich.COLORS) {
                        DropdownMenuItem(
                            text = { Text("● $name", color = Color(c)) },
                            onClick = { colorMenu = false; ed.setValue("c", c.toString()); ed.requestFocus() }
                        )
                    }
                }
            }
            Box {
                Tool("Aa", "z" in m.active) { sizeMenu = true }
                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Varsayılan boyut") },
                        onClick = { sizeMenu = false; ed.setValue("z", null); ed.requestFocus() }
                    )
                    for (s in Rich.SIZES) {
                        DropdownMenuItem(
                            text = { Text("$s") },
                            onClick = { sizeMenu = false; ed.setValue("z", s.toString()); ed.requestFocus() }
                        )
                    }
                }
            }
            Box {
                Tool("Yazı", "f" in m.active) { fontMenu = true }
                DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Varsayılan yazı tipi") },
                        onClick = { fontMenu = false; ed.setValue("f", null); ed.requestFocus() }
                    )
                    for ((family, name) in Rich.FONTS) {
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { fontMenu = false; ed.setValue("f", family); ed.requestFocus() }
                        )
                    }
                }
            }
            Tool("•", false) { ed.toggleList(0); ed.requestFocus() }
            Tool("1.", false) { ed.toggleList(1); ed.requestFocus() }
            Tool("☑", false) { ed.toggleList(2); ed.requestFocus() }
        }
    }
}

@Composable
private fun Tool(
    label: String,
    on: Boolean,
    weight: FontWeight? = null,
    style: FontStyle? = null,
    deco: TextDecoration? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = weight, fontStyle = style, textDecoration = deco)
    }
}

/** Önerilen düzeltmeleri tek tek işaretleyip uygulatır. */
@Composable
private fun CorrectDialog(
    fixes: List<Corrector.Fix>,
    onCancel: () -> Unit,
    onApply: (List<Corrector.Fix>) -> Unit
) {
    val checked = remember { mutableStateListOf<Boolean>().also { l -> fixes.forEach { l.add(!it.optional) } } }
    val count = checked.count { it }
    fun vis(s: String) = s.replace(" ", "·").replace("\n", "↵")
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Önerilen düzeltmeler (${fixes.size})") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                itemsIndexed(fixes) { i, f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                        Column(Modifier.weight(1f)) {
                            Text("«${vis(f.old)}» → «${vis(f.new)}»")
                            Text(
                                f.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = count > 0,
                onClick = { onApply(fixes.filterIndexed { i, _ -> checked[i] }) }
            ) { Text("Uygula ($count)") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Vazgeç") } }
    )
}
