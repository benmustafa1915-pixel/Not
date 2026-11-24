package com.notdefteri.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr", "TR"))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(vm: NotesViewModel, onOpen: (Long, Long) -> Unit, onSettings: () -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val sel by vm.category.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val sortBy by vm.sortBy.collectAsStateWithLifecycle()
    val desc by vm.sortDesc.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var catDialog by remember { mutableStateOf<Category?>(null) }
    var delNote by remember { mutableStateOf<NoteItem?>(null) }
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val searchFocus = remember { FocusRequester() }

    BackHandler(enabled = searching) {
        searching = false
        vm.query.value = ""
    }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val tc = MaterialTheme.colorScheme.onSurface
                        BasicTextField(
                            value = q, onValueChange = { vm.query.value = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = tc),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (q.isEmpty()) {
                                        Text("Ara…", style = MaterialTheme.typography.titleMedium, color = tc.copy(alpha = 0.4f))
                                    }
                                    inner()
                                }
                            }
                        )
                    } else {
                        Text("Not Defterim")
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; vm.query.value = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Aramayı kapat")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Ara")
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Sırala")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                val opts = listOf("Güncelleme tarihi", "Oluşturma tarihi", "Başlık", "Boyut")
                                opts.forEachIndexed { i, label ->
                                    DropdownMenuItem(
                                        text = { Text(if (sortBy == i) "✓ $label" else label) },
                                        onClick = { vm.setSort(i, desc); sortMenu = false }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (desc) "Yeniden eskiye / Z→A" else "Eskiden yeniye / A→Z") },
                                    onClick = { vm.setSort(sortBy, !desc); sortMenu = false }
                                )
                            }
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0, if (sel > 0) sel else 0) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Yeni not")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Chip("Tümü", sel == -1L, MaterialTheme.colorScheme.primary, onClick = { vm.category.value = -1 })
                }
                items(cats, key = { it.id }) { c ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Chip(
                            (if (c.pinned) "📌 " else "") + c.name, sel == c.id, Color(c.color),
                            onClick = { vm.category.value = if (sel == c.id) -1L else c.id },
                            onLong = { menu = true }
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (c.pinned) "Sabitlemeyi kaldır" else "Başa sabitle") },
                                onClick = { menu = false; vm.pinCategory(c) }
                            )
                            DropdownMenuItem(
                                text = { Text("Düzenle") },
                                onClick = { menu = false; catDialog = c }
                            )
                            DropdownMenuItem(
                                text = { Text(if (c.emojiOn) "Kelime-emoji: açık" else "Kelime-emoji: kapalı") },
                                onClick = { menu = false; vm.toggleCategoryEmoji(c) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; vm.deleteCategory(c) }
                            )
                        }
                    }
                }
                item {
                    IconButton(onClick = { catDialog = Category(name = "", color = CAT_COLORS[0]) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Kategori ekle")
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (q.isNotEmpty()) "Sonuç yok" else "Henüz not yok.\nSağ alttaki kalem butonuna dokun.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { n ->
                        NoteRow(
                            n = n, cat = catMap[n.categoryId], cats = cats,
                            onClick = { onOpen(n.id, 0) },
                            onPin = { vm.pinNote(n) },
                            onFav = { vm.favNote(n) },
                            onMove = { vm.moveNote(n.id, it) },
                            onDelete = { delNote = n }
                        )
                    }
                }
            }
        }
    }

    catDialog?.let { c ->
        CategoryDialog(
            initial = c,
            onCancel = { catDialog = null },
            onSave = { name, color ->
                if (c.id == 0L) vm.addCategory(name, color) else vm.updateCategory(c.copy(name = name, color = color))
                catDialog = null
            }
        )
    }
    delNote?.let { n ->
        AlertDialog(
            onDismissRequest = { delNote = null },
            title = { Text("Not silinsin mi?") },
            text = { Text("\"${n.title}\" kalıcı olarak silinir.") },
            confirmButton = { TextButton(onClick = { vm.deleteNote(n.id); delNote = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { delNote = null }) { Text("Vazgeç") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    n: NoteItem,
    cat: Category?,
    cats: List<Category>,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onFav: () -> Unit,
    onMove: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val date = remember(n.updatedAt) { dateFmt.format(Date(n.updatedAt)) }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .combinedClickable(onClick = onClick, onLongClick = { pick = false; menu = true })
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cat != null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(cat.color)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    n.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (n.pinned) Text(" 📌")
                if (n.favorite) Icon(Icons.Filled.Star, null, tint = Color(0xFFEF9F27), modifier = Modifier.size(18.dp))
                if (n.locked) Text(" 🔒")
            }
            if (!n.locked && n.preview.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    n.preview, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!pick) {
                DropdownMenuItem(
                    text = { Text(if (n.pinned) "Sabitlemeyi kaldır" else "Başa sabitle") },
                    onClick = { menu = false; onPin() }
                )
                DropdownMenuItem(
                    text = { Text(if (n.favorite) "Favoriden çıkar" else "Favori yap") },
                    onClick = { menu = false; onFav() }
                )
                DropdownMenuItem(text = { Text("Kategori seç…") }, onClick = { pick = true })
                DropdownMenuItem(
                    text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(if (n.categoryId == 0L) "✓ Kategorisiz" else "Kategorisiz") },
                    onClick = { menu = false; onMove(0) }
                )
                for (c in cats) {
                    DropdownMenuItem(
                        text = { Text(if (n.categoryId == c.id) "✓ ${c.name}" else c.name) },
                        onClick = { menu = false; onMove(c.id) }
                    )
                }
            }
        }
    }
}
