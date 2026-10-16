package dev.inkov.inkvault.android

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

private const val CLEAR_MS = 45_000L
private val handler = Handler(Looper.getMainLooper())
private var clearTask: Runnable? = null

/** Копирование секрета: буфер помечается чувствительным (Android 13 не показывает его содержимое) и очищается. */
fun copySecret(context: Context, label: String, value: String): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = PersistableBundle().apply {
        val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
        putBoolean(key, true)
    }
    clipboard.setPrimaryClip(clip)
    clearTask?.let(handler::removeCallbacks)
    clearTask = Runnable { if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() }.also { handler.postDelayed(it, CLEAR_MS) }
    return context.getString(R.string.copied, CLEAR_MS / 1000)
}
