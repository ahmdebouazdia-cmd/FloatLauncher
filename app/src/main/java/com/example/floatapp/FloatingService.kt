package com.example.floatapp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FloatingService : Service() {

    companion object {
        const val PREFS = "cfg"
        const val KEY_SITE = "site"
        const val KEY_SIZE = "size"
        const val KEY_WIN = "win"
        const val DEFAULT_SIZE = 70 // حجم الأيقونة dp
        const val DEFAULT_WIN = 70  // حجم النافذة % من الشاشة

        val SITES = listOf(
            Triple("Instagram", "https://www.instagram.com/", "com.instagram.android"),
            Triple("Facebook", "https://m.facebook.com/", "com.facebook.katana"),
            Triple("YouTube", "https://m.youtube.com/", "com.google.android.youtube"),
            Triple("TikTok", "https://www.tiktok.com/", "com.zhiliaoapp.musically"),
            Triple("X (Twitter)", "https://mobile.twitter.com/", "com.twitter.android"),
            Triple("Telegram", "https://web.telegram.org/k/", "org.telegram.messenger")
        )

        @Volatile
        var instance: FloatingService? = null
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    internal val ui = Handler(Looper.getMainLooper())

    internal lateinit var wm: WindowManager

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var bubble: ImageView? = null
    private var bubbleAttached = false

    internal var winParams: WindowManager.LayoutParams? = null
    internal var windowView: FrameLayout? = null
    private var webView: WebView? = null
    internal var attached = false
    internal var kbOn = false

    internal var handleView: GlassCircleView? = null
    internal var handleParams: WindowManager.LayoutParams? = null
    internal var handleAttached = false
    internal var menuOpen = false
    internal val actionViews = mutableListOf<GlassCircleView>()
    internal val actionParams = mutableListOf<WindowManager.LayoutParams>()
    internal var kbAction: GlassCircleView? = null
    internal val hideRunnable = Runnable { hideCircles() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun site() = SITES[prefs().getInt(KEY_SITE, 0).coerceIn(0, SITES.lastIndex)]

    private fun buildNotification(): Notification {
        val channelId = "float_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Float Launcher", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("الأيقونة العائمة تعمل")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(
        target: View,
        p: WindowManager.LayoutParams,
        host: View,
        onTap: (() -> Unit)? = null,
        holdMs: Long = 0L,
        onHold: (() -> Unit)? = null
    ) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        var held = false
        val holdRunnable = Runnable {
            held = true
            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold?.invoke()
        }
        target.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x; sy = p.y
                    tx = e.rawX; ty = e.rawY
                    moved = false
                    held = false
                    if (onHold != null) ui.postDelayed(holdRunnable, holdMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        moved = true
                        ui.removeCallbacks(holdRunnable)
                    }
                    if (moved) {
                        p.x = sx + dx.toInt()
                        p.y = sy + dy.toInt()
                        wm.updateViewLayout(host, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(holdRunnable)
                    if (!moved && !held) onTap?.invoke()
                }
                MotionEvent.ACTION_CANCEL -> ui.removeCallbacks(holdRunnable)
            }
            true
        }
    }

    private fun createBubble() {
        val size = dp(prefs().getInt(KEY_SIZE, DEFAULT_SIZE))
        bubbleParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.9f
        }
        attachDrag(
            view, bubbleParams, view,
            onTap = { toggleWindow() },
            holdMs = 4000L,
            onHold = { quitApp() }
        )
        bubble = view
        showBubble()
        applyIcon()
    }

    private fun quitApp() {
        Toast.makeText(this, "تم إغلاق Float Launcher", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun showBubble() {
        val v = bubble ?: return
        if (!bubbleAttached) {
            wm.addView(v, bubbleParams)
            bubbleAttached = true
        }
    }

    private fun hideBubble() {
        val v = bubble ?: return
        if (bubbleAttached) {
            wm.removeView(v)
            bubbleAttached = false
        }
    }

    fun setSize(sizeDp: Int) {
        val view = bubble ?: return
        val px = dp(sizeDp)
        bubbleParams.width = px
        bubbleParams.height = px
        if (bubbleAttached) wm.updateViewLayout(view, bubbleParams)
    }

    fun applyIcon() {
        val view = bubble ?: return
        try {
            view.setImageDrawable(packageManager.getApplicationIcon(site().third))
        } catch (e: Exception) {
            view.setImageResource(R.drawable.ic_launcher)
        }
    }

    fun onSiteChanged() {
        applyIcon()
        webView?.loadUrl(site().second)
    }

    private fun windowSize(pct: Int): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * pct / 100f).toInt()
        val h = (dm.heightPixels * pct / 100f * 0.85f).toInt()
        return Pair(w, h)
    }

    fun setWindowPercent(pct: Int) {
        val p = winParams ?: return
        val v = windowView ?: return
        val (w, h) = windowSize(pct)
        p.width = w
        p.height = h
        if (attached) wm.updateViewLayout(v, p)
        layoutHandle()
    }

    private fun toggleWindow() {
        when {
            windowView == null -> createWindow()
            attached -> hideWindow()
            else -> showWindow()
        }
    }

    private fun fitWeb(web: WebView, cw: Int, ch: Int) {
        if (cw <= 0 || ch <= 0) return
        val d = resources.displayMetrics.density
        val baseDp = (cw / d / 0.8f).coerceIn(320f, 412f)
        val baseW = (baseDp * d).toInt()
        val scale = cw.toFloat() / baseW
        val newH = (ch / scale).toInt()
        val lp = web.layoutParams
        if (lp.width != baseW || lp.height != newH) {
            lp.width = baseW
            lp.height = newH
            web.layoutParams = lp
        }
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = scale
        web.scaleY = scale
    }

    @Suppress("DEPRECATION")
    private fun enableDark(s: WebSettings) {
        if (Build.VERSION.SDK_INT >= 33) {
            s.isAlgorithmicDarkeningAllowed = true
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.forceDark = WebSettings.FORCE_DARK_ON
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachWebTouch(web: WebView) {
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWindow() {
        val dm = resources.displayMetrics
        val (w, h) = windowSize(prefs().getInt(KEY_WIN, DEFAULT_WIN))
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - w) / 2
            y = ((dm.heightPixels - h) * 0.2f).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        val root = FrameLayout(this)
        root.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(18).toFloat()
        }
        root.clipToOutline = true
        root.foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0x55FFFFFF)
        }

        val web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        enableDark(web.settings)
        web.webViewClient = WebViewClient()
        attachWebTouch(web)
        web.loadUrl(site().second)

        val container = FrameLayout(this)
        container.setBackgroundColor(Color.BLACK)
        container.addView(web, FrameLayout.LayoutParams(dp(360), 1))
        container.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            fitWeb(web, r - l, b - t)
        }
        root.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))

        winParams = p
        windowView = root
        webView = web
        wm.addView(root, p)
        attached = true
        hideBubble()
        showHandle()
    }

    internal fun hideWindow() {
        val v = windowView ?: return
        if (kbOn) toggleKeyboard()
        hideCircles()
        if (attached) wm.removeView(v)
        attached = false
        webView?.onPause()
        showBubble()
    }

    private fun showWindow() {
        val v = windowView ?: return
        val p = winParams ?: return
        if (!attached) wm.addView(v, p)
        attached = true
        webView?.onResume()
        hideBubble()
        showHandle()
    }

    internal fun closeWindow() {
        hideCircles()
        val v = windowView ?: return
        if (attached) wm.removeView(v)
        attached = false
        v.removeAllViews()
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        windowView = null
        winParams = null
        kbOn = false
        kbAction?.active = false
        CookieManager.getInstance().flush()
        showBubble()
    }

    internal fun toggleKeyboard() {
        val v = windowView ?: return
        val p = winParams ?: return
        kbOn = !kbOn
        p.flags = if (kbOn) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (attached) wm.updateViewLayout(v, p)
        kbAction?.active = kbOn
        if (kbOn) {
            webView?.requestFocus()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    internal fun goBack() {
        webView?.let { if (it.canGoBack()) it.goBack() }
        scheduleHide()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        closeWindow()
        hideCircles()
        hideBubble()
        bubble = null
        handleView = null
        instance = null
        super.onDestroy()
    }
}
