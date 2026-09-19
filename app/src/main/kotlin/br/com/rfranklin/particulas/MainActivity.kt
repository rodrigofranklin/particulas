package br.com.rfranklin.particulas

import android.os.Bundle
import android.view.View

/** Tela da brincadeira: só a superfície do modo escolhido. Tecla física volta ao menu. */
class MainActivity : KioskActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view: View = when (intent.getIntExtra(EXTRA_MODE, MODE_PARTICLES)) {
            MODE_FLUID -> FluidView(this)
            MODE_DARK -> EmitterView(this)
            else -> ParticleView(this)
        }
        setContentView(view)
    }

    override fun onHardwareKey() {
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PARTICLES = 0
        const val MODE_FLUID = 1
        const val MODE_DARK = 2
    }
}
