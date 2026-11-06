package com.example.floatapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

// بطاقة زجاجية سوداء بحد فضي رفيع
class GlassPanelDrawable(private val radius: Float) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val rect = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        rect.set(b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f)
        fill.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom, 0x30FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, fill)
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0x99FFFFFF.toInt(), 0x1AFFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, rim)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// خلفية سوداء بخطوط فضية منحنية وتوهج خافت
class BackgroundView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val bg = Paint()
    private val glow = Paint()
    private val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        bg.shader = LinearGradient(
            0f, 0f, 0f, h, 0xFF040405.toInt(), 0xFF0D0E11.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, bg)
        glow.shader = RadialGradient(
            w * 0.85f, h * 0.06f, w * 0.9f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, glow)

        // سطح زجاجي بين منحنيين
        path.reset()
        path.moveTo(-0.1f * w, 0.22f * h)
        path.cubicTo(0.3f * w, 0.05f * h, 0.65f * w, 0.40f * h, 1.1f * w, 0.18f * h)
        path.lineTo(1.1f * w, 0.26f * h)
        path.cubicTo(0.65f * w, 0.52f * h, 0.3f * w, 0.12f * h, -0.1f * w, 0.30f * h)
        path.close()
        glass.shader = LinearGradient(0f, 0f, w, 0f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawPath(path, glass)

        wave(c, w, h, 0.22f, 0.05f, 0.40f, 0.18f, 1.2f, 0x80)
        wave(c, w, h, 0.30f, 0.12f, 0.52f, 0.26f, 2f, 0x55)
        wave(c, w, h, 0.78f, 0.62f, 0.95f, 0.70f, 1.2f, 0x66)
        wave(c, w, h, 0.88f, 0.75f, 1.05f, 0.82f, 2f, 0x40)
    }

    private fun wave(
        c: Canvas, w: Float, h: Float,
        y0: Float, c1: Float, c2: Float, y1: Float, width: Float, alpha: Int
    ) {
        path.reset()
        path.moveTo(-0.1f * w, y0 * h)
        path.cubicTo(0.3f * w, c1 * h, 0.65f * w, c2 * h, 1.1f * w, y1 * h)
        curve.strokeWidth = width * d
        curve.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0x00FFFFFF, (alpha shl 24) or 0xFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(path, curve)
    }
}

// شعار التطبيق: مربع زجاجي بشبكة 2×2 وحلقة مدارية حوله
class LogoView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0121317.toInt() }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = 0x88FFFFFF.toInt()
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val ringRect = RectF()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.5f

        glow.shader = RadialGradient(
            cx, cy, s * 1.2f, intArrayOf(0x33FFFFFF, 0x00FFFFFF), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, s * 1.2f, glow)

        val rw = s * 1.18f
        val rh = s * 0.46f
        ringRect.set(-rw, -rh, rw, rh)
        ring.strokeWidth = 2.2f * d
        ring.shader = LinearGradient(
            -rw, 0f, rw, 0f, 0xFFFFFFFF.toInt(), 0x66AEB3BB, Shader.TileMode.CLAMP
        )

        c.save()
        c.translate(cx, cy + s * 0.05f)
        c.rotate(-24f)
        ring.alpha = 90
        c.drawArc(ringRect, 180f, 180f, false, ring)
        c.restore()

        c.save()
        c.translate(cx, cy)
        c.rotate(-8f)
        rect.set(-s / 2f, -s / 2f, s / 2f, s / 2f)
        val rad = s * 0.26f
        c.drawRoundRect(rect, rad, rad, base)
        sheen.shader = LinearGradient(
            -s / 2f, -s / 2f, s / 2f, s / 2f, 0x77FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, rad, rad, sheen)
        rim.strokeWidth = 1.6f * d
        rim.shader = LinearGradient(
            -s / 2f, -s / 2f, s / 2f, s / 2f, 0xFFFFFFFF.toInt(), 0x44C7CBD1, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, rad, rad, rim)

        val cs = s * 0.30f
        val g = s * 0.07f
        cell.shader = LinearGradient(0f, -cs, 0f, cs, 0x77FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP)
        for (iy in 0..1) {
            for (ix in 0..1) {
                val l = -g / 2f - cs + ix * (cs + g)
                val t = -g / 2f - cs + iy * (cs + g)
                rect.set(l, t, l + cs, t + cs)
                c.drawRoundRect(rect, cs * 0.28f, cs * 0.28f, cell)
                c.drawRoundRect(rect, cs * 0.28f, cs * 0.28f, cellRim)
            }
        }
        c.restore()

        c.save()
        c.translate(cx, cy + s * 0.05f)
        c.rotate(-24f)
        ring.alpha = 255
        c.drawArc(ringRect, 0f, 180f, false, ring)
        c.restore()
    }
}

// أيقونة خطية داخل مربع زجاجي (مثل إعدادات آيفون)
class GlassIconView(context: Context, private val kind: Int, private val boxed: Boolean = true) : View(context) {

    companion object {
        const val SHIELD = 0
        const val BOLT = 1
        const val GLOBE = 2
        const val EXPAND = 3
        const val WINDOWS = 4
        const val INFO = 5
    }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F3F5.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F3F5.toInt() }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        if (boxed) {
            rect.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
            val rad = w * 0.3f
            fill.shader = LinearGradient(0f, 0f, w, h, 0x55FFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, rad, rad, fill)
            rim.shader = LinearGradient(0f, 0f, w, h, 0xCCFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, rad, rad, rim)
        }
        val a = if (boxed) w * 0.26f else w * 0.34f
        path.reset()
        when (kind) {
            SHIELD -> {
                path.moveTo(cx, cy - a)
                path.lineTo(cx + a * 0.85f, cy - a * 0.55f)
                path.lineTo(cx + a * 0.85f, cy + a * 0.1f)
                path.quadTo(cx + a * 0.85f, cy + a * 0.7f, cx, cy + a)
                path.quadTo(cx - a * 0.85f, cy + a * 0.7f, cx - a * 0.85f, cy + a * 0.1f)
                path.lineTo(cx - a * 0.85f, cy - a * 0.55f)
                path.close()
                path.moveTo(cx - a * 0.35f, cy + a * 0.05f)
                path.lineTo(cx - a * 0.08f, cy + a * 0.33f)
                path.lineTo(cx + a * 0.4f, cy - a * 0.2f)
                c.drawPath(path, ink)
            }
            BOLT -> {
                path.moveTo(cx + a * 0.2f, cy - a)
                path.lineTo(cx - a * 0.5f, cy + a * 0.1f)
                path.lineTo(cx - a * 0.05f, cy + a * 0.1f)
                path.lineTo(cx - a * 0.2f, cy + a)
                path.lineTo(cx + a * 0.5f, cy - a * 0.15f)
                path.lineTo(cx + a * 0.05f, cy - a * 0.15f)
                path.close()
                c.drawPath(path, ink)
            }
            GLOBE -> {
                c.drawCircle(cx, cy, a, ink)
                rect.set(cx - a * 0.45f, cy - a, cx + a * 0.45f, cy + a)
                c.drawOval(rect, ink)
                c.drawLine(cx - a, cy, cx + a, cy, ink)
                c.drawLine(cx - a * 0.87f, cy - a * 0.5f, cx + a * 0.87f, cy - a * 0.5f, ink)
                c.drawLine(cx - a * 0.87f, cy + a * 0.5f, cx + a * 0.87f, cy + a * 0.5f, ink)
            }
            EXPAND -> {
                val e = a * 0.95f
                val l = a * 0.5f
                path.moveTo(cx - e, cy - e + l); path.lineTo(cx - e, cy - e); path.lineTo(cx - e + l, cy - e)
                path.moveTo(cx + e - l, cy - e); path.lineTo(cx + e, cy - e); path.lineTo(cx + e, cy - e + l)
                path.moveTo(cx + e, cy + e - l); path.lineTo(cx + e, cy + e); path.lineTo(cx + e - l, cy + e)
                path.moveTo(cx - e + l, cy + e); path.lineTo(cx - e, cy + e); path.lineTo(cx - e, cy + e - l)
                c.drawPath(path, ink)
            }
            WINDOWS -> {
                rect.set(cx - a, cy - a * 0.35f, cx + a * 0.45f, cy + a)
                c.drawRoundRect(rect, 3f * d, 3f * d, ink)
                rect.set(cx - a * 0.45f, cy - a, cx + a, cy + a * 0.35f)
                c.drawRoundRect(rect, 3f * d, 3f * d, ink)
            }
            INFO -> {
                c.drawCircle(cx, cy, a, ink)
                c.drawCircle(cx, cy - a * 0.45f, 1.2f * d, dot)
                c.drawLine(cx, cy - a * 0.1f, cx, cy + a * 0.55f, ink)
            }
        }
    }
}

// مفتاح تشغيل زجاجي فضي بحركة ناعمة
class GlassSwitch(context: Context) : View(context) {

    var onChange: ((Boolean) -> Unit)? = null
    var checked = false
        private set

    private val d = resources.displayMetrics.density
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        color = 0x66FFFFFF
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension((54f * d).toInt(), (32f * d).toInt())
    }

    fun setChecked(v: Boolean, animate: Boolean) {
        checked = v
        anim?.cancel()
        if (!animate) {
            pos = if (v) 1f else 0f
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(pos, if (v) 1f else 0f)
        a.duration = 240
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                animate().scaleX(0.94f).scaleY(0.94f).setStartDelay(0).setDuration(80).start()
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start()
                setChecked(!checked, true)
                onChange?.invoke(checked)
            }
            MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(1f * d, 1f * d, w - 1f * d, h - 1f * d)
        val rad = rect.height() / 2f
        off.shader = LinearGradient(0f, 0f, 0f, h, 0xFF4A4C53.toInt(), 0xFF2A2C31.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, off)
        on.shader = LinearGradient(0f, 0f, 0f, h, 0xFFF4F5F7.toInt(), 0xFFA9ACB3.toInt(), Shader.TileMode.CLAMP)
        on.alpha = (pos * 255f).toInt()
        c.drawRoundRect(rect, rad, rad, on)
        c.drawRoundRect(rect, rad, rad, rim)
        val tr = h / 2f - 3.5f * d
        val tx = h / 2f + (w - h) * pos
        val ty = h / 2f
        c.drawCircle(tx, ty + 1.5f * d, tr + 1f * d, shadow)
        c.drawCircle(tx, ty, tr, thumb)
    }
}
