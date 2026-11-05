package com.example.floatapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun FloatingService.circleParams(size: Int) = WindowManager.LayoutParams(
    size, size,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
).apply { gravity = Gravity.TOP or Gravity.START }

internal fun FloatingService.ensureCircles() {
    if (handleView != null) return
    handleParams = circleParams(dp(34))
    handleView = GlassCircleView(this, "").also { attachHandleTouch(it) }

    val defs: List<Pair<String, () -> Unit>> = listOf(
        "◀" to { goBack() },
        "⌨" to { toggleKeyboard(); scheduleHide() },
        "—" to { hideWindow() },
        "✕" to { closeWindow() }
    )
    for ((glyph, action) in defs) {
        val v = GlassCircleView(this, glyph)
        v.setOnClickListener { action() }
        if (glyph == "⌨") kbAction = v
        actionViews.add(v)
        actionParams.add(circleParams(dp(36)))
    }
}

internal fun FloatingService.scheduleHide() {
    ui.removeCallbacks(hideRunnable)
    ui.postDelayed(hideRunnable, 5000)
}

internal fun FloatingService.showHandle() {
    if (!attached) return
    ensureCircles()
    val hv = handleView ?: return
    val hp = handleParams ?: return
    layoutHandle()
    if (!handleAttached) {
        wm.addView(hv, hp)
        handleAttached = true
        hv.alpha = 0f
        hv.animate().alpha(1f).setDuration(180).start()
    }
    scheduleHide()
}

internal fun FloatingService.layoutHandle() {
    val wp = winParams ?: return
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    val hs = hp.width
    hp.x = (wp.x + wp.width - hs / 2 + dp(2)).coerceIn(0, dm.widthPixels - hs)
    hp.y = (wp.y + wp.height - hs / 2 + dp(2)).coerceIn(0, dm.heightPixels - hs)
    if (handleAttached) handleView?.let { wm.updateViewLayout(it, hp) }
    if (menuOpen) layoutMenu()
}

internal fun FloatingService.layoutMenu() {
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    val cx = hp.x + hp.width / 2f
    val cy = hp.y + hp.height / 2f
    val radius = dp(80).toFloat()
    for (i in actionViews.indices) {
        val ap = actionParams[i]
        val ang = Math.toRadians((180 + i * 30).toDouble())
        val bs = ap.width
        ap.x = (cx + radius * cos(ang) - bs / 2f).toInt().coerceIn(0, dm.widthPixels - bs)
        ap.y = (cy + radius * sin(ang) - bs / 2f).toInt().coerceIn(0, dm.heightPixels - bs)
        if (menuOpen) wm.updateViewLayout(actionViews[i], ap)
    }
}

internal fun FloatingService.openMenu() {
    if (menuOpen) return
    ensureCircles()
    layoutMenu()
    for (i in actionViews.indices) {
        val v = actionViews[i]
        wm.addView(v, actionParams[i])
        v.alpha = 0f
        v.scaleX = 0.3f
        v.scaleY = 0.3f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(i * 40L).setDuration(200).start()
    }
    menuOpen = true
    kbAction?.active = kbOn
    scheduleHide()
}

internal fun FloatingService.closeMenu() {
    if (!menuOpen) return
    for (v in actionViews) wm.removeView(v)
    menuOpen = false
}

internal fun FloatingService.hideCircles() {
    ui.removeCallbacks(hideRunnable)
    closeMenu()
    if (handleAttached) {
        handleView?.let { wm.removeView(it) }
        handleAttached = false
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun FloatingService.attachHandleTouch(h: View) {
    val slop = ViewConfiguration.get(this).scaledTouchSlop
    var rw = 0
    var rh = 0
    var tx = 0f
    var ty = 0f
    var moved = false
    var menuTriggered = false
    val menuRunnable = Runnable {
        menuTriggered = true
        h.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        openMenu()
    }
    h.setOnTouchListener { _, e ->
        val p = winParams
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (p != null) {
                    rw = p.width
                    rh = p.height
                }
                tx = e.rawX
                ty = e.rawY
                moved = false
                menuTriggered = false
                ui.removeCallbacks(hideRunnable)
                ui.postDelayed(menuRunnable, 450)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!menuTriggered && p != null) {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        ui.removeCallbacks(menuRunnable)
                    }
                    if (moved) {
                        val dm = resources.displayMetrics
                        p.width = (rw + dx).toInt().coerceIn(dp(150), dm.widthPixels)
                        p.height = (rh + dy).toInt().coerceIn(dp(200), dm.heightPixels)
                        windowView?.let { if (attached) wm.updateViewLayout(it, p) }
                        layoutHandle()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(menuRunnable)
                if (!moved && !menuTriggered && menuOpen) closeMenu()
                scheduleHide()
            }
        }
        true
    }
}

// دائرة زجاجية صغيرة: إما برمز نصي، أو بسهمي التكبير إذا كان الرمز فارغاً
class GlassCircleView(context: Context, private val glyph: String) : View(context) {

    var active = false
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3101018.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 2f * d

        c.drawCircle(cx, cy, r, base)
        shine.shader = RadialGradient(
            cx - r * 0.4f, cy - r * 0.5f, r * 1.3f,
            0x66FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, shine)
        ring.color = if (active) 0xFF4ADE80.toInt() else 0xAAFFFFFF.toInt()
        c.drawCircle(cx, cy, r, ring)

        if (glyph.isEmpty()) {
            val a = r * 0.38f
            val h = r * 0.34f
            val path = Path()
            path.moveTo(cx - a, cy - a)
            path.lineTo(cx + a, cy + a)
            path.moveTo(cx + a - h, cy + a)
            path.lineTo(cx + a, cy + a)
            path.lineTo(cx + a, cy + a - h)
            path.moveTo(cx - a + h, cy - a)
            path.lineTo(cx - a, cy - a)
            path.lineTo(cx - a, cy - a + h)
            c.drawPath(path, ink)
        } else {
            txt.textSize = r * 0.95f
            val y = cy - (txt.descent() + txt.ascent()) / 2f
            c.drawText(glyph, cx, y, txt)
        }
    }
}
