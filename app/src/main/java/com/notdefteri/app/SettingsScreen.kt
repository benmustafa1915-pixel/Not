package com.notdefteri.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: NotesViewModel,
    act: MainActivity,
    theme: Int,
    onTheme: (Int) -> Unit,
    onShortcuts: () -> Unit,
    onBack: () -> Unit
) {
    val p = vm.prefs
    val scope = rememberCoroutineScope()
    var lockOn by remember { mutableStateOf(p.lockEnabled) }
    var bio by remember { mutableStateOf(p.biometric) }
    var auto by remember { mutableStateOf(p.autoUnlock) }
    var hide by remember { mutableStateOf(p.hideRecents) }
    var trust by remember { mutableIntStateOf(p.trustMinutes) }
    var isPin by remember { mutableStateOf(p.lockType == "pin") }
    var dlg by remember { mutableIntStateOf(0) } // 1: şifre belirle, 2: kilidi kapat
    var dlgErr by remember { mutableStateOf<String?>(null) }
    val bioOk = remember { biometricAvailable(act) }
    var openNew by remember { mutableStateOf(p.openToNew) }
    var msg by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val json = vm.exportJson()
                    withContext(Dispatchers.IO) {
                        act.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                }.isSuccess
                msg = if (ok) "Yedek kaydedildi" else "Yedek alınamadı"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val r = runCatching {
                    val json = withContext(Dispatchers.IO) {
                        act.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    }
                    vm.importJson(json)
                }
                msg = r.fold({ n -> "$n not geri yüklendi" }, { "Yedek okunamadı" })
            }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Header("Güvenlik")
            SwitchRow("Uygulama kilidi", lockOn) { on -> dlg = if (on) 1 else 2 }
            if (lockOn) {
                OutlinedButton(onClick = { dlg = 1 }) { Text("Şifreyi veya türünü değiştir") }
                Spacer(Modifier.height(8.dp))
                if (bioOk) {
                    SwitchRow("Parmak izi / yüz ile aç", bio) { bio = it; p.biometric = it }
                }
                if (isPin) {
                    SwitchRow("PIN doğruysa otomatik aç", auto) { auto = it; p.autoUnlock = it }
                }
                Text("Şifre sorma süresi", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Uygulamadan çıkıp bu süre içinde dönersen şifre sorulmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                TrustChips(trust) { trust = it; p.trustMinutes = it }
                Spacer(Modifier.height(8.dp))
            }
            SwitchRow("Son uygulamalarda içeriği gizle", hide) {
                hide = it
                p.hideRecents = it
                act.applyPrivacy()
            }
            Text(
                "Not: Android 13'ten eski sürümlerde bu seçenek ekran görüntüsü almayı da engeller.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Header("Görünüm")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("Sistem", "Açık", "Koyu", "Sepya", "Fütüristik").forEachIndexed { i, label ->
                    FilterChip(selected = theme == i, onClick = { onTheme(i) }, label = { Text(label) })
                    Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Header("Yazma")
            SwitchRow("Açılışta direkt yeni not", openNew) { openNew = it; p.openToNew = it }
            OutlinedButton(onClick = onShortcuts) { Text("Kısayollar ve emoji") }
            Text(
                "scs gibi kısayollar ve domates → 🍅 gibi emojiler burada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Header("Yedek")
            Text(
                "Notların, kategorilerin ve kısayolların tek dosyaya kaydedilir. Kilitli notlar yedekte de " +
                    "şifreli kalır. Geri yükleme mevcut notlarını silmez, yedektekileri ekler.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedButton(onClick = { exportLauncher.launch("not-defterim-yedek.json") }) { Text("Yedek al") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Text("Geri yükle")
                }
            }
            val note = msg
            if (note != null) {
                Text(note, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(20.dp))
            Header("Hakkında")
            Text(
                "Not Defterim 1.1\nKilitli notlar cihazda AES-256 ile şifrelenir. Şifreni unutursan " +
                    "o notlar kurtarılamaz.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    when (dlg) {
        1 -> SetLockDialog(
            onCancel = { dlg = 0 },
            onOk = { type, secret ->
                scope.launch {
                    vm.setLock(type, secret)
                    lockOn = true
                    isPin = type == "pin"
                    dlg = 0
                }
            }
        )
        2 -> PasswordDialog(
            title = "Kilidi kapat", askConfirm = false, error = dlgErr,
            onCancel = { dlg = 0; dlgErr = null },
            onOk = { pw ->
                scope.launch {
                    if (vm.checkLock(pw)) {
                        p.clearLock()
                        lockOn = false
                        bio = false
                        dlg = 0
                        dlgErr = null
                    } else {
                        dlgErr = "Şifre yanlış"
                    }
                }
            }
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SetLockDialog(onCancel: () -> Unit, onOk: (String, String) -> Unit) {
    var type by remember { mutableStateOf("pin") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val pin = type == "pin"
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Uygulama kilidi") },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = pin,
                        onClick = { type = "pin"; a = ""; b = ""; err = null },
                        label = { Text("PIN") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = !pin,
                        onClick = { type = "password"; a = ""; b = ""; err = null },
                        label = { Text("Parola") }
                    )
                }
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = if (pin) it.filter { c -> c.isDigit() }.take(8) else it; err = null },
                    label = { Text(if (pin) "PIN (4-8 rakam)" else "Parola (en az 4 karakter)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = b,
                    onValueChange = { b = if (pin) it.filter { c -> c.isDigit() }.take(8) else it; err = null },
                    label = { Text("Tekrar") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                val msg = err
                if (msg != null) {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (a.length < 4) {
                    err = "En az 4 karakter olmalı"
                } else if (a != b) {
                    err = "İkisi aynı değil"
                } else {
                    onOk(type, a)
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Vazgeç") } }
    )
}
