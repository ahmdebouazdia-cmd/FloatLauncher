package com.example.floatapp

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============ نوافذ صغيرة عائمة: القرط، الأزرار، شريط التطبيقات ============

internal fun FloatingService.circleParams(w: Int, h: Int) = WindowManager.LayoutParams(
    w, h,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
).apply { gravity = Gravity.TOP or Gravity.START }

internal fun FloatingService.ensureCircles() {
    if (handleView != null) return
    handleParams = circleParams(dp(44), dp(44))
    handleView = EarringView(this).also { attachHandleTouch(it) }

    val defs: List<Pair<Int, () -> Unit>> = listOf(
        GlassCircleView.BACK to { goBack() },
        GlassCircleView.KEYBOARD to { toggleKeyboard(); scheduleHide() },
        GlassCircleView.TABS to { closeMenu(); showDock(false) },
        GlassCircleView.MINUS to { hideWindow() },
        GlassCircleView.CLOSE to { closeWindow() }
    )
    for ((icon, action) in defs) {
        val v = GlassCircleView(this, icon)
        v.setOnClickListener { action() }
        if (icon == GlassCircleView.KEYBOARD) kbAction = v
        actionViews.add(v)
        actionParams.add(circleParams(dp(42), dp(42)))
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
        hv.scaleX = 0.5f
        hv.scaleY = 0.5f
        hv.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(260)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
    }
    scheduleHide()
}

// مسمار القرط على زاوية النافذة السفلية اليمنى تماماً
internal fun FloatingService.layoutHandle() {
    val wp = winParams ?: return
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    val hs = hp.width
    hp.x = (wp.x + wp.width - dp(9)).coerceIn(0, maxOf(0, dm.widthPixels - hs))
    hp.y = (wp.y + wp.height - dp(9)).coerceIn(0, maxOf(0, dm.heightPixels - hs))
    if (handleAttached) handleView?.let { wm.updateViewLayout(it, hp) }
    if (menuOpen) layoutMenu()
    if (dockAttached) layoutDock()
}

// الأزرار على قوس قريب ومائل حول القرط
internal fun FloatingService.layoutMenu() {
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    val cx = hp.x + dp(26).toFloat()
    val cy = hp.y + dp(26).toFloat()
    val radius = dp(56).toFloat()
    val n = actionViews.size
    for (i in 0 until n) {
        val ap = actionParams[i]
        val ang = Math.toRadians(225.0 + (i - (n - 1) / 2.0) * 40.0)
        val bs = ap.width
        ap.x = (cx + radius * cos(ang) - bs / 2f).toInt().coerceIn(0, maxOf(0, dm.widthPixels - bs))
        ap.y = (cy + radius * sin(ang) - bs / 2f).toInt().coerceIn(0, maxOf(0, dm.heightPixels - bs))
        if (menuOpen) wm.updateViewLayout(actionViews[i], ap)
    }
}

internal fun FloatingService.openMenu() {
    if (menuOpen) return
    ensureCircles()
    val hp = handleParams ?: return
    hideDock()
    layoutMenu()
    val cx = hp.x + dp(26)
    val cy = hp.y + dp(26)
    for (i in actionViews.indices) {
        val v = actionViews[i]
        val ap = actionParams[i]
        wm.addView(v, ap)
        v.alpha = 0f
        v.scaleX = 0.3f
        v.scaleY = 0.3f
        v.translationX = (cx - (ap.x + ap.width / 2)).toFloat()
        v.translationY = (cy - (ap.y + ap.height / 2)).toFloat()
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setStartDelay(i * 35L).setDuration(280)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
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

internal fun FloatingService.toggleMenu() {
    if (menuOpen) closeMenu() else openMenu()
}

internal fun FloatingService.hideCircles() {
    ui.removeCallbacks(hideRunnable)
    closeMenu()
    hideDock()
    if (handleAttached) {
        handleView?.let { wm.removeView(it) }
        handleAttached = false
    }
}

// ------------ شريط التطبيقات ------------

internal fun FloatingService.dockItems(): List<DockItem> {
    val pm = packageManager
    fun iconOf(pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }
    return if (!dockPicker) {
        val list = tabs.mapIndexed { i, t ->
            val s = FloatingService.SITES[t.site]
            DockItem(iconOf(s.third), s.first.take(1), i == current, false)
        }.toMutableList()
        if (tabs.size < FloatingService.MAX_TABS) list.add(DockItem(null, "+", false, true))
        list
    } else {
        FloatingService.SITES.map { DockItem(iconOf(it.third), it.first.take(1), false, false) }
    }
}

internal fun FloatingService.ensureDock() {
    if (dockView != null) return
    dockParams = circleParams(dp(60), dp(56))
    dockView = DockView(this).also { dv ->
        dv.onTap = { i -> onDockTap(i) }
        dv.onLong = { i -> onDockLong(i) }
    }
}

internal fun FloatingService.refreshDock() {
    val dv = dockView ?: return
    val dpar = dockParams ?: return
    val list = dockItems()
    dv.items = list
    dpar.width = dv.contentWidth(list.size)
    dpar.height = dv.contentHeight()
    if (dockAttached) wm.updateViewLayout(dv, dpar)
}

// فوق النافذة في الوسط، وإن لم يوجد مكان فتحتها
internal fun FloatingService.layoutDock() {
    val wp = winParams ?: return
    val dpar = dockParams ?: return
    val dm = resources.displayMetrics
    val x = wp.x + (wp.width - dpar.width) / 2
    var y = wp.y - dpar.height - dp(8)
    if (y < dp(4)) y = wp.y + wp.height + dp(8)
    dpar.x = x.coerceIn(0, maxOf(0, dm.widthPixels - dpar.width))
    dpar.y = y.coerceIn(0, maxOf(0, dm.heightPixels - dpar.height))
    if (dockAttached) dockView?.let { wm.updateViewLayout(it, dpar) }
}

internal fun FloatingService.showDock(picker: Boolean) {
    if (!attached) return
    ensureDock()
    dockPicker = picker
    refreshDock()
    layoutDock()
    val dv = dockView ?: return
    val dpar = dockParams ?: return
    if (!dockAttached) {
        wm.addView(dv, dpar)
        dockAttached = true
        dv.alpha = 0f
        dv.scaleX = 0.8f
        dv.scaleY = 0.8f
        dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(240)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
    }
    scheduleHide()
}

internal fun FloatingService.hideDock() {
    if (dockAttached) {
        dockView?.let { wm.removeView(it) }
        dockAttached = false
    }
}

internal fun FloatingService.onDockTap(i: Int) {
    if (!dockPicker) {
        if (i < tabs.size) {
            switchTo(i)
            refreshDock()
        } else {
            dockPicker = true
            refreshDock()
            layoutDock()
        }
    } else {
        addTab(i)
        dockPicker = false
        refreshDock()
        layoutDock()
    }
    scheduleHide()
}

internal fun FloatingService.onDockLong(i: Int) {
    if (!dockPicker && i < tabs.size) {
        closeTab(i)
        if (windowView != null && dockAttached) {
            refreshDock()
            layoutDock()
        }
    }
    if (windowView != null) scheduleHide()
}

// ------------ لمس القرط: ضغطة = الأزرار، سحب = تغيير الحجم (الزاوية السفلية ثابتة) ------------

@SuppressLint("ClickableViewAccessibility")
internal fun FloatingService.attachHandleTouch(h: View) {
    val slop = ViewConfiguration.get(this).scaledTouchSlop
    var rw = 0
    var rh = 0
    var anchorR = 0
    var anchorB = 0
    var tx = 0f
    var ty = 0f
    var moved = false
    h.setOnTouchListener { _, e ->
        val p = winParams
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (p != null) {
                    rw = p.width
                    rh = p.height
                    anchorR = p.x + p.width
                    anchorB = p.y + p.height
                }
                tx = e.rawX
                ty = e.rawY
                moved = false
                ui.removeCallbacks(hideRunnable)
                h.animate().scaleX(0.9f).scaleY(0.9f).setStartDelay(0).setDuration(90).start()
            }
            MotionEvent.ACTION_MOVE -> {
                if (p != null) {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        val dm = resources.displayMetrics
                        val maxW = maxOf(minOf(anchorR, dm.widthPixels), dp(150))
                        val maxH = maxOf(minOf(anchorB, dm.heightPixels), dp(200))
                        val nw = (rw + dx).toInt().coerceIn(dp(150), maxW)
                        val nh = (rh + dy).toInt().coerceIn(dp(200), maxH)
                        p.width = nw
                        p.height = nh
                        p.x = anchorR - nw
                        p.y = anchorB - nh
                        windowView?.let { if (attached) wm.updateViewLayout(it, p) }
                        layoutHandle()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                h.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start()
                if (e.actionMasked == MotionEvent.ACTION_UP && !moved) {
                    h.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    toggleMenu()
                }
                scheduleHide()
            }
        }
        true
    }
}

// ------------ لمس النافذة: لمس مطوّل ثم سحب = تحريك النافذة ------------

@SuppressLint("ClickableViewAccessibility")
internal fun FloatingService.attachWebTouch(web: WebView) {
    val slop = ViewConfiguration.get(this).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var lastX = 0f
    var lastY = 0f
    var sx = 0
    var sy = 0
    var dragging = false
    val dragRunnable = Runnable {
        val p = winParams ?: return@Runnable
        dragging = true
        sx = p.x
        sy = p.y
        downX = lastX
        downY = lastY
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        web.onTouchEvent(cancel)
        cancel.recycle()
        web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    web.setOnTouchListener { _, e ->
        lastX = e.rawX
        lastY = e.rawY
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                dragging = false
                showHandle()
                ui.postDelayed(dragRunnable, 450)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val p = winParams
                    val v = windowView
                    if (p != null && v != null) {
                        p.x = sx + (e.rawX - downX).toInt()
                        p.y = sy + (e.rawY - downY).toInt()
                        if (attached) wm.updateViewLayout(v, p)
                        layoutHandle()
                    }
                } else if (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop) {
                    ui.removeCallbacks(dragRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(dragRunnable)
                val was = dragging
                dragging = false
                if (was) {
                    scheduleHide()
                    return@setOnTouchListener true
                }
            }
        }
        dragging
    }
}
