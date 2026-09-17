package br.com.rfranklin.particulas

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * Tela única, imersiva e fixada (screen pinning): a criança só interage com a
 * superfície de partículas; Home/Recentes ficam bloqueados enquanto fixado.
 * Qualquer tecla física entregue ao app (voltar, volume, câmera...) desfixa e encerra.
 */
class MainActivity : Activity() {

    private lateinit var particleView: ParticleView
    private var pinRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Desenha também por trás do notch / recorte da câmera.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        particleView = ParticleView(this)
        setContentView(particleView)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        pinScreen()
    }

    /**
     * Pede a fixação de tela uma vez por execução. O Android mostra um diálogo de
     * confirmação (e exige que "Fixar tela" esteja ativo nas configurações de segurança).
     */
    private fun pinScreen() {
        if (pinRequested) return
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            pinRequested = true
            try {
                startLockTask()
            } catch (_: Exception) {
            }
        }
    }

    private fun exitApp() {
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
        finishAndRemoveTask()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Qualquer tecla física fecha o app. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            exitApp()
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        exitApp()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                // Se a criança arrastar da borda, as barras aparecem por ~3 s e somem de novo.
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
