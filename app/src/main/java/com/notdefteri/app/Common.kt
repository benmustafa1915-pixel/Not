package com.notdefteri.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

val CAT_COLORS = listOf(
    0xFF534AB7.toInt(), 0xFF1D9E75.toInt(), 0xFFE24B4A.toInt(), 0xFFEF9F27.toInt(),
    0xFF378ADD.toInt(), 0xFFD4537E.toInt(), 0xFF639922.toInt(), 0xFF888780.toInt()
)

val TRUST_OPTIONS = listOf(0 to "Her seferinde", 1 to "1 dk", 5 to "5 dk", 15 to "15 dk", 30 to "30 dk", 60 to "1 saat")

/** Şifre sorma süresi seçici (uygulama kilidi). */
@Composable
fun TrustChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        for ((v, label) in TRUST_OPTIONS) {
            FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(label) })
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Şifre girişi. askConfirm = true ise şifre iki kez istenir ve en az 4 karakter aranır. */
@Composable
fun PasswordDialog(
    title: String,
    askConfirm: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onOk: (String) -> Unit
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = a, onValueChange = { a = it; err = null },
                    label = { Text("Şifre") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (askConfirm) {
                    OutlinedTextField(
                        value = b, onValueChange = { b = it; err = null },
                        label = { Text("Şifre (tekrar)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                val msg = err ?: error
                if (msg != null) {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (askConfirm && a.length < 4) {
                    err = "En az 4 karakter olmalı"
                } else if (askConfirm && a != b) {
                    err = "Şifreler aynı değil"
                } else if (a.isNotEmpty()) {
                    onOk(a)
                }
            }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Vazgeç") } }
    )
}

/** Kategori ekle / düzenle. */
@Composable
fun CategoryDialog(initial: Category, onCancel: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial.id == 0L) "Yeni kategori" else "Kategoriyi düzenle") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState())) {
                    for (c in CAT_COLORS) {
                        FilterChip(
                            selected = color == c, onClick = { color = c },
                            label = { Text("●", color = Color(c)) }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Vazgeç") } }
    )
}
