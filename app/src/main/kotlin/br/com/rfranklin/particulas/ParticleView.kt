package br.com.rfranklin.particulas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Milhares de partículas coloridas num SurfaceView com thread própria.
 *
 * - Sem dedo: as partículas flutuam num campo de fluxo suave.
 * - Cada dedo (até o limite do hardware, normalmente 10) vira um vórtice com cor
 *   própria: atrai, faz girar, e as partículas próximas brilham e mudam de cor.
 * - Ao tirar o dedo: explosão que espalha as partículas com a cor daquele dedo.
 */
class ParticleView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // ---------- partículas ----------
    private var n = 0
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var hue = FloatArray(0)
    private var glow = FloatArray(0)

    // ---------- dedos (escrito pela UI thread, lido pela thread de física) ----------
    private val lock = Any()
    private val fingerId = IntArray(MAX_FINGERS) { -1 }
    private val fingerX = FloatArray(MAX_FINGERS)
    private val fingerY = FloatArray(MAX_FINGERS)
    private val fingerHue = FloatArray(MAX_FINGERS)
    private val fingerAge = FloatArray(MAX_FINGERS)
    private val fingerSeq = IntArray(MAX_FINGERS)
    private var seqCounter = 0
    private var nextHue = Random.nextFloat() * 360f

    // explosões pendentes (x, y, hue) — geradas quando um dedo sai da tela
    private val burstX = FloatArray(MAX_BURSTS)
    private val burstY = FloatArray(MAX_BURSTS)
    private val burstHue = FloatArray(MAX_BURSTS)
    private val burstStrength = FloatArray(MAX_BURSTS)
    private var burstCount = 0

    // cópias locais usadas pela thread de física (evita segurar o lock durante o passo)
    private val fActive = BooleanArray(MAX_FINGERS)
    private val fX = FloatArray(MAX_FINGERS)
    private val fY = FloatArray(MAX_FINGERS)
    private val fHue = FloatArray(MAX_FINGERS)
    private val fAge = FloatArray(MAX_FINGERS)
    private val fSeq = IntArray(MAX_FINGERS)
    private val fLastSeq = IntArray(MAX_FINGERS) { -1 }
    private val fPulses = IntArray(MAX_FINGERS)      // pulsos já emitidos por este dedo
    private val fSpin = FloatArray(MAX_FINGERS)      // sentido de rotação (+1/-1)
    private val fRadialK = FloatArray(MAX_FINGERS)   // fator de atração (negativo = repele)
    private val fSwirlK = FloatArray(MAX_FINGERS)    // fator de rotação
    private val bX = FloatArray(MAX_BURSTS)
    private val bY = FloatArray(MAX_BURSTS)
    private val bHue = FloatArray(MAX_BURSTS)
    private val bStrength = FloatArray(MAX_BURSTS)
    private var bCount = 0

    // ---------- trilhas do arraste ----------
    // fita: ring buffer de pontos por dedo (continua existindo um pouco após soltar)
    private val tX = FloatArray(MAX_FINGERS * TRAIL_LEN)
    private val tY = FloatArray(MAX_FINGERS * TRAIL_LEN)
    private val tHead = IntArray(MAX_FINGERS)
    private val tCount = IntArray(MAX_FINGERS)
    private val tHue = FloatArray(MAX_FINGERS)
    private val fPrevX = FloatArray(MAX_FINGERS)
    private val fPrevY = FloatArray(MAX_FINGERS)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // faíscas: pool fixo, emitidas ao longo do caminho do dedo
    private val sX = FloatArray(MAX_SPARKS)
    private val sY = FloatArray(MAX_SPARKS)
    private val sVX = FloatArray(MAX_SPARKS)
    private val sVY = FloatArray(MAX_SPARKS)
    private val sLife = FloatArray(MAX_SPARKS)
    private val sHue = FloatArray(MAX_SPARKS)
    private var sNext = 0

    // ---------- geometria / constantes em pixels ----------
    private val dp = resources.displayMetrics.density
    @Volatile private var pendingW = 0
    @Volatile private var pendingH = 0
    private var w = 0
    private var h = 0
    private var reach = 0f          // raio de influência do dedo
    private var burstReach = 0f     // raio da explosão
    private val core = 34f * dp     // núcleo que empurra (evita colapsar num ponto)
    private val pull = 0.34f * dp
    private val swirl = 0.55f * dp
    private val corePush = 1.6f * dp
    private val flowForce = 0.035f * dp
    private val burstImpulse = 22f * dp
    private val vMax = 14f * dp
    private val damping = 0.972f

    // ---------- desenho ----------
    private val bins = HUE_BINS * GLOW_LEVELS
    private val binPaints = Array(bins) { Paint() }
    private var binLines = Array(bins) { FloatArray(0) }
    private val binCounts = IntArray(bins)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowMatrix = Matrix()
    private val glowRadius = 110f * dp
    private val hsv = FloatArray(3)

    private var thread: Thread? = null
    @Volatile private var running = false
    private var time = 0f
    private val rnd = Random(System.nanoTime())

    init {
        holder.addCallback(this)
        isFocusable = true
        setupPaints()
    }

    private fun setupPaints() {
        val widths = floatArrayOf(2.2f, 3.2f, 4.6f, 6.8f)
        val sats = floatArrayOf(0.95f, 0.85f, 0.55f, 0.22f)
        val vals = floatArrayOf(0.82f, 0.92f, 1f, 1f)
        val alphas = intArrayOf(200, 225, 255, 255)
        for (hb in 0 until HUE_BINS) {
            for (gl in 0 until GLOW_LEVELS) {
                val p = binPaints[hb * GLOW_LEVELS + gl]
                p.isAntiAlias = true
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = widths[gl] * dp
                hsv[0] = (hb + 0.5f) * (360f / HUE_BINS)
                hsv[1] = sats[gl]
                hsv[2] = vals[gl]
                p.color = Color.HSVToColor(alphas[gl], hsv)
                // Soma de luz: partículas sobrepostas ficam mais brilhantes.
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
        }
        glowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        trailPaint.style = Paint.Style.STROKE
        trailPaint.strokeCap = Paint.Cap.ROUND
        trailPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    // ================= ciclo de vida da surface =================

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "particles").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        pendingW = width
        pendingH = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
        thread = null
    }

    private fun applyResize() {
        val nw = pendingW
        val nh = pendingH
        if (nw == w && nh == h) return
        val first = n == 0
        w = nw
        h = nh
        reach = 0.42f * min(w, h)
        burstReach = 0.6f * min(w, h)
        if (first) {
            n = ((w.toLong() * h) / (300 * dp * dp).toLong()).toInt().coerceIn(1500, 6000)
            px = FloatArray(n) { rnd.nextFloat() * w }
            py = FloatArray(n) { rnd.nextFloat() * h }
            vx = FloatArray(n) { (rnd.nextFloat() - 0.5f) * dp }
            vy = FloatArray(n) { (rnd.nextFloat() - 0.5f) * dp }
            hue = FloatArray(n) { rnd.nextFloat() * 360f }
            glow = FloatArray(n)
            binLines = Array(bins) { FloatArray((n + MAX_SPARKS) * 4) }
        } else {
            for (i in 0 until n) {
                px[i] = px[i].coerceIn(0f, w - 1f)
                py[i] = py[i].coerceIn(0f, h - 1f)
            }
        }
    }

    // ================= loop principal =================

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
            last = now

            applyResize()
            if (w == 0 || h == 0) {
                Thread.sleep(8)
                continue
            }

            snapshotInput(dt)
            step(dt)

            val canvas: Canvas = try {
                holder.lockHardwareCanvas() ?: continue
            } catch (e: Exception) {
                break
            }
            try {
                render(canvas)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun snapshotInput(dt: Float) {
        synchronized(lock) {
            for (i in 0 until MAX_FINGERS) {
                fActive[i] = fingerId[i] >= 0
                if (fActive[i]) {
                    fingerAge[i] += dt
                    fX[i] = fingerX[i]
                    fY[i] = fingerY[i]
                    fHue[i] = heldHue(i)
                    fAge[i] = fingerAge[i]
                    fSeq[i] = fingerSeq[i]
                }
            }
            bCount = burstCount
            for (i in 0 until bCount) {
                bX[i] = burstX[i]
                bY[i] = burstY[i]
                bHue[i] = burstHue[i]
                bStrength[i] = burstStrength[i]
            }
            burstCount = 0
        }
    }

    /** Cor do dedo: a inicial girando devagar enquanto ele fica na tela (arco-íris). */
    private fun heldHue(slot: Int): Float = (fingerHue[slot] + fingerAge[slot] * HUE_SPIN) % 360f

    /**
     * Evolução de um dedo segurado: respiração (puxa/empurra), rotação que
     * oscila e inverte, e um pulso de choque a cada [PULSE_PERIOD] segundos.
     */
    private fun updateHeldFingers() {
        for (f in 0 until MAX_FINGERS) {
            if (!fActive[f]) {
                fLastSeq[f] = -1
                continue
            }
            if (fSeq[f] != fLastSeq[f]) {       // dedo novo neste slot
                fLastSeq[f] = fSeq[f]
                fPulses[f] = 0
                fSpin[f] = if (rnd.nextBoolean()) 1f else -1f
                tCount[f] = 0
                fPrevX[f] = fX[f]
                fPrevY[f] = fY[f]
            }
            tHue[f] = fHue[f]
            val age = fAge[f]
            val breathe = sin(age * 1.4f)                    // ciclo de ~4,5 s
            fRadialK[f] = 0.6f + 0.8f * breathe              // -0,2 .. 1,4
            fSwirlK[f] = (1f + 0.6f * cos(age * 0.9f)) * fSpin[f]

            val due = (age / PULSE_PERIOD).toInt()
            if (due > fPulses[f]) {
                fPulses[f] = due
                fSpin[f] = -fSpin[f]
                if (bCount < MAX_BURSTS) {
                    bX[bCount] = fX[f]
                    bY[bCount] = fY[f]
                    bHue[bCount] = fHue[f]
                    bStrength[bCount] = 0.6f
                    bCount++
                }
            }
        }
    }

    // ================= física =================

    private fun step(dt: Float) {
        val s = dt * 60f            // escala "por frame a 60 Hz"
        time += dt
        val t = time
        val reach2 = reach * reach
        val flowK = 0.0345f / dp
        val jitter = 0.05f * dp
        val dampS = pow(damping, s)
        val glowDecay = pow(0.9f, s)
        val vMax2 = vMax * vMax

        updateHeldFingers()
        updateTrails(dt, s)

        // explosões (dedo saiu da tela ou pulso de dedo segurado)
        for (b in 0 until bCount) {
            val bx = bX[b]
            val by = bY[b]
            val bh = bHue[b]
            val str = bStrength[b]
            val r = burstReach * (0.6f + 0.4f * str)
            val r2 = r * r
            for (i in 0 until n) {
                val dx = px[i] - bx
                val dy = py[i] - by
                val d2 = dx * dx + dy * dy
                if (d2 < r2) {
                    val d = sqrt(d2) + 1f
                    val q = 1f - d / r
                    val imp = burstImpulse * str * q * q
                    vx[i] += dx / d * imp
                    vy[i] += dy / d * imp
                    if (q > glow[i]) glow[i] = q
                    hue[i] = lerpHue(hue[i], bh, q * 0.8f)
                }
            }
        }

        for (i in 0 until n) {
            var x = px[i]
            var y = py[i]
            // campo de fluxo rotacional (sem divergência): as partículas circulam
            // em células suaves em vez de se acumularem nas bordas
            val a = x * flowK + t * 0.31f
            val b = y * flowK * 1.37f + t * 0.23f
            val cc = x * flowK * 0.6f - t * 0.17f
            val d = y * flowK * 0.8f + t * 0.29f
            var ax = flowForce * (1.37f * sin(a) * cos(b) - 0.8f * cos(cc) * sin(d)) +
                (rnd.nextFloat() - 0.5f) * jitter
            var ay = -flowForce * (cos(a) * sin(b) - 0.6f * sin(cc) * cos(d)) +
                (rnd.nextFloat() - 0.5f) * jitter
            var g = 0f
            var targetHue = 0f

            for (f in 0 until MAX_FINGERS) {
                if (!fActive[f]) continue
                val dx = fX[f] - x
                val dy = fY[f] - y
                val d2 = dx * dx + dy * dy
                if (d2 >= reach2) continue
                val d = sqrt(d2) + 1f
                val q = 1f - d / reach
                val q2 = q * q
                var radial = pull * q2 * fRadialK[f]
                if (d < core) radial -= corePush * (1f - d / core)
                val tang = swirl * q2 * fSwirlK[f]
                val ux = dx / d
                val uy = dy / d
                ax += ux * radial - uy * tang
                ay += uy * radial + ux * tang
                if (q2 > g) {
                    g = q2
                    targetHue = fHue[f]
                }
            }

            var cvx = vx[i] + ax * s
            var cvy = vy[i] + ay * s
            val sp2 = cvx * cvx + cvy * cvy
            if (sp2 > vMax2) {
                val k = vMax / sqrt(sp2)
                cvx *= k
                cvy *= k
            }
            cvx *= dampS
            cvy *= dampS
            x += cvx * s
            y += cvy * s

            // quicar nas bordas
            if (x < 0f) { x = -x; cvx = abs(cvx) * 0.8f }
            else if (x >= w) { x = 2f * w - x - 1f; cvx = -abs(cvx) * 0.8f }
            if (y < 0f) { y = -y; cvy = abs(cvy) * 0.8f }
            else if (y >= h) { y = 2f * h - y - 1f; cvy = -abs(cvy) * 0.8f }
            if (x < 0f || x >= w) x = rnd.nextFloat() * w
            if (y < 0f || y >= h) y = rnd.nextFloat() * h

            px[i] = x
            py[i] = y
            vx[i] = cvx
            vy[i] = cvy

            val old = glow[i] * glowDecay
            glow[i] = if (g > old) g else old
            hue[i] = if (g > 0f) {
                lerpHue(hue[i], targetHue, min(1f, 0.12f * g * s))
            } else {
                hue[i] + 0.04f * s
            }
        }
    }

    /** Fita atrás de cada dedo + faíscas lançadas do caminho percorrido. */
    private fun updateTrails(dt: Float, s: Float) {
        val sparkDamp = pow(0.93f, s)
        for (f in 0 until MAX_FINGERS) {
            if (fActive[f]) {
                // fita: acrescenta o ponto atual
                tHead[f] = (tHead[f] + 1) % TRAIL_LEN
                tX[f * TRAIL_LEN + tHead[f]] = fX[f]
                tY[f * TRAIL_LEN + tHead[f]] = fY[f]
                if (tCount[f] < TRAIL_LEN) tCount[f]++

                // faíscas: quantidade proporcional ao deslocamento neste frame
                val dx = fX[f] - fPrevX[f]
                val dy = fY[f] - fPrevY[f]
                val dist = sqrt(dx * dx + dy * dy)
                val count = min((dist / (1.5f * dp)).toInt(), 14)
                for (k in 0 until count) {
                    val u = (k + rnd.nextFloat()) / count
                    val i = sNext
                    sNext = (sNext + 1) % MAX_SPARKS
                    sX[i] = fPrevX[f] + dx * u
                    sY[i] = fPrevY[f] + dy * u
                    // sai para os lados do movimento, com um pouco do impulso do dedo
                    val side = (rnd.nextFloat() - 0.5f) * 2f
                    val nx = -dy / (dist + 0.01f)
                    val ny = dx / (dist + 0.01f)
                    val spd = (2f + rnd.nextFloat() * 5f) * dp
                    sVX[i] = nx * side * spd + dx * 0.15f + (rnd.nextFloat() - 0.5f) * dp
                    sVY[i] = ny * side * spd + dy * 0.15f + (rnd.nextFloat() - 0.5f) * dp
                    sLife[i] = 0.5f + rnd.nextFloat() * 0.7f
                    sHue[i] = fHue[f] + (rnd.nextFloat() - 0.5f) * 40f
                }
                if (count == 0) {
                    // dedo parado: chafariz em espiral, para o centro nunca ficar vazio
                    for (k in 0 until 2) {
                        val i = sNext
                        sNext = (sNext + 1) % MAX_SPARKS
                        val ang = fAge[f] * 4f * fSpin[f] + k * 3.1416f + (rnd.nextFloat() - 0.5f) * 0.4f
                        val spd = (2.5f + rnd.nextFloat() * 3f) * dp
                        sX[i] = fX[f]
                        sY[i] = fY[f]
                        sVX[i] = cos(ang) * spd
                        sVY[i] = sin(ang) * spd
                        sLife[i] = 0.6f + rnd.nextFloat() * 0.6f
                        sHue[i] = fHue[f] + (rnd.nextFloat() - 0.5f) * 30f
                    }
                }
                fPrevX[f] = fX[f]
                fPrevY[f] = fY[f]
            } else if (tCount[f] > 0) {
                // dedo saiu: a fita vai encurtando pela cauda
                tCount[f] = (tCount[f] - (3f * s).toInt().coerceAtLeast(1)).coerceAtLeast(0)
            }
        }

        for (i in 0 until MAX_SPARKS) {
            if (sLife[i] <= 0f) continue
            sLife[i] -= dt
            sX[i] += sVX[i] * s
            sY[i] += sVY[i] * s
            sVX[i] *= sparkDamp
            sVY[i] *= sparkDamp
        }
    }

    private fun lerpHue(from: Float, to: Float, k: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return from + d * k
    }

    private fun pow(base: Float, e: Float): Float = Math.pow(base.toDouble(), e.toDouble()).toFloat()

    // ================= desenho =================

    private fun render(c: Canvas) {
        c.drawColor(Color.BLACK)

        // halo suave em cada dedo, crescendo um pouco enquanto segura
        for (f in 0 until MAX_FINGERS) {
            if (!fActive[f]) continue
            hsv[0] = fHue[f]; hsv[1] = 0.75f; hsv[2] = 1f
            val col = Color.HSVToColor(hsv)
            val shader = RadialGradient(
                0f, 0f, glowRadius,
                intArrayOf(withAlpha(col, 150), withAlpha(col, 60), withAlpha(col, 0)),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            val scale = 1.25f + 0.35f * sin(fAge[f] * 1.4f)
            glowMatrix.reset()
            glowMatrix.setScale(scale, scale)
            glowMatrix.postTranslate(fX[f], fY[f])
            shader.setLocalMatrix(glowMatrix)
            glowPaint.shader = shader
            c.drawCircle(fX[f], fY[f], glowRadius * scale, glowPaint)
        }

        // fitas: da cabeça (grossa e forte) para a cauda (fina e apagada)
        for (f in 0 until MAX_FINGERS) {
            val cnt = tCount[f]
            if (cnt < 2) continue
            hsv[0] = tHue[f]; hsv[1] = 0.6f; hsv[2] = 1f
            val col = Color.HSVToColor(hsv)
            val base = f * TRAIL_LEN
            var idx = tHead[f]
            var xPrev = tX[base + idx]
            var yPrev = tY[base + idx]
            for (k in 1 until cnt) {
                idx = (idx - 1 + TRAIL_LEN) % TRAIL_LEN
                val x = tX[base + idx]
                val y = tY[base + idx]
                val fade = 1f - k.toFloat() / TRAIL_LEN
                trailPaint.strokeWidth = (1f + 11f * fade) * dp
                trailPaint.color = withAlpha(col, (210 * fade * fade).toInt())
                c.drawLine(xPrev, yPrev, x, y, trailPaint)
                xPrev = x
                yPrev = y
            }
        }

        // partículas agrupadas por (matiz, brilho) para desenhar em poucos lotes
        java.util.Arrays.fill(binCounts, 0)
        val hueScale = HUE_BINS / 360f
        for (i in 0 until n) {
            var hh = hue[i] % 360f
            if (hh < 0f) hh += 360f
            hue[i] = hh
            val hb = (hh * hueScale).toInt().coerceIn(0, HUE_BINS - 1)
            val g = glow[i]
            val gl = when {
                g < 0.12f -> 0
                g < 0.4f -> 1
                g < 0.72f -> 2
                else -> 3
            }
            val bin = hb * GLOW_LEVELS + gl
            val arr = binLines[bin]
            val k = binCounts[bin]
            val x = px[i]
            val y = py[i]
            var x0 = x - vx[i] * TRAIL
            var y0 = y - vy[i] * TRAIL
            if (abs(x0 - x) + abs(y0 - y) < 0.8f) { x0 = x + 0.6f; y0 = y }
            arr[k] = x0
            arr[k + 1] = y0
            arr[k + 2] = x
            arr[k + 3] = y
            binCounts[bin] = k + 4
        }
        // faíscas entram nos mesmos lotes; quanto mais novas, mais brancas
        for (i in 0 until MAX_SPARKS) {
            val life = sLife[i]
            if (life <= 0f) continue
            var hh = sHue[i] % 360f
            if (hh < 0f) hh += 360f
            val hb = (hh * hueScale).toInt().coerceIn(0, HUE_BINS - 1)
            val gl = when {
                life > 0.55f -> 3
                life > 0.3f -> 2
                else -> 1
            }
            val bin = hb * GLOW_LEVELS + gl
            val arr = binLines[bin]
            val k = binCounts[bin]
            val x = sX[i]
            val y = sY[i]
            var x0 = x - sVX[i] * 1.5f
            var y0 = y - sVY[i] * 1.5f
            if (abs(x0 - x) + abs(y0 - y) < 0.8f) { x0 = x + 0.6f; y0 = y }
            arr[k] = x0
            arr[k + 1] = y0
            arr[k + 2] = x
            arr[k + 3] = y
            binCounts[bin] = k + 4
        }
        for (b in 0 until bins) {
            val cnt = binCounts[b]
            if (cnt > 0) c.drawLines(binLines[b], 0, cnt, binPaints[b])
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    // ================= toque =================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                fingerDown(event.getPointerId(idx), event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> synchronized(lock) {
                for (idx in 0 until event.pointerCount) {
                    val slot = slotOf(event.getPointerId(idx))
                    if (slot >= 0) {
                        fingerX[slot] = event.getX(idx)
                        fingerY[slot] = event.getY(idx)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val idx = event.actionIndex
                fingerUp(event.getPointerId(idx), event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_CANCEL -> synchronized(lock) {
                for (i in 0 until MAX_FINGERS) fingerId[i] = -1
            }
        }
        return true
    }

    private fun slotOf(id: Int): Int {
        for (i in 0 until MAX_FINGERS) if (fingerId[i] == id) return i
        return -1
    }

    private fun fingerDown(id: Int, x: Float, y: Float): Unit = synchronized(lock) {
        var slot = slotOf(id)
        if (slot < 0) slot = slotOf(-1)
        if (slot < 0) return
        fingerId[slot] = id
        fingerX[slot] = x
        fingerY[slot] = y
        fingerAge[slot] = 0f
        fingerSeq[slot] = ++seqCounter
        fingerHue[slot] = nextHue
        nextHue = (nextHue + 137.5f) % 360f   // ângulo áureo: cores bem distintas
    }

    private fun fingerUp(id: Int, x: Float, y: Float): Unit = synchronized(lock) {
        val slot = slotOf(id)
        if (slot < 0) return
        fingerId[slot] = -1
        if (burstCount < MAX_BURSTS) {
            burstX[burstCount] = x
            burstY[burstCount] = y
            burstHue[burstCount] = heldHue(slot)
            burstStrength[burstCount] = 1f
            burstCount++
        }
    }

    private companion object {
        const val MAX_FINGERS = 16
        const val MAX_BURSTS = 16
        const val HUE_BINS = 12
        const val GLOW_LEVELS = 4
        const val TRAIL = 2.5f
        const val HUE_SPIN = 30f        // graus por segundo com o dedo na tela
        const val PULSE_PERIOD = 3f     // segundos entre pulsos de um dedo segurado
        const val TRAIL_LEN = 40        // pontos da fita atrás do dedo
        const val MAX_SPARKS = 1200
    }
}
