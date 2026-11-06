package com.example.floatapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

// زر دائري زجاجي برمز مرسوم (رجوع، كتابة، تطبيقات، إخفاء، إغلاق)
class GlassCircleView(context: Context, private val icon: Int) : View(context) {

    companion object {
        const val BACK = 0
        const val KEYBOARD = 1
        const val TABS = 2
        const val MINUS = 3
        const val CLOSE = 4
    }

    var active = false
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB8121218.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 4f * d

        glow.shader = RadialGradient(
            cx, cy, r + 4f * d,
            intArrayOf(0x40000000, 0x00000000), floatArrayOf(0.75f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r + 4f * d, glow)
        c.drawCircle(cx, cy, r, base)
        shine.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.55f, r * 1.25f,
            0x70FFFFFF, 0x04FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, shine)
        val accent = 0xFF4ADE80.toInt()
        rim.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            if (active) accent else 0xDDFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, rim)

        ink.color = if (active) accent else Color.WHITE
        dot.color = ink.color
        val a = r * 0.42f
        when (icon) {
            BACK -> {
                val p = Path()
                p.moveTo(cx + a * 0.45f, cy - a)
                p.lineTo(cx - a * 0.55f, cy)
                p.lineTo(cx + a * 0.45f, cy + a)
                c.drawPath(p, ink)
            }
            CLOSE -> {
                c.drawLine(cx - a * 0.8f, cy - a * 0.8f, cx + a * 0.8f, cy + a * 0.8f, ink)
                c.drawLine(cx + a * 0.8f, cy - a * 0.8f, cx - a * 0.8f, cy + a * 0.8f, ink)
            }
            MINUS -> c.drawLine(cx - a, cy, cx + a, cy, ink)
            TABS -> {
                rect.set(cx - a, cy - a * 0.35f, cx + a * 0.45f, cy + a)
                c.drawRoundRect(rect, 3f * d, 3f * d, ink)
                rect.set(cx - a * 0.45f, cy - a, cx + a, cy + a * 0.35f)
                c.drawRoundRect(rect, 3f * d, 3f * d, ink)
            }
            KEYBOARD -> {
                rect.set(cx - a * 1.15f, cy - a * 0.7f, cx + a * 1.15f, cy + a * 0.7f)
                c.drawRoundRect(rect, 3f * d, 3f * d, ink)
                for (i in -1..1) c.drawCircle(cx + i * a * 0.55f, cy - a * 0.2f, 1f * d, dot)
                c.drawLine(cx - a * 0.5f, cy + a * 0.3f, cx + a * 0.5f, cy + a * 0.3f, ink)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                animate().scaleX(0.86f).scaleY(0.86f).setStartDelay(0).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start()
        }
        return super.onTouchEvent(e)
    }
}

// لسان نصف دائري زجاجي يخرج من خلف النافذة عند الزاوية السفلية ويختبئ خلفها
class TabHandleView(context: Context) : View(context) {

    var reveal = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC101014.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * d
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val r = width / 2f - 2f * d
        val cx = width / 2f
        val cy = -(1f - reveal) * (r + 3f * d)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 0f, 180f, true, base)
        shine.shader = LinearGradient(0f, cy, 0f, cy + r, 0x66FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP)
        c.drawArc(rect, 0f, 180f, true, shine)
        rim.shader = LinearGradient(
            cx - r, cy, cx + r, cy + r,
            0xFFFFFFFF.toInt(), 0x44C7CBD1, Shader.TileMode.CLAMP
        )
        c.drawArc(rect, 0f, 180f, false, rim)

        val gy = cy + r * 0.5f
        val a = r * 0.2f
        val h = a * 0.9f
        val p = Path()
        p.moveTo(cx - a, gy - a)
        p.lineTo(cx + a, gy + a)
        p.moveTo(cx + a - h, gy + a)
        p.lineTo(cx + a, gy + a)
        p.lineTo(cx + a, gy + a - h)
        p.moveTo(cx - a + h, gy - a)
        p.lineTo(cx - a, gy - a)
        p.lineTo(cx - a, gy - a + h)
        c.drawPath(p, ink)
    }
}

class DockItem(val site: Int, val active: Boolean, val plus: Boolean)

// شريط زجاجي لشعارات التطبيقات المفتوحة (ضغطة = تنقل، ضغطة مطولة = إغلاق)
class DockView(context: Context) : View(context) {

    var items: List<DockItem> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var onTap: ((Int) -> Unit)? = null
    var onLong: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val cell = 40f * d
    private val gap = 8f * d
    private val pad = 8f * d

    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC101014.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = 0x55FFFFFF
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val plusInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    private val ui = Handler(Looper.getMainLooper())
    private var downIdx = -1
    private var longFired = false
    private val longRun = Runnable {
        if (downIdx >= 0) {
            longFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLong?.invoke(downIdx)
        }
    }

    fun contentWidth(n: Int): Int = (pad * 2 + n * cell + maxOf(0, n - 1) * gap).toInt()
    fun contentHeight(): Int = (cell + pad * 2).toInt()

    private fun indexAt(x: Float, y: Float): Int {
        if (y < 0f || y > height || x < pad) return -1
        val i = ((x - pad) / (cell + gap)).toInt()
        return if (i in items.indices) i else -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downIdx = indexAt(e.x, e.y)
                longFired = false
                if (downIdx >= 0) ui.postDelayed(longRun, 550)
            }
            MotionEvent.ACTION_UP -> {
                ui.removeCallbacks(longRun)
                val i = indexAt(e.x, e.y)
                if (!longFired && i >= 0 && i == downIdx) onTap?.invoke(i)
                downIdx = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(longRun)
                downIdx = -1
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        ui.removeCallbacks(longRun)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
        val rad = rect.height() / 2f
        c.drawRoundRect(rect, rad, rad, base)
        shine.shader = LinearGradient(0f, 0f, 0f, h, 0x55FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, shine)
        rim.shader = LinearGradient(0f, 0f, w, h, 0xCCFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, rim)

        for ((i, item) in items.withIndex()) {
            val cx = pad + cell / 2f + i * (cell + gap)
            val cy = h / 2f
            val r = cell / 2f - 3f * d
            bg.shader = LinearGradient(cx, cy - r, cx, cy + r, 0x44FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, bg)
            c.drawCircle(cx, cy, r, cellRim)
            if (item.plus) {
                val a = 6f * d
                c.drawLine(cx - a, cy, cx + a, cy, plusInk)
                c.drawLine(cx, cy - a, cx, cy + a, plusInk)
            } else {
                SiteGlyph.draw(c, item.site, cx, cy, r * 0.5f)
            }
            if (item.active) c.drawCircle(cx, cy, cell / 2f - 1f * d, accent)
        }
    }
}

// تأثير الغبار المتلاشي: الأيقونة تتفتت إلى جسيمات تتطاير وتتلاشى
class DustView(context: Context, bmp: Bitmap, private val margin: Int) : View(context) {

    private val g = 22
    private val x0 = FloatArray(g * g)
    private val y0 = FloatArray(g * g)
    private val dx = FloatArray(g * g)
    private val dy = FloatArray(g * g)
    private val delay = FloatArray(g * g)
    private val color = IntArray(g * g)
    private var count = 0
    private var half = 0f
    private var gravity = 0f
    private var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val d = resources.displayMetrics.density
        val w = bmp.width
        val h = bmp.height
        half = w / g / 2f
        gravity = 40f * d
        val rnd = Random(System.nanoTime())
        for (iy in 0 until g) {
            for (ix in 0 until g) {
                val px = ((ix + 0.5f) * w / g).toInt().coerceIn(0, w - 1)
                val py = ((iy + 0.5f) * h / g).toInt().coerceIn(0, h - 1)
                val col = bmp.getPixel(px, py)
                if ((col ushr 24) > 24) {
                    x0[count] = margin + (ix + 0.5f) * w / g
                    y0[count] = margin + (iy + 0.5f) * h / g
                    dx[count] = (rnd.nextFloat() * 90f - 10f) * d
                    dy[count] = -(rnd.nextFloat() * 70f + 10f) * d
                    delay[count] = ix / g.toFloat() * 0.5f + rnd.nextFloat() * 0.08f
                    color[count] = col
                    count++
                }
            }
        }
    }

    fun start(onEnd: () -> Unit) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 1000
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd()
            }
        })
        anim.start()
    }

    override fun onDraw(c: Canvas) {
        for (i in 0 until count) {
            val t = ((progress - delay[i]) / 0.4f).coerceIn(0f, 1f)
            val x = x0[i] + dx[i] * t
            val y = y0[i] + dy[i] * t + gravity * t * t
            val a = ((1f - t) * 255f).toInt()
            paint.color = (color[i] and 0x00FFFFFF) or (a shl 24)
            if (t <= 0f) {
                c.drawRect(x - half, y - half, x + half, y + half, paint)
            } else {
                c.drawCircle(x, y, half * (1f - 0.55f * t), paint)
            }
        }
    }
}

// شعارات المواقع مرسومة بالكود بلون فضي زجاجي (بلا صور خارجية)
object SiteGlyph {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()

    private fun letter(c: Canvas, s: String, cx: Float, cy: Float, size: Float) {
        text.textSize = size
        val y = cy - (text.descent() + text.ascent()) / 2f
        c.drawText(s, cx, y, text)
    }

    fun draw(c: Canvas, site: Int, cx: Float, cy: Float, r: Float) {
        val sh = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            0xFFFFFFFF.toInt(), 0xFFB4B8C0.toInt(), Shader.TileMode.CLAMP
        )
        line.shader = sh
        solid.shader = sh
        text.shader = sh
        line.strokeWidth = maxOf(r * 0.17f, 1.3f)
        when (site) {
            0 -> {
                rect.set(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f)
                c.drawRoundRect(rect, r * 0.5f, r * 0.5f, line)
                c.drawCircle(cx, cy, r * 0.38f, line)
                c.drawCircle(cx + r * 0.5f, cy - r * 0.5f, r * 0.09f, solid)
            }
            1 -> letter(c, "f", cx, cy, r * 2.3f)
            2 -> {
                rect.set(cx - r * 0.95f, cy - r * 0.68f, cx + r * 0.95f, cy + r * 0.68f)
                c.drawRoundRect(rect, r * 0.38f, r * 0.38f, line)
                path.reset()
                path.moveTo(cx - r * 0.2f, cy - r * 0.32f)
                path.lineTo(cx + r * 0.38f, cy)
                path.lineTo(cx - r * 0.2f, cy + r * 0.32f)
                path.close()
                c.drawPath(path, solid)
            }
            3 -> {
                c.drawCircle(cx - r * 0.22f, cy + r * 0.48f, r * 0.32f, line)
                c.drawLine(cx + r * 0.1f, cy + r * 0.48f, cx + r * 0.1f, cy - r * 0.8f, line)
                path.reset()
                path.moveTo(cx + r * 0.1f, cy - r * 0.8f)
                path.cubicTo(cx + r * 0.1f, cy - r * 0.45f, cx + r * 0.4f, cy - r * 0.3f, cx + r * 0.8f, cy - r * 0.3f)
                c.drawPath(path, line)
            }
            4 -> {
                c.drawLine(cx - r * 0.75f, cy - r * 0.85f, cx + r * 0.75f, cy + r * 0.85f, line)
                val w = line.strokeWidth
                line.strokeWidth = w * 0.55f
                c.drawLine(cx + r * 0.75f, cy - r * 0.85f, cx - r * 0.75f, cy + r * 0.85f, line)
                line.strokeWidth = w
            }
            5 -> {
                path.reset()
                path.moveTo(cx - r * 0.95f, cy - r * 0.05f)
                path.lineTo(cx + r * 0.95f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.35f, cy + r * 0.85f)
                path.lineTo(cx - r * 0.1f, cy + r * 0.12f)
                path.close()
                c.drawPath(path, line)
                c.drawLine(cx - r * 0.1f, cy + r * 0.12f, cx + r * 0.95f, cy - r * 0.8f, line)
            }
            6 -> letter(c, "G", cx, cy, r * 2.1f)
        }
    }
}

// مربع زجاجي فضي بداخله شعار الموقع
class SiteGlassDrawable(private val site: Int) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0141519.toInt() }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        if (w <= 0f) return
        val inset = w * 0.04f
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val rad = rect.width() * 0.28f
        c.drawRoundRect(rect, rad, rad, base)
        sheen.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0x66FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, rad, rad, sheen)
        rim.strokeWidth = maxOf(1.2f, w * 0.02f)
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0xEEFFFFFF.toInt(), 0x33C7CBD1, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, rad, rad, rim)
        SiteGlyph.draw(c, site, rect.centerX(), rect.centerY(), rect.width() * 0.28f)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
