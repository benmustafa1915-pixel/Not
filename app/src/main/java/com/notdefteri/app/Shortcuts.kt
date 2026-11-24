package com.notdefteri.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kısayol yardımcıları ve hazır başlangıç listesi. Hepsi cihazda, internet gerekmez. */
object Shortcuts {
    private val tr = Locale.forLanguageTag("tr-TR")

    fun key(s: String): String = s.trim().lowercase(tr)

    /** {tarih} ve {saat} o anki değerle değişir. */
    fun expandTokens(s: String): String {
        if (!s.contains('{')) return s
        val now = Date()
        return s
            .replace("{tarih}", SimpleDateFormat("d MMMM yyyy", tr).format(now))
            .replace("{saat}", SimpleDateFormat("HH:mm", tr).format(now))
    }

    private const val FOODS =
        "domates=🍅;elma=🍎;armut=🍐;muz=🍌;portakal=🍊;limon=🍋;çilek=🍓;üzüm=🍇;karpuz=🍉;" +
            "patates=🥔;havuç=🥕;soğan=🧅;sarımsak=🧄;biber=🌶️;mısır=🌽;ekmek=🍞;peynir=🧀;" +
            "yumurta=🥚;süt=🥛;kahve=☕;çay=🍵;pizza=🍕;hamburger=🍔;makarna=🍝;pilav=🍚;" +
            "çorba=🍲;tatlı=🍰;dondurma=🍦;çikolata=🍫;hediye=🎁"

    fun seeds(): List<Shortcut> {
        val list = ArrayList<Shortcut>()
        list.add(Shortcut(trig = "scs", expansion = "Seni çok seviyorum 🫶"))
        list.add(Shortcut(trig = "/bugun", expansion = "{tarih}"))
        list.add(Shortcut(trig = "/saat", expansion = "{saat}"))
        for (pair in FOODS.split(";")) {
            val p = pair.split("=")
            if (p.size == 2) list.add(Shortcut(trig = p[0], expansion = p[1], appendMode = true, builtin = true))
        }
        return list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(vm: NotesViewModel, onBack: () -> Unit) {
    val list by vm.shortcuts.collectAsStateWithLifecycle()
    var on by remember { mutableStateOf(vm.prefs.shortcutsOn) }
    var emoji by remember { mutableStateOf(vm.prefs.emojiOn) }
    var edit by remember { mutableStateOf<Shortcut?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kısayollar ve emoji") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = Shortcut(trig = "", expansion = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Kısayol ekle")
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
        ) {
            item { SwitchLine("Kısayolları kullan", on) { on = it; vm.prefs.shortcutsOn = it } }
            item { SwitchLine("Hazır kelime-emoji sözlüğü", emoji) { emoji = it; vm.prefs.emojiOn = it } }
            item {
                Text(
                    "Kısayolu yazıp boşluk bırakınca metin çıkar. Geri tuşuna hemen basarsan iptal olur. " +
                        "Roman gibi notlarda emoji istemiyorsan, ana ekranda o kategoriye uzun basıp kelime-emojiyi kapat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(list, key = { it.id }) { s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { edit = s }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        s.trig + "  →  " + s.expansion.replace("\n", " ⏎ "),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2
                    )
                    Text(
                        (if (s.appendMode) "yanına ekler" else "yerine koyar") +
                            (if (s.builtin) " · hazır emoji" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    edit?.let { s ->
        ShortcutDialog(
            init = s,
            existing = list,
            onCancel = { edit = null },
            onSave = { vm.saveShortcut(it); edit = null },
            onDelete = { vm.deleteShortcut(it); edit = null }
        )
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ShortcutDialog(
    init: Shortcut,
    existing: List<Shortcut>,
    onCancel: () -> Unit,
    onSave: (Shortcut) -> Unit,
    onDelete: (Shortcut) -> Unit
) {
    var trig by remember { mutableStateOf(init.trig) }
    var exp by remember { mutableStateOf(init.expansion) }
    var app by remember { mutableStateOf(init.appendMode) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (init.id == 0L) "Yeni kısayol" else "Kısayolu düzenle") },
        text = {
            Column {
                OutlinedTextField(
                    value = trig,
                    onValueChange = { trig = it.filter { c -> !c.isWhitespace() }; err = null },
                    label = { Text("Yazdığın (ör. scs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exp,
                    onValueChange = { exp = it; err = null },
                    label = { Text("Çıkacak metin") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Kelimenin yanına ekle (silme)", modifier = Modifier.weight(1f))
                    Switch(checked = app, onCheckedChange = { app = it })
                }
                Text(
                    "{tarih} ve {saat} yazarsan o anki tarih ve saat gelir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                val msg = err
                if (msg != null) {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = Shortcuts.key(trig)
                if (k.length < 2) {
                    err = "En az 2 karakter olmalı"
                } else if (exp.isEmpty()) {
                    err = "Çıkacak metin boş olamaz"
                } else if (existing.any { it.id != init.id && Shortcuts.key(it.trig) == k }) {
                    err = "Bu kısayol zaten var"
                } else {
                    onSave(init.copy(trig = k, expansion = exp, appendMode = app))
                }
            }) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (init.id != 0L) {
                    TextButton(onClick = { onDelete(init) }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onCancel) { Text("Vazgeç") }
            }
        }
    )
}
