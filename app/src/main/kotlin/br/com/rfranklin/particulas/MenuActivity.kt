package br.com.rfranklin.particulas

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tela inicial: três botões grandes, um por modo. Aqui a tarefa é fixada
 * (screen pinning); uma tecla física nesta tela desfixa e fecha o app.
 */
class MenuActivity : KioskActivity() {

    private var pinRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 15))
            val pad = (20 * dp).toInt()
            setPadding(pad, (48 * dp).toInt(), pad, (32 * dp).toInt())
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20 * dp).toInt())
        })

        addModeButton(root, MainActivity.MODE_PARTICLES, "✨", getString(R.string.mode_particles),
            Color.rgb(20, 90, 200), Color.rgb(120, 40, 200))
        addModeButton(root, MainActivity.MODE_FLUID, "🌊", getString(R.string.mode_fluid),
            Color.rgb(0, 150, 140), Color.rgb(230, 60, 120))
        addModeButton(root, MainActivity.MODE_DARK, "🌙", getString(R.string.mode_dark),
            Color.rgb(25, 25, 45), Color.rgb(90, 70, 160))

        setContentView(root)
    }

    private fun addModeButton(parent: LinearLayout, mode: Int, emoji: String, label: String, c1: Int, c2: Int) {
        val dp = resources.displayMetrics.density
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
                cornerRadius = 28 * dp
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { open(mode) }
        }
        button.addView(TextView(this).apply {
            text = emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
        })
        button.addView(TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        parent.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(0, (10 * dp).toInt(), 0, (10 * dp).toInt()) })
    }

    private fun open(mode: Int) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_MODE, mode))
    }

    override fun onResume() {
        super.onResume()
        pinScreen()
    }

    /**
     * Pede a fixação de tela uma vez por execução. O Android mostra um diálogo de
     * confirmação (e exige que a fixação de tela esteja ativa nas configurações de segurança).
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

    override fun onHardwareKey() {
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
        finishAndRemoveTask()
    }
}
