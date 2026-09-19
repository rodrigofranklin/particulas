package br.com.rfranklin.particulas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modo "Luz no escuro": a tela é preta; cada dedo é um emissor que solta uma
 * mistura de coisas brilhantes (faíscas, fumaça colorida, vento, estrelas,
 * bolhas, borboletas, corações) enquanto estiver na tela. Tudo tem vida curta:
 * sem toques, os elementos somem e a tela volta a ficar preta.
 */
class EmitterView(context: Context) : SimView(context) {

    // pool de entidades (structure of arrays)
    private val type = IntArray(MAX)
    private val ex = FloatArray(MAX)
    private val ey = FloatArray(MAX)
    private val evx = FloatArray(MAX)
    private val evy = FloatArray(MAX)
    private val life = FloatArray(MAX)
    private val maxLife = FloatArray(MAX)
    private val hue = FloatArray(MAX)
    private val size = FloatArray(MAX)
    private val phase = FloatArray(MAX)
    private val spin = FloatArray(MAX)
    private var next = 0
    private var alive = 0

    // por dedo
    private val fLastSeq = IntArray(MAX_FINGERS) { -1 }
    private val fPrevX = FloatArray(MAX_FINGERS)
    private val fPrevY = FloatArray(MAX_FINGERS)
    private val fAccum = FloatArray(MAX_FINGERS)   // emissões acumuladas (fração)

    // desenho
    private val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val hsv = FloatArray(3)
    private val puffShaders = Array(HUE_BINS) { hb ->
        val c = hsvColor((hb + 0.5f) * 360f / HUE_BINS, 0.8f, 1f)
        RadialGradient(0f, 0f, 1f, intArrayOf(withAlpha(c, 235), withAlpha(c, 110), withAlpha(c, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val matrix = Matrix()
    private val wing = Path()
    private val star = Path()
    private val heart = Path()
    private val wind = Path()
    private var glowRadius = 0f

    init {
        // formas unitárias (tamanho 1), escaladas na hora de desenhar
        // asa direita da borboleta: lóbulo superior grande + inferior menor
        wing.moveTo(0f, 0f)
        wing.cubicTo(0.45f, -0.9f, 1.15f, -0.75f, 1.05f, -0.15f)
        wing.cubicTo(0.95f, 0.15f, 0.5f, 0.1f, 0.15f, 0.05f)
        wing.cubicTo(0.7f, 0.25f, 0.85f, 0.75f, 0.45f, 0.75f)
        wing.cubicTo(0.2f, 0.75f, 0.05f, 0.4f, 0f, 0f)
        wing.close()
        for (k in 0 until 10) {
            val a = -1.5708f + k * 0.6283f
            val rr = if (k % 2 == 0) 1f else 0.45f
            if (k == 0) star.moveTo(cos(a) * rr, sin(a) * rr) else star.lineTo(cos(a) * rr, sin(a) * rr)
        }
        star.close()
        heart.moveTo(0f, 0.35f)
        heart.cubicTo(-1.1f, -0.45f, -0.55f, -1.1f, 0f, -0.5f)
        heart.cubicTo(0.55f, -1.1f, 1.1f, -0.45f, 0f, 0.35f)
        heart.close()
    }

    private fun hsvColor(h: Float, s: Float, v: Float): Int {
        hsv[0] = ((h % 360f) + 360f) % 360f; hsv[1] = s; hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    override fun onSizeReady(first: Boolean) {
        glowRadius = 90f * dp
    }

    // ================= emissão =================

    private fun spawn(t: Int, x: Float, y: Float, h: Float, fvx: Float, fvy: Float) {
        val i = next
        next = (next + 1) % MAX
        type[i] = t
        ex[i] = x
        ey[i] = y
        hue[i] = h + (rnd.nextFloat() - 0.5f) * 40f
        phase[i] = rnd.nextFloat() * 6.2832f
        val ang = rnd.nextFloat() * 6.2832f
        when (t) {
            SPARK -> {
                val sp = (3f + rnd.nextFloat() * 8f) * dp
                evx[i] = cos(ang) * sp + fvx * 0.3f; evy[i] = sin(ang) * sp + fvy * 0.3f
                maxLife[i] = 0.8f + rnd.nextFloat() * 0.9f; size[i] = (2.5f + rnd.nextFloat() * 2.5f) * dp
            }
            PUFF -> {
                val sp = (0.3f + rnd.nextFloat() * 1.2f) * dp
                evx[i] = cos(ang) * sp + fvx * 0.2f; evy[i] = sin(ang) * sp - 1.2f * dp + fvy * 0.2f
                maxLife[i] = 1.8f + rnd.nextFloat() * 1.4f; size[i] = (18f + rnd.nextFloat() * 26f) * dp
            }
            WIND -> {
                val sp = (9f + rnd.nextFloat() * 6f) * dp
                evx[i] = cos(ang) * sp + fvx * 0.5f; evy[i] = sin(ang) * sp + fvy * 0.5f
                maxLife[i] = 0.9f + rnd.nextFloat() * 0.6f; size[i] = (3f + rnd.nextFloat() * 2.5f) * dp
                spin[i] = if (rnd.nextBoolean()) 1f else -1f
            }
            STAR -> {
                val sp = (1.5f + rnd.nextFloat() * 4f) * dp
                evx[i] = cos(ang) * sp + fvx * 0.3f; evy[i] = sin(ang) * sp + fvy * 0.3f
                maxLife[i] = 1.6f + rnd.nextFloat() * 1.4f; size[i] = (6f + rnd.nextFloat() * 10f) * dp
                spin[i] = (rnd.nextFloat() - 0.5f) * 6f
            }
            BUBBLE -> {
                evx[i] = (rnd.nextFloat() - 0.5f) * 1.5f * dp + fvx * 0.2f; evy[i] = -(1f + rnd.nextFloat() * 1.5f) * dp
                maxLife[i] = 2f + rnd.nextFloat() * 2f; size[i] = (6f + rnd.nextFloat() * 14f) * dp
            }
            BUTTERFLY -> {
                val sp = (2f + rnd.nextFloat() * 2f) * dp
                evx[i] = cos(ang) * sp; evy[i] = sin(ang) * sp
                maxLife[i] = 4f + rnd.nextFloat() * 3f; size[i] = (12f + rnd.nextFloat() * 12f) * dp
                spin[i] = (rnd.nextFloat() - 0.5f) * 3f
            }
            HEART -> {
                evx[i] = (rnd.nextFloat() - 0.5f) * 2f * dp + fvx * 0.2f; evy[i] = -(1.5f + rnd.nextFloat() * 2f) * dp
                maxLife[i] = 2f + rnd.nextFloat() * 1.5f; size[i] = (7f + rnd.nextFloat() * 10f) * dp
                hue[i] = if (rnd.nextFloat() < 0.5f) 340f + rnd.nextFloat() * 30f else hue[i]
            }
            RING -> {
                evx[i] = 0f; evy[i] = 0f
                maxLife[i] = 0.7f; size[i] = 0f
            }
        }
        life[i] = maxLife[i]
    }

    private fun pickType(): Int {
        val r = rnd.nextFloat()
        return when {
            r < 0.32f -> SPARK
            r < 0.52f -> PUFF
            r < 0.64f -> WIND
            r < 0.75f -> STAR
            r < 0.84f -> BUBBLE
            r < 0.93f -> BUTTERFLY
            else -> HEART
        }
    }

    override fun step(dt: Float) {
        val s = dt * 60f

        // emissores
        for (f in 0 until MAX_FINGERS) {
            if (!fActive[f]) { fLastSeq[f] = -1; continue }
            val x = fX[f]; val y = fY[f]
            if (fSeq[f] != fLastSeq[f]) {
                fLastSeq[f] = fSeq[f]
                fPrevX[f] = x; fPrevY[f] = y
                fAccum[f] = 0f
                spawn(RING, x, y, fHue[f], 0f, 0f)
            }
            val fvx = (x - fPrevX[f]) / s      // px por frame
            val fvy = (y - fPrevY[f]) / s
            fPrevX[f] = x; fPrevY[f] = y
            val speed = sqrt(fvx * fvx + fvy * fvy)
            val rate = 55f + min(speed / dp, 25f) * 8f      // por segundo (mais rápido = mais coisas)
            fAccum[f] += rate * dt
            var k = 0
            while (fAccum[f] >= 1f && k < 32) {
                fAccum[f] -= 1f
                k++
                // nasce num ponto entre a posição anterior e a atual (sem buracos ao arrastar)
                val u = rnd.nextFloat()
                spawn(pickType(), x - fvx * s * u, y - fvy * s * u, fHue[f], fvx, fvy)
            }
        }

        // integração
        alive = 0
        val sparkDamp = pow(0.96f, s)
        for (i in 0 until MAX) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            if (life[i] <= 0f) continue
            alive++
            val t = maxLife[i] - life[i]
            when (type[i]) {
                SPARK -> {
                    evy[i] += 0.12f * dp * s
                    evx[i] *= sparkDamp; evy[i] *= sparkDamp
                }
                PUFF -> { evx[i] *= pow(0.985f, s); evy[i] -= 0.01f * dp * s }
                WIND -> {
                    // serpenteia perpendicular ao movimento
                    val wob = sin(t * 9f + phase[i]) * 0.35f * spin[i]
                    val vx0 = evx[i]; val vy0 = evy[i]
                    evx[i] = vx0 + (-vy0) * wob * dt * 4f
                    evy[i] = vy0 + vx0 * wob * dt * 4f
                }
                BUTTERFLY -> {
                    // voa em curvas suaves, batendo asas, com pequenas subidas
                    val turn = sin(t * 1.3f + phase[i]) * 1.6f + spin[i] * 0.4f
                    val vx0 = evx[i]; val vy0 = evy[i]
                    evx[i] = vx0 * cos(turn * dt) - vy0 * sin(turn * dt)
                    evy[i] = vx0 * sin(turn * dt) + vy0 * cos(turn * dt) - 0.02f * dp * s
                }
                BUBBLE, HEART -> evx[i] += sin(t * 3f + phase[i]) * 0.06f * dp * s
                STAR -> { evx[i] *= pow(0.98f, s); evy[i] *= pow(0.98f, s) }
            }
            ex[i] += evx[i] * s
            ey[i] += evy[i] * s
            // fora da tela: morre
            if (ex[i] < -80f * dp || ex[i] > w + 80f * dp || ey[i] < -80f * dp || ey[i] > h + 80f * dp) life[i] = 0f
        }
    }

    // ================= desenho =================

    override fun render(c: Canvas) {
        c.drawColor(Color.BLACK)

        // halo do dedo
        for (f in 0 until MAX_FINGERS) {
            if (!fActive[f]) continue
            val hb = hueBin(fHue[f])
            val sc = glowRadius * (1f + 0.15f * sin(fAge[f] * 6f))
            matrix.reset(); matrix.setScale(sc, sc); matrix.postTranslate(fX[f], fY[f])
            val sh = puffShaders[hb]
            sh.setLocalMatrix(matrix)
            puffPaint.shader = sh
            puffPaint.alpha = 255
            c.drawCircle(fX[f], fY[f], sc, puffPaint)
        }

        for (i in 0 until MAX) {
            val l = life[i]
            if (l <= 0f) continue
            val ml = maxLife[i]
            val t = ml - l
            val fadeOut = min(1f, l / (ml * 0.35f))       // some no último terço
            val fadeIn = min(1f, t * 8f)
            val a = fadeOut * fadeIn
            val x = ex[i]; val y = ey[i]
            when (type[i]) {
                SPARK -> {
                    val x0 = x - evx[i] * 2.5f
                    val y0 = y - evy[i] * 2.5f
                    addPaint.strokeCap = Paint.Cap.ROUND
                    // halo largo e fraco + núcleo brilhante
                    addPaint.color = hsvColor(hue[i], 0.9f, 1f)
                    addPaint.alpha = (90 * a).toInt()
                    addPaint.strokeWidth = size[i] * 3f
                    c.drawLine(x0, y0, x, y, addPaint)
                    addPaint.color = hsvColor(hue[i], 0.45f, 1f)
                    addPaint.alpha = (255 * a).toInt()
                    addPaint.strokeWidth = size[i]
                    c.drawLine(x0, y0, x, y, addPaint)
                    addPaint.strokeWidth = 0f
                }
                PUFF -> {
                    val grow = size[i] * (0.4f + 0.6f * min(1f, t / ml))
                    matrix.reset(); matrix.setScale(grow, grow); matrix.postTranslate(x, y)
                    val sh = puffShaders[hueBin(hue[i])]
                    sh.setLocalMatrix(matrix)
                    puffPaint.shader = sh
                    puffPaint.alpha = (200 * a).toInt()
                    c.drawCircle(x, y, grow, puffPaint)
                }
                WIND -> {
                    // rastro ondulado atrás da cabeça
                    val vx0 = evx[i]; val vy0 = evy[i]
                    val len = 9f
                    val nx = -vy0; val ny = vx0
                    val wob = sin(t * 9f + phase[i]) * 1.2f * spin[i]
                    wind.reset()
                    wind.moveTo(x, y)
                    wind.cubicTo(x - vx0 * len * 0.33f + nx * wob, y - vy0 * len * 0.33f + ny * wob,
                        x - vx0 * len * 0.66f - nx * wob, y - vy0 * len * 0.66f - ny * wob,
                        x - vx0 * len, y - vy0 * len)
                    strokePaint.color = hsvColor(hue[i], 0.3f, 1f)
                    strokePaint.alpha = (235 * a).toInt()
                    strokePaint.strokeWidth = size[i]
                    strokePaint.xfermode = addPaint.xfermode
                    c.drawPath(wind, strokePaint)
                    strokePaint.xfermode = null
                }
                STAR -> {
                    val sc = size[i] * (0.75f + 0.25f * sin(t * 7f + phase[i]))
                    c.save()
                    c.translate(x, y)
                    c.rotate(t * spin[i] * 57.3f)
                    c.scale(sc, sc)
                    addPaint.color = hsvColor(hue[i], 0.35f, 1f)
                    addPaint.alpha = (230 * a).toInt()
                    c.drawPath(star, addPaint)
                    c.restore()
                }
                BUBBLE -> {
                    val pop = l < 0.15f
                    val rr = size[i] * (if (pop) 1f + (0.15f - l) * 6f else 1f + 0.05f * sin(t * 5f + phase[i]))
                    strokePaint.color = hsvColor(hue[i], 0.3f, 1f)
                    strokePaint.alpha = (if (pop) 255 * (l / 0.15f) else 200 * a).toInt()
                    strokePaint.strokeWidth = 1.6f * dp
                    c.drawCircle(x, y, rr, strokePaint)
                    fillPaint.color = Color.WHITE
                    fillPaint.alpha = (200 * a).toInt()
                    c.drawCircle(x - rr * 0.35f, y - rr * 0.35f, rr * 0.18f, fillPaint)
                }
                BUTTERFLY -> {
                    val heading = Math.toDegrees(Math.atan2(evy[i].toDouble(), evx[i].toDouble())).toFloat() + 90f
                    val flap = abs(cos(t * 16f + phase[i])) * 0.85f + 0.15f
                    val sc = size[i]
                    c.save()
                    c.translate(x, y)
                    c.rotate(heading)
                    c.scale(sc, sc)
                    val c1 = hsvColor(hue[i], 0.85f, 1f)
                    val c2 = hsvColor(hue[i] + 40f, 0.9f, 0.85f)
                    // asa direita
                    c.save(); c.scale(flap, 1f)
                    fillPaint.color = c1; fillPaint.alpha = (240 * a).toInt(); c.drawPath(wing, fillPaint)
                    c.scale(0.55f, 0.55f); c.translate(0.25f, -0.05f)
                    fillPaint.color = c2; fillPaint.alpha = (240 * a).toInt(); c.drawPath(wing, fillPaint)
                    c.restore()
                    // asa esquerda (espelhada)
                    c.save(); c.scale(-flap, 1f)
                    fillPaint.color = c1; fillPaint.alpha = (240 * a).toInt(); c.drawPath(wing, fillPaint)
                    c.scale(0.55f, 0.55f); c.translate(0.25f, -0.05f)
                    fillPaint.color = c2; fillPaint.alpha = (240 * a).toInt(); c.drawPath(wing, fillPaint)
                    c.restore()
                    // corpo
                    strokePaint.color = Color.rgb(40, 30, 50); strokePaint.alpha = (255 * a).toInt()
                    strokePaint.strokeWidth = 0.16f
                    c.drawLine(0f, -0.55f, 0f, 0.55f, strokePaint)
                    c.restore()
                }
                HEART -> {
                    val sc = size[i] * (0.9f + 0.1f * sin(t * 6f))
                    c.save()
                    c.translate(x, y)
                    c.rotate(sin(t * 2f + phase[i]) * 15f)
                    c.scale(sc, sc)
                    fillPaint.color = hsvColor(hue[i], 0.8f, 1f)
                    fillPaint.alpha = (235 * a).toInt()
                    c.drawPath(heart, fillPaint)
                    c.restore()
                }
                RING -> {
                    val k = t / ml
                    strokePaint.color = hsvColor(hue[i], 0.4f, 1f)
                    strokePaint.alpha = (220 * (1f - k)).toInt()
                    strokePaint.strokeWidth = (6f - 4f * k) * dp
                    strokePaint.xfermode = addPaint.xfermode
                    c.drawCircle(x, y, (10f + 150f * k) * dp, strokePaint)
                    strokePaint.xfermode = null
                }
            }
        }
    }

    private fun hueBin(h: Float): Int {
        var hh = h % 360f
        if (hh < 0f) hh += 360f
        return (hh * HUE_BINS / 360f).toInt().coerceIn(0, HUE_BINS - 1)
    }

    private companion object {
        const val MAX = 1600
        const val HUE_BINS = 12
        const val SPARK = 0
        const val PUFF = 1
        const val WIND = 2
        const val STAR = 3
        const val BUBBLE = 4
        const val BUTTERFLY = 5
        const val HEART = 6
        const val RING = 7
    }
}
