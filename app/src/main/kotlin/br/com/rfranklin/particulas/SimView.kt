package br.com.rfranklin.particulas

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

/**
 * Base dos três modos: SurfaceView com thread própria de física + desenho e
 * rastreamento de todos os dedos na tela.
 *
 * A UI thread escreve os dedos em `finger*` (sob [lock]); a cada frame a thread
 * de simulação copia para `f*`, que as subclasses leem livremente em [step] e
 * [render]. Cada dedo tem uma cor própria que gira devagar enquanto ele fica
 * na tela ([heldHue]).
 */
abstract class SimView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    protected val dp: Float = resources.displayMetrics.density
    protected val rnd = Random(System.nanoTime())

    // ---------- dedos, lado da UI thread (sempre sob lock) ----------
    protected val lock = Any()
    private val fingerId = IntArray(MAX_FINGERS) { -1 }
    private val fingerX = FloatArray(MAX_FINGERS)
    private val fingerY = FloatArray(MAX_FINGERS)
    private val fingerHue = FloatArray(MAX_FINGERS)
    private val fingerAge = FloatArray(MAX_FINGERS)
    private val fingerSeq = IntArray(MAX_FINGERS)
    private var seqCounter = 0
    private var nextHue = Random.nextFloat() * 360f

    // ---------- cópia para a thread de simulação ----------
    protected val fActive = BooleanArray(MAX_FINGERS)
    protected val fX = FloatArray(MAX_FINGERS)
    protected val fY = FloatArray(MAX_FINGERS)
    protected val fHue = FloatArray(MAX_FINGERS)
    protected val fAge = FloatArray(MAX_FINGERS)
    protected val fSeq = IntArray(MAX_FINGERS)      // muda quando um dedo novo ocupa o slot
    protected var anyFinger = false

    // ---------- tamanho ----------
    @Volatile private var pendingW = 0
    @Volatile private var pendingH = 0
    protected var w = 0
    protected var h = 0
    protected var time = 0f

    private var thread: Thread? = null
    @Volatile private var running = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ================= contrato das subclasses =================

    /** Chamado na thread de simulação sempre que o tamanho muda (`first` na primeira vez). */
    protected abstract fun onSizeReady(first: Boolean)
    protected abstract fun step(dt: Float)
    protected abstract fun render(c: Canvas)

    /** Dedo encostou (UI thread, sob lock). */
    protected open fun onFingerPressed(slot: Int, x: Float, y: Float) {}

    /** Dedo saiu (UI thread, sob lock). `hue` é a cor atual do dedo. */
    protected open fun onFingerReleased(slot: Int, x: Float, y: Float, hue: Float) {}

    /** Chamado sob lock logo após copiar os dedos; para copiar filas extras. */
    protected open fun snapshotExtra() {}

    /** Cor do dedo: a inicial girando devagar enquanto ele fica na tela (arco-íris). */
    protected fun heldHue(slot: Int): Float = (fingerHue[slot] + fingerAge[slot] * HUE_SPIN) % 360f

    // ================= ciclo de vida da surface =================

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "sim").also { it.start() }
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

    private fun loop() {
        var last = System.nanoTime()
        var sized = false
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
            last = now

            val nw = pendingW
            val nh = pendingH
            if (nw != w || nh != h) {
                w = nw
                h = nh
                if (w > 0 && h > 0) {
                    onSizeReady(!sized)
                    sized = true
                }
            }
            if (w == 0 || h == 0) {
                Thread.sleep(8)
                continue
            }

            snapshotInput(dt)
            time += dt
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
            anyFinger = false
            for (i in 0 until MAX_FINGERS) {
                fActive[i] = fingerId[i] >= 0
                if (fActive[i]) {
                    anyFinger = true
                    fingerAge[i] += dt
                    fX[i] = fingerX[i]
                    fY[i] = fingerY[i]
                    fHue[i] = heldHue(i)
                    fAge[i] = fingerAge[i]
                    fSeq[i] = fingerSeq[i]
                }
            }
            snapshotExtra()
        }
    }

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
        onFingerPressed(slot, x, y)
    }

    private fun fingerUp(id: Int, x: Float, y: Float): Unit = synchronized(lock) {
        val slot = slotOf(id)
        if (slot < 0) return
        val hue = heldHue(slot)
        fingerId[slot] = -1
        onFingerReleased(slot, x, y, hue)
    }

    // ================= utilidades =================

    protected fun lerpHue(from: Float, to: Float, k: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return from + d * k
    }

    protected fun pow(base: Float, e: Float): Float = Math.pow(base.toDouble(), e.toDouble()).toFloat()

    protected fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    companion object {
        const val MAX_FINGERS = 16
        const val HUE_SPIN = 30f        // graus por segundo com o dedo na tela
    }
}
