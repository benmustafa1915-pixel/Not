package com.notdefteri.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

fun biometricAvailable(a: FragmentActivity): Boolean =
    BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(a: FragmentActivity, onOk: () -> Unit) {
    if (!biometricAvailable(a)) return
    val prompt = BiometricPrompt(
        a, ContextCompat.getMainExecutor(a),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Kilidi aç")
        .setNegativeButtonText("Şifre kullan")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(vm: NotesViewModel, act: FragmentActivity, onUnlocked: () -> Unit) {
    val p = vm.prefs
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var trust by remember { mutableIntStateOf(p.trustMinutes) }
    var working by remember { mutableStateOf(false) }
    val isPin = p.lockType == "pin"
    val focus = remember { FocusRequester() }

    fun done() {
        p.trustMinutes = trust
        onUnlocked()
    }

    fun attempt(v: String) {
        if (working || v.isEmpty()) return
        working = true
        scope.launch {
            val ok = vm.checkLock(v)
            working = false
            if (ok) {
                done()
            } else {
                wrong = true
                input = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // alan henüz hazır değilse sorun değil, kullanıcı dokununca odaklanır
        }
        if (p.biometric) promptBiometric(act) { done() }
    }

    // Altındaki içeriği tamamen kapatır ve dokunuşları yutar
    Surface(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text("Kilitli", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { raw ->
                    wrong = false
                    val v = if (isPin) raw.filter { it.isDigit() }.take(8) else raw
                    input = v
                    if (isPin && p.autoUnlock && p.pinLength > 0 && v.length >= p.pinLength) attempt(v)
                },
                label = { Text(if (isPin) "PIN" else "Parola") },
                singleLine = true,
                isError = wrong,
                supportingText = { if (wrong) Text("Şifre yanlış") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { attempt(input) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { attempt(input) }) { Text("Giriş") }
                if (p.biometric && biometricAvailable(act)) {
                    OutlinedButton(onClick = { promptBiometric(act) { done() } }) { Text("Parmak izi") }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Şifre sorma süresi", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            TrustChips(trust) { trust = it }
        }
    }
}
