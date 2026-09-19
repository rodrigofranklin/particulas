package br.com.rfranklin.particulas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modo Fluido: simulação de fluido incompressível ("stable fluids" de Jos Stam)
 * numa grade grossa, com tinta RGB injetada pelos dedos. A grade é desenhada
 * como um bitmap ampliado com filtro bilinear, o que dá o aspecto líquido.
 *
 * Unidades: velocidade em células/segundo; cada célula tem [cellPx] pixels.
 */
class FluidView(context: Context) : SimView(context) {

    private var n = 0           // colunas
    private var m = 0           // linhas
    private var cellPx = 1f
    private lateinit var u: FloatArray
    private lateinit var v: FloatArray
    private lateinit var u0: FloatArray
    private lateinit var v0: FloatArray
    private lateinit var r: FloatArray
    private lateinit var g: FloatArray
    private lateinit var b: FloatArray
    private lateinit var r0: FloatArray
    private lateinit var g0: FloatArray
    private lateinit var b0: FloatArray
    private lateinit var p: FloatArray
    private lateinit var div: FloatArray
    private lateinit var curl: FloatArray
    private lateinit var pixels: IntArray
    private var bitmap: Bitmap? = null
    private val dst = RectF()
    private val bmpPaint = Paint().apply { isFilterBitmap = true }
    private val hsv = FloatArray(3)

    // por dedo
    private val fLastSeq = IntArray(MAX_FINGERS) { -1 }
    private val fPrevX = FloatArray(MAX_FINGERS)
    private val fPrevY = FloatArray(MAX_FINGERS)
    private val fSpin = FloatArray(MAX_FINGERS)
    private var idleTime = 0f
    private var nextAmbient = 0f

    override fun onSizeReady(first: Boolean) {
        // ~3,6 dp por célula, limitado a ~40 mil células
        var cs = 3.6f * dp
        while ((w / cs) * (h / cs) > 40000f) cs *= 1.1f
        cellPx = cs
        n = max(8, (w / cs).toInt())
        m = max(8, (h / cs).toInt())
        val size = n * m
        u = FloatArray(size); v = FloatArray(size); u0 = FloatArray(size); v0 = FloatArray(size)
        r = FloatArray(size); g = FloatArray(size); b = FloatArray(size)
        r0 = FloatArray(size); g0 = FloatArray(size); b0 = FloatArray(size)
        p = FloatArray(size); div = FloatArray(size); curl = FloatArray(size)
        pixels = IntArray(size)
        bitmap = Bitmap.createBitmap(n, m, Bitmap.Config.ARGB_8888)
        dst.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    // ================= fontes =================

    /** Splat gaussiano de velocidade e/ou tinta centrado em (cx, cy) células. */
    private fun splat(cx: Float, cy: Float, radius: Float, fu: Float, fv: Float, cr: Float, cg: Float, cb: Float, dye: Float) {
        val rad = radius.toInt() + 1
        val i0 = max(1, (cx - rad).toInt()); val i1 = min(n - 2, (cx + rad).toInt())
        val j0 = max(1, (cy - rad).toInt()); val j1 = min(m - 2, (cy + rad).toInt())
        val inv = 1f / (radius * radius)
        for (j in j0..j1) {
            val dy = j - cy
            for (i in i0..i1) {
                val dx = i - cx
                val k = exp(-(dx * dx + dy * dy) * inv * 2f)
                if (k < 0.01f) continue
                val idx = i + j * n
                u[idx] += fu * k
                v[idx] += fv * k
                if (dye > 0f) {
                    r[idx] += cr * dye * k
                    g[idx] += cg * dye * k
                    b[idx] += cb * dye * k
                }
            }
        }
    }

    private fun hueRgb(hue: Float, sat: Float): FloatArray {
        hsv[0] = ((hue % 360f) + 360f) % 360f; hsv[1] = sat; hsv[2] = 1f
        val c = Color.HSVToColor(hsv)
        rgbTmp[0] = Color.red(c) / 255f; rgbTmp[1] = Color.green(c) / 255f; rgbTmp[2] = Color.blue(c) / 255f
        return rgbTmp
    }
    private val rgbTmp = FloatArray(3)

    private fun addSources(dt: Float) {
        val fs = dt * 60f       // escala "por frame a 60 Hz": injeções ficam por segundo
        var any = false
        for (f in 0 until MAX_FINGERS) {
            if (!fActive[f]) { fLastSeq[f] = -1; continue }
            any = true
            val cx = fX[f] / cellPx
            val cy = fY[f] / cellPx
            if (fSeq[f] != fLastSeq[f]) {
                fLastSeq[f] = fSeq[f]
                fPrevX[f] = cx; fPrevY[f] = cy
                fSpin[f] = if (rnd.nextBoolean()) 1f else -1f
                // toque novo: pulso radial para fora
                pulse(cx, cy, 6f, 45f)
            }
            val dx = cx - fPrevX[f]
            val dy = cy - fPrevY[f]
            fPrevX[f] = cx; fPrevY[f] = cy
            val speed = sqrt(dx * dx + dy * dy)          // células neste frame
            val col = hueRgb(fHue[f], 0.95f)

            if (speed > 0.15f) {
                // arrastando: empurra o fluido na direção do dedo e deixa tinta pelo caminho
                val k = min(speed, 4f) / speed
                splat(cx, cy, 4.5f, dx * k * 60f, dy * k * 60f, col[0], col[1], col[2], 2.2f * fs)
            } else {
                // parado: redemoinho girando com pulsos, tinta em espiral
                val age = fAge[f]
                val spin = fSpin[f] * (0.8f + 0.5f * sin(age * 1.1f))
                swirl(cx, cy, 9f, 7f * spin * fs)
                val ang = age * 5f * fSpin[f]
                splat(cx + cos(ang) * 3.5f, cy + sin(ang) * 3.5f, 3f, cos(ang) * 10f * fs, sin(ang) * 10f * fs,
                    col[0], col[1], col[2], 1.6f * fs)
                if (((age / 2.5f).toInt() and 1) == 1 && (age % 2.5f) < dt * 1.5f) pulse(cx, cy, 8f, 45f)
            }
        }

        // sem dedos: de vez em quando um respiro de cor, para não ficar parado
        if (any) { idleTime = 0f } else {
            idleTime += dt
            if (idleTime > 1.2f && time > nextAmbient) {
                nextAmbient = time + 0.8f + rnd.nextFloat() * 1.2f
                val cx = 4f + rnd.nextFloat() * (n - 8f)
                val cy = 4f + rnd.nextFloat() * (m - 8f)
                val ang = rnd.nextFloat() * 6.2832f
                val col = hueRgb(rnd.nextFloat() * 360f, 0.95f)
                splat(cx, cy, 6f, cos(ang) * 45f, sin(ang) * 45f, col[0], col[1], col[2], 5f)
                swirl(cx, cy, 10f, if (rnd.nextBoolean()) 14f else -14f)
            }
        }
    }

    /** Força tangencial (redemoinho) em torno de um ponto. */
    private fun swirl(cx: Float, cy: Float, radius: Float, strength: Float) {
        val rad = radius.toInt() + 1
        val i0 = max(1, (cx - rad).toInt()); val i1 = min(n - 2, (cx + rad).toInt())
        val j0 = max(1, (cy - rad).toInt()); val j1 = min(m - 2, (cy + rad).toInt())
        for (j in j0..j1) {
            val dy = j - cy
            for (i in i0..i1) {
                val dx = i - cx
                val d = sqrt(dx * dx + dy * dy) + 0.5f
                if (d > radius) continue
                val k = (1f - d / radius) * strength / d
                val idx = i + j * n
                u[idx] += -dy * k
                v[idx] += dx * k
            }
        }
    }

    /** Força radial para fora (choque). */
    private fun pulse(cx: Float, cy: Float, radius: Float, strength: Float) {
        val rad = radius.toInt() + 1
        val i0 = max(1, (cx - rad).toInt()); val i1 = min(n - 2, (cx + rad).toInt())
        val j0 = max(1, (cy - rad).toInt()); val j1 = min(m - 2, (cy + rad).toInt())
        for (j in j0..j1) {
            val dy = j - cy
            for (i in i0..i1) {
                val dx = i - cx
                val d = sqrt(dx * dx + dy * dy) + 0.5f
                if (d > radius) continue
                val k = (1f - d / radius) * strength / d
                val idx = i + j * n
                u[idx] += dx * k
                v[idx] += dy * k
            }
        }
    }

    // ================= solver =================

    override fun step(dt: Float) {
        addSources(dt)
        vorticity(dt)
        project()
        advectVelocity(dt)
        project()
        advectDye(dt)
        dissipate(dt)
    }

    /** Confinamento de vorticidade: realça os redemoinhos que a grade grossa dissiparia. */
    private fun vorticity(dt: Float) {
        for (j in 1 until m - 1) {
            for (i in 1 until n - 1) {
                val idx = i + j * n
                curl[idx] = (v[idx + 1] - v[idx - 1] - u[idx + n] + u[idx - n]) * 0.5f
            }
        }
        val eps = 3f * dt
        for (j in 2 until m - 2) {
            for (i in 2 until n - 2) {
                val idx = i + j * n
                var gx = abs(curl[idx + 1]) - abs(curl[idx - 1])
                var gy = abs(curl[idx + n]) - abs(curl[idx - n])
                val len = sqrt(gx * gx + gy * gy) + 1e-5f
                gx /= len; gy /= len
                val c = curl[idx]
                u[idx] += gy * c * eps
                v[idx] -= gx * c * eps
            }
        }
    }

    private fun project() {
        for (j in 1 until m - 1) {
            for (i in 1 until n - 1) {
                val idx = i + j * n
                div[idx] = -0.5f * (u[idx + 1] - u[idx - 1] + v[idx + n] - v[idx - n])
                p[idx] = 0f
            }
        }
        // Gauss-Seidel
        for (k in 0 until 18) {
            for (j in 1 until m - 1) {
                val row = j * n
                for (i in 1 until n - 1) {
                    val idx = i + row
                    p[idx] = (div[idx] + p[idx - 1] + p[idx + 1] + p[idx - n] + p[idx + n]) * 0.25f
                }
            }
            boundaryScalar(p)
        }
        for (j in 1 until m - 1) {
            for (i in 1 until n - 1) {
                val idx = i + j * n
                u[idx] -= 0.5f * (p[idx + 1] - p[idx - 1])
                v[idx] -= 0.5f * (p[idx + n] - p[idx - n])
            }
        }
        boundaryVelocity()
    }

    private fun boundaryScalar(a: FloatArray) {
        for (i in 0 until n) { a[i] = a[i + n]; a[i + (m - 1) * n] = a[i + (m - 2) * n] }
        for (j in 0 until m) { a[j * n] = a[1 + j * n]; a[n - 1 + j * n] = a[n - 2 + j * n] }
    }

    /** Paredes: velocidade zero nas bordas. */
    private fun boundaryVelocity() {
        for (i in 0 until n) { u[i] = 0f; v[i] = 0f; u[i + (m - 1) * n] = 0f; v[i + (m - 1) * n] = 0f }
        for (j in 0 until m) { u[j * n] = 0f; v[j * n] = 0f; u[n - 1 + j * n] = 0f; v[n - 1 + j * n] = 0f }
    }

    private fun advectVelocity(dt: Float) {
        System.arraycopy(u, 0, u0, 0, u.size)
        System.arraycopy(v, 0, v0, 0, v.size)
        val maxX = n - 1.501f
        val maxY = m - 1.501f
        for (j in 1 until m - 1) {
            for (i in 1 until n - 1) {
                val idx = i + j * n
                var x = i - u0[idx] * dt
                var y = j - v0[idx] * dt
                if (x < 0.5f) x = 0.5f else if (x > maxX) x = maxX
                if (y < 0.5f) y = 0.5f else if (y > maxY) y = maxY
                val i0 = x.toInt(); val j0 = y.toInt()
                val s1 = x - i0; val s0 = 1f - s1
                val t1 = y - j0; val t0 = 1f - t1
                val a = i0 + j0 * n
                u[idx] = s0 * (t0 * u0[a] + t1 * u0[a + n]) + s1 * (t0 * u0[a + 1] + t1 * u0[a + n + 1])
                v[idx] = s0 * (t0 * v0[a] + t1 * v0[a + n]) + s1 * (t0 * v0[a + 1] + t1 * v0[a + n + 1])
            }
        }
        boundaryVelocity()
    }

    private fun advectDye(dt: Float) {
        System.arraycopy(r, 0, r0, 0, r.size)
        System.arraycopy(g, 0, g0, 0, g.size)
        System.arraycopy(b, 0, b0, 0, b.size)
        val maxX = n - 1.501f
        val maxY = m - 1.501f
        for (j in 1 until m - 1) {
            for (i in 1 until n - 1) {
                val idx = i + j * n
                var x = i - u[idx] * dt
                var y = j - v[idx] * dt
                if (x < 0.5f) x = 0.5f else if (x > maxX) x = maxX
                if (y < 0.5f) y = 0.5f else if (y > maxY) y = maxY
                val i0 = x.toInt(); val j0 = y.toInt()
                val s1 = x - i0; val s0 = 1f - s1
                val t1 = y - j0; val t0 = 1f - t1
                val a = i0 + j0 * n
                val w00 = s0 * t0; val w01 = s0 * t1; val w10 = s1 * t0; val w11 = s1 * t1
                r[idx] = w00 * r0[a] + w01 * r0[a + n] + w10 * r0[a + 1] + w11 * r0[a + n + 1]
                g[idx] = w00 * g0[a] + w01 * g0[a + n] + w10 * g0[a + 1] + w11 * g0[a + n + 1]
                b[idx] = w00 * b0[a] + w01 * b0[a + n] + w10 * b0[a + 1] + w11 * b0[a + n + 1]
            }
        }
    }

    private fun dissipate(dt: Float) {
        val kd = pow(0.992f, dt * 60f)      // tinta some em ~8 s
        val kv = pow(0.993f, dt * 60f)
        for (i in u.indices) {
            u[i] *= kv; v[i] *= kv
            r[i] *= kd; g[i] *= kd; b[i] *= kd
        }
    }

    // ================= desenho =================

    override fun render(c: Canvas) {
        val bmp = bitmap ?: return
        val px = pixels
        for (i in px.indices) {
            // tone mapping tipo HDR: cores saturam em branco onde a tinta acumula
            val rr = 1f - exp(-2.2f * r[i])
            val gg = 1f - exp(-2.2f * g[i])
            val bb = 1f - exp(-2.2f * b[i])
            px[i] = (0xFF shl 24) or ((rr * 255f).toInt() shl 16) or ((gg * 255f).toInt() shl 8) or (bb * 255f).toInt()
        }
        bmp.setPixels(px, 0, n, 0, 0, n, m)
        c.drawBitmap(bmp, null, dst, bmpPaint)
    }
}
