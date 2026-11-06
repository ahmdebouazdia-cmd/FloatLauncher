package com.example.floatapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============ نوافذ صغيرة عائمة: اللسان، الأزرار، شريط التطبيقات ============

internal fun FloatingService.circleParams(w: Int, h: Int) = WindowManager.LayoutParams(
    w, h,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
).apply { gravity = Gravity.TOP or Gravity.START }

internal fun FloatingService.ensureCircles() {
    if (handleView != null) return
    handleParams = circleParams(dp(52), dp(32))
    handleView = TabHandleView(this).also { attachHandleTouch(it) }

    val defs: List<Pair<Int, () -> Unit>> = listOf(
        GlassCircleView.BACK to { goBack() },
        GlassCircleView.KEYBOARD to { toggleKeyboard(); scheduleHide() },
        GlassCircleView.TABS to { closeMenu(); showDock(false) },
        GlassCircleView.MINUS to { hideWindow() },
        GlassCircleView.CLOSE to { closeWindowAnimated() }
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

// ------------ اللسان نصف الدائري ------------

internal fun FloatingService.animateHandle(show: Boolean, onEnd: (() -> Unit)? = null) {
    val hv = handleView ?: return
    handleAnim?.cancel()
    val a = ValueAnimator.ofFloat(hv.reveal, if (show) 1f else 0f)
    a.duration = if (show) 280L else 200L
    a.interpolator = if (show) DecelerateInterpolator() else AccelerateInterpolator()
    a.addUpdateListener { hv.reveal = it.animatedValue as Float }
    a.addListener(object : AnimatorListenerAdapter() {
        var cancelled = false
        override fun onAnimationCancel(animation: Animator) {
            cancelled = true
        }

        override fun onAnimationEnd(animation: Animator) {
            if (!cancelled) onEnd?.invoke()
        }
    })
    handleAnim = a
    a.start()
}

internal fun FloatingService.showHandle() {
    if (!attached) return
    ensureCircles()
    val hv = handleView ?: return
    val hp = handleParams ?: return
    layoutHandle()
    if (!handleAttached) {
        hv.reveal = 0f
        wm.addView(hv, hp)
        handleAttached = true
    }
    if (!handleShown) {
        handleShown = true
        animateHandle(true)
    }
    scheduleHide()
}

// اللسان تحت الحافة السفلية للنافذة مباشرة، قرب الزاوية اليمنى
internal fun FloatingService.layoutHandle() {
    val wp = winParams ?: return
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    hp.x = (wp.x + wp.width - hp.width - dp(14)).coerceIn(0, maxOf(0, dm.widthPixels - hp.width))
    hp.y = (wp.y + wp.height).coerceIn(0, maxOf(0, dm.heightPixels - hp.height))
    if (handleAttached) handleView?.let { wm.updateViewLayout(it, hp) }
    if (actionsAttached) layoutMenu()
    if (dockAttached) layoutDock()
}

internal fun FloatingService.removeHandleNow() {
    handleAnim?.cancel()
    if (handleAttached) {
        handleView?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        handleAttached = false
    }
    handleShown = false
}

// ------------ الأزرار على قوس حول اللسان ------------

internal fun FloatingService.layoutMenu() {
    val hp = handleParams ?: return
    val dm = resources.displayMetrics
    val cx = hp.x + hp.width / 2f
    val cy = hp.y.toFloat()
    val radius = dp(58).toFloat()
    val n = actionViews.size
    // تفتح للأسفل إن وُجد مكان، وإلا للأعلى
    val below = hp.y + radius + dp(24) < dm.heightPixels - dp(8)
    val base = if (below) 90.0 else 270.0
    for (i in 0 until n) {
        val ap = actionParams[i]
        val ang = Math.toRadians(base + (i - (n - 1) / 2.0) * 40.0)
        val bs = ap.width
        ap.x = (cx + radius * cos(ang) - bs / 2f).toInt().coerceIn(0, maxOf(0, dm.widthPixels - bs))
        ap.y = (cy + radius * sin(ang) - bs / 2f).toInt().coerceIn(0, maxOf(0, dm.heightPixels - bs))
        if (actionsAttached) wm.updateViewLayout(actionViews[i], ap)
    }
}

internal fun FloatingService.openMenu() {
    if (menuOpen) return
    ensureCircles()
    val hp = handleParams ?: return
    hideDock(true)
    ui.removeCallbacks(menuRemoveRunnable)
    layoutMenu()
    val cx = hp.x + hp.width / 2
    val cy = hp.y
    val fresh = !actionsAttached
    for (i in actionViews.indices) {
        val v = actionViews[i]
        val ap = actionParams[i]
        if (fresh) {
            wm.addView(v, ap)
            v.alpha = 0f
            v.scaleX = 0.3f
            v.scaleY = 0.3f
            v.translationX = (cx - (ap.x + ap.width / 2)).toFloat()
            v.translationY = (cy - (ap.y + ap.height / 2)).toFloat()
        }
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setStartDelay(i * 35L).setDuration(300)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }
    actionsAttached = true
    menuOpen = true
    kbAction?.active = kbOn
    scheduleHide()
}

// إغلاق متحرك: الأزرار تنكمش وتعود إلى اللسان بالترتيب العكسي
internal fun FloatingService.closeMenu() {
    if (!menuOpen) return
    menuOpen = false
    val hp = handleParams
    if (hp == null || !actionsAttached) {
        removeActionsNow()
        return
    }
    val cx = hp.x + hp.width / 2
    val cy = hp.y
    val n = actionViews.size
    for (i in 0 until n) {
        val v = actionViews[i]
        val ap = actionParams[i]
        v.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f)
            .translationX((cx - (ap.x + ap.width / 2)).toFloat())
            .translationY((cy - (ap.y + ap.height / 2)).toFloat())
            .setStartDelay((n - 1 - i) * 25L).setDuration(200)
            .setInterpolator(AccelerateInterpolator()).start()
    }
    ui.postDelayed(menuRemoveRunnable, 200L + n * 25L + 40L)
}

internal fun FloatingService.removeActionsNow() {
    ui.removeCallbacks(menuRemoveRunnable)
    if (actionsAttached) {
        for (v in actionViews) {
            v.animate().cancel()
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
        actionsAttached = false
    }
    menuOpen = false
}

internal fun FloatingService.toggleMenu() {
    if (menuOpen) closeMenu() else openMenu()
}

// ------------ شريط التطبيقات ------------

internal fun FloatingService.dockItems(): List<DockItem> {
    return if (!dockPicker) {
        val list = tabs.mapIndexed { i, t -> DockItem(t.site, i == current, false) }.toMutableList()
        if (tabs.size < FloatingService.MAX_TABS) list.add(DockItem(0, false, true))
        list
    } else {
        FloatingService.SITES.indices.map { DockItem(it, false, false) }
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

internal fun FloatingService.layoutDock() {
    val wp = winParams ?: return
    val dpar = dockParams ?: return
    val dm = resources.displayMetrics
    val x = wp.x + (wp.width - dpar.width) / 2
    var y = wp.y - dpar.height - dp(8)
    if (y < dp(4)) y = wp.y + wp.height + dp(40)
    dpar.x = x.coerceIn(0, maxOf(0, dm.widthPixels - dpar.width))
    dpar.y = y.coerceIn(0, maxOf(0, dm.heightPixels - dpar.height))
    if (dockAttached) dockView?.let { wm.updateViewLayout(it, dpar) }
}

internal fun FloatingService.showDock(picker: Boolean) {
    if (!attached) return
    ensureDock()
    ui.removeCallbacks(dockRemoveRunnable)
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
    }
    dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
        .setStartDelay(0).setDuration(260)
        .setInterpolator(OvershootInterpolator(1.1f)).start()
    scheduleHide()
}

internal fun FloatingService.hideDock(animated: Boolean) {
    if (!dockAttached) return
    if (!animated) {
        removeDockNow()
        return
    }
    dockView?.animate()?.alpha(0f)?.scaleX(0.85f)?.scaleY(0.85f)
        ?.setStartDelay(0)?.setDuration(180)?.setInterpolator(AccelerateInterpolator())?.start()
    ui.postDelayed(dockRemoveRunnable, 210)
}

internal fun FloatingService.removeDockNow() {
    ui.removeCallbacks(dockRemoveRunnable)
    if (dockAttached) {
        dockView?.let {
            it.animate().cancel()
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
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

// ------------ إخفاء ناعم بعد 5 ثوان (الأزرار ترجع، والشريط يتلاشى، واللسان يختبئ خلف النافذة) ------------

internal fun FloatingService.softHideCircles() {
    ui.removeCallbacks(hideRunnable)
    closeMenu()
    hideDock(true)
    if (handleAttached && handleShown) {
        handleShown = false
        animateHandle(false) { removeHandleNow() }
    }
}

// إخفاء فوري (عند إخفاء النافذة أو إغلاقها)
internal fun FloatingService.hideCircles() {
    ui.removeCallbacks(hideRunnable)
    removeActionsNow()
    removeDockNow()
    removeHandleNow()
}

// ------------ لمس اللسان: ضغطة = الأزرار، سحب = تغيير حجم النافذة ------------

@SuppressLint("ClickableViewAccessibility")
internal fun FloatingService.attachHandleTouch(h: View) {
    val slop = ViewConfiguration.get(this).scaledTouchSlop
    var rw = 0
    var rh = 0
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
                }
                tx = e.rawX
                ty = e.rawY
                moved = false
                ui.removeCallbacks(hideRunnable)
                h.animate().scaleX(0.92f).scaleY(0.92f).setStartDelay(0).setDuration(90).start()
            }
            MotionEvent.ACTION_MOVE -> {
                if (p != null) {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > slop || abs(dy) > slop) {
                        if (!moved) closeMenu()
                        moved = true
                    }
                    if (moved) {
                        val dm = resources.displayMetrics
                        val maxW = maxOf(dm.widthPixels - p.x, dp(150))
                        val maxH = maxOf(dm.heightPixels - p.y, dp(200))
                        p.width = (rw + dx).toInt().coerceIn(dp(150), maxW)
                        p.height = (rh + dy).toInt().coerceIn(dp(200), maxH)
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
