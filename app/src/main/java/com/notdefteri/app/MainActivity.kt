package com.notdefteri.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val vm: NotesViewModel by viewModels()

    val locked = mutableStateOf(false)
    private var leftAt = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked.value = vm.prefs.lockEnabled
        applyPrivacy()
        setContent { App(this, vm) }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val p = vm.prefs
        if (p.lockEnabled && !locked.value && leftAt >= 0) {
            val away = SystemClock.elapsedRealtime() - leftAt
            if (away >= p.trustMinutes * 60_000L) locked.value = true
        }
    }

    fun unlock() {
        locked.value = false
        leftAt = -1L
    }

    /** Son kullanılan uygulamalar ekranında içerik görünmesin. */
    fun applyPrivacy() {
        val hide = vm.prefs.hideRecents
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
