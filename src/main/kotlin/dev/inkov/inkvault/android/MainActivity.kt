package dev.inkov.inkvault.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.inkov.inkvault.android.ui.InkVaultApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 включает режим edge-to-edge сам, на старых версиях он включается здесь:
        // отступы от системных панелей и цвет их значков одинаковы на всех версиях
        enableEdgeToEdge()
        // секреты на экране: без снимков экрана и без превью в списке недавних приложений
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val controller = controller ?: VaultController(applicationContext).also { controller = it }
        setContent { InkVaultApp(controller, this) }
    }

    override fun onStart() {
        super.onStart()
        controller?.checkAutoLock()
    }

    // касания внутри диалогов Compose сюда не доходят: у диалога свое окно
    override fun onUserInteraction() {
        super.onUserInteraction()
        controller?.userActive()
    }

    companion object {
        // контроллер живет дольше активности: поворот экрана не закрывает соединение и не блокирует хранилище
        private var controller: VaultController? = null
    }
}
