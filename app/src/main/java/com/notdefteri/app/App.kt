package com.notdefteri.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

sealed interface Screen {
    data object Home : Screen
    data class Editor(val id: Long, val cat: Long) : Screen
    data object Settings : Screen
    data object ShortcutList : Screen
}

@Composable
fun NotTheme(mode: Int, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        3 -> lightColorScheme(
            primary = Color(0xFF7A5C2E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE6D3A8),
            onPrimaryContainer = Color(0xFF3B2F1E),
            background = Color(0xFFF4ECD8),
            onBackground = Color(0xFF3B2F1E),
            surface = Color(0xFFF4ECD8),
            onSurface = Color(0xFF3B2F1E),
            surfaceVariant = Color(0xFFE8DDC3),
            onSurfaceVariant = Color(0xFF6B5A3E),
            outline = Color(0xFF9C8B6A)
        )
        4 -> darkColorScheme(
            primary = Color(0xFF00E5FF),
            onPrimary = Color(0xFF00262E),
            primaryContainer = Color(0xFF0E3A4A),
            onPrimaryContainer = Color(0xFFC9F6FF),
            background = Color(0xFF0B0F1A),
            onBackground = Color(0xFFE6F7FF),
            surface = Color(0xFF0B0F1A),
            onSurface = Color(0xFFE6F7FF),
            surfaceVariant = Color(0xFF141B2B),
            onSurfaceVariant = Color(0xFF9FB8CC),
            outline = Color(0xFF2B3B57)
        )
        2 -> darkScheme()
        1 -> lightColorScheme(primary = Color(0xFF534AB7))
        else -> if (isSystemInDarkTheme()) darkScheme() else lightColorScheme(primary = Color(0xFF534AB7))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun darkScheme() = darkColorScheme(
    primary = Color(0xFFAFA9EC),
    onPrimary = Color(0xFF26215C),
    primaryContainer = Color(0xFF3C3489),
    onPrimaryContainer = Color(0xFFEEEDFE),
    background = Color(0xFF141218),
    surface = Color(0xFF141218)
)

@Composable
fun App(act: MainActivity, vm: NotesViewModel) {
    var theme by remember { mutableIntStateOf(vm.prefs.theme) }
    var screen by remember { mutableStateOf<Screen>(if (vm.prefs.openToNew) Screen.Editor(0, 0) else Screen.Home) }
    val locked by act.locked

    NotTheme(theme) {
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val view = LocalView.current
        SideEffect {
            val c = WindowCompat.getInsetsController(act.window, view)
            c.isAppearanceLightStatusBars = light
            c.isAppearanceLightNavigationBars = light
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (val s = screen) {
                    Screen.Home -> {
                        NoteListScreen(
                            vm,
                            onOpen = { id, cat -> screen = Screen.Editor(id, cat) },
                            onSettings = { screen = Screen.Settings }
                        )
                    }
                    is Screen.Editor -> key(s) {
                        EditorScreen(vm, s.id, s.cat, onBack = { screen = Screen.Home })
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            vm, act, theme,
                            onTheme = { theme = it; vm.prefs.theme = it },
                            onShortcuts = { screen = Screen.ShortcutList },
                            onBack = { screen = Screen.Home }
                        )
                    }
                    Screen.ShortcutList -> {
                        ShortcutsScreen(vm, onBack = { screen = Screen.Settings })
                    }
                }
                // Kilitliyken içerik altta kalır ama tamamen kapatılır
                if (locked) {
                    BackHandler { act.moveTaskToBack(true) }
                    LockScreen(vm, act) { act.unlock() }
                }
            }
        }
    }
}
