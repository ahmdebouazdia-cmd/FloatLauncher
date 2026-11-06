package com.example.floatapp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs

class Tab(val site: Int, val web: WebView)

class FloatingService : Service() {

    companion object {
        const val PREFS = "cfg"
        const val KEY_SITE = "site"
        const val KEY_SIZE = "size"
        const val KEY_WIN = "win"
        const val DEFAULT_SIZE = 70 // حجم الأيقونة dp
        const val DEFAULT_WIN = 70  // حجم النافذة % من الشاشة
        const val MAX_TABS = 4

        // الاسم، الرابط، حزمة التطبيق (لعرض أيقونته إن كان مثبتاً)
        val SITES = listOf(
            Triple("Instagram", "https://www.instagram.com/", "com.instagram.android"),
            Triple("Facebook", "https://m.facebook.com/", "com.facebook.katana"),
            Triple("YouTube", "https://m.youtube.com/", "com.google.android.youtube"),
            Triple("TikTok", "https://www.tiktok.com/", "com.zhiliaoapp.musically"),
            Triple("X (Twitter)", "https://mobile.twitter.com/", "com.twitter.android"),
            Triple("Telegram", "https://web.telegram.org/k/", "org.telegram.messenger"),
            Triple("Google", "https://www.google.com/", "com.google.android.googlequicksearchbox")
        )

        @Volatile
        var instance: FloatingService? = null
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    internal val ui = Handler(Looper.getMainLooper())
    internal lateinit var wm: WindowManager

    // الأيقونة العائمة
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var bubble: ImageView? = null
    private var bubbleAttached = false
    private var dust: DustView? = null

    // النافذة والتبويبات
    internal var winParams: WindowManager.LayoutParams? = null
    internal var windowView: FrameLayout? = null
    internal var contentBox: FrameLayout? = null
    internal val tabs = mutableListOf<Tab>()
    internal var current = 0
    internal var attached = false
    internal var kbOn = false
    private var busy = false
    internal var customView: View? = null
    internal var customCb: WebChromeClient.CustomViewCallback? = null

    // القرط والأزرار وشريط التطبيقات (Controls.kt)
    internal var handleView: EarringView? = null
    internal var handleParams: WindowManager.LayoutParams? = null
    internal var handleAttached = false
    internal var menuOpen = false
    internal val actionViews = mutableListOf<GlassCircleView>()
    internal val actionParams = mutableListOf<WindowManager.LayoutParams>()
    internal var kbAction: GlassCircleView? = null
    internal var dockView: DockView? = null
    internal var dockParams: WindowManager.LayoutParams? = null
    internal var dockAttached = false
    internal var dockPicker = false
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
    internal fun siteIndex() = prefs().getInt(KEY_SITE, 0).coerceIn(0, SITES.lastIndex)

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

    // ================= الأيقونة العائمة =================

    // سحب الأيقونة، ضغطة = فتح النافذة، ضغط مستمر ثانيتين = إغلاق التطبيق بتأثير الغبار
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
        fun restore() {
            target.animate().cancel()
            target.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        val holdRunnable = Runnable {
            held = true
            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold?.invoke()
        }
        target.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x
                    sy = p.y
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                    held = false
                    if (onHold != null) {
                        ui.postDelayed(holdRunnable, holdMs)
                        target.animate().cancel()
                        target.animate().scaleX(0.75f).scaleY(0.75f)
                            .setStartDelay(250).setDuration(holdMs - 250)
                            .setInterpolator(LinearInterpolator()).start()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (!moved && (abs(dx) > 10 || abs(dy) > 10)) {
                        moved = true
                        ui.removeCallbacks(holdRunnable)
                        restore()
                    }
                    if (moved) {
                        p.x = sx + dx.toInt()
                        p.y = sy + dy.toInt()
                        wm.updateViewLayout(host, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(holdRunnable)
                    if (!held) restore()
                    if (!moved && !held) onTap?.invoke()
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(holdRunnable)
                    restore()
                }
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
            alpha = 0.92f
        }
        attachDrag(
            view, bubbleParams, view,
            onTap = { toggleWindow() },
            holdMs = 2000L,
            onHold = { quitApp() }
        )
        bubble = view
        applyIcon()
        showBubble()
    }

    // الأيقونة تظهر بحركة نابضة وتختفي عند فتح النافذة
    private fun showBubble() {
        val v = bubble ?: return
        if (!bubbleAttached) {
            wm.addView(v, bubbleParams)
            bubbleAttached = true
            v.scaleX = 0.4f
            v.scaleY = 0.4f
            v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(300)
                .setInterpolator(OvershootInterpolator(1.6f)).start()
        }
    }

    private fun hideBubble() {
        val v = bubble ?: return
        if (bubbleAttached) {
            v.animate().cancel()
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
            view.setImageDrawable(packageManager.getApplicationIcon(SITES[siteIndex()].third))
        } catch (e: Exception) {
            view.setImageResource(R.drawable.ic_launcher)
        }
    }

    fun onSiteChanged() {
        applyIcon()
    }

    // إغلاق التطبيق: الأيقونة تتحول إلى غبار يتلاشى
    private fun quitApp() {
        val v = bubble
        val d = v?.drawable
        val w = bubbleParams.width
        val h = bubbleParams.height
        if (v == null || d == null || !bubbleAttached || w <= 0 || h <= 0) {
            stopSelf()
            return
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        copy.setBounds(0, 0, w, h)
        copy.draw(Canvas(bmp))
        val m = dp(70)
        val dustParams = WindowManager.LayoutParams(
            w + 2 * m, h + 2 * m,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x - m
            y = bubbleParams.y - m
        }
        val dv = DustView(this, bmp, m)
        hideBubble()
        wm.addView(dv, dustParams)
        dust = dv
        dv.start {
            try {
                wm.removeView(dv)
            } catch (e: Exception) {
            }
            dust = null
            stopSelf()
        }
    }

    // ================= النافذة =================

    // النافذة بنسبة الشاشة تقريباً ليتناسق المحتوى مع شكل الهاتف
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
        p.x = maxOf(0, p.x + p.width - w)
        p.y = maxOf(0, p.y + p.height - h)
        p.width = w
        p.height = h
        if (attached) wm.updateViewLayout(v, p)
        layoutHandle()
    }

    private fun toggleWindow() {
        if (busy) return
        when {
            windowView == null -> createWindow()
            attached -> hideWindow()
            else -> showWindow()
        }
    }

    private fun pivotFromBubble(v: View, p: WindowManager.LayoutParams) {
        v.pivotX = (bubbleParams.x + bubbleParams.width / 2f - p.x).coerceIn(0f, p.width.toFloat())
        v.pivotY = (bubbleParams.y + bubbleParams.height / 2f - p.y).coerceIn(0f, p.height.toFloat())
    }

    private fun animateIn(v: View) {
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(280)
            .setInterpolator(DecelerateInterpolator()).start()
    }

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

        val box = FrameLayout(this)
        box.setBackgroundColor(Color.BLACK)
        box.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            for (tb in tabs) fitWeb(tb.web, r - l, b - t)
        }
        root.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))

        winParams = p
        windowView = root
        contentBox = box

        root.alpha = 0f
        root.scaleX = 0.85f
        root.scaleY = 0.85f
        pivotFromBubble(root, p)
        wm.addView(root, p)
        attached = true
        addTab(siteIndex())
        hideBubble()
        animateIn(root)
        showHandle()
    }

    internal fun hideWindow() {
        val v = windowView ?: return
        val p = winParams ?: return
        if (!attached || busy) return
        busy = true
        if (kbOn) toggleKeyboard()
        hideCircles()
        pivotFromBubble(v, p)
        v.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setStartDelay(0).setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (windowView === v && attached) {
                    wm.removeView(v)
                    attached = false
                    activeWeb()?.onPause()
                    busy = false
                    showBubble()
                }
            }.start()
    }

    private fun showWindow() {
        val v = windowView ?: return
        val p = winParams ?: return
        if (attached || busy) return
        v.alpha = 0f
        v.scaleX = 0.85f
        v.scaleY = 0.85f
        pivotFromBubble(v, p)
        wm.addView(v, p)
        attached = true
        activeWeb()?.onResume()
        hideBubble()
        animateIn(v)
        showHandle()
    }

    // إغلاق كامل وتحرير ذاكرة المتصفحات
    internal fun closeWindow() {
        hideCircles()
        hideCustom()
        busy = false
        val v = windowView
        if (v != null) {
            v.animate().cancel()
            if (attached) wm.removeView(v)
            attached = false
            v.removeAllViews()
        }
        for (t in tabs) {
            t.web.stopLoading()
            t.web.destroy()
        }
        tabs.clear()
        current = 0
        contentBox = null
        windowView = null
        winParams = null
        kbOn = false
        kbAction?.active = false
        CookieManager.getInstance().flush()
        showBubble()
    }

    // ================= الكتابة والرجوع =================

    internal fun toggleKeyboard() {
        val v = windowView ?: return
        val p = winParams ?: return
        kbOn = !kbOn
        p.flags = if (kbOn) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (attached) wm.updateViewLayout(v, p)
        kbAction?.active = kbOn
        if (kbOn) {
            activeWeb()?.requestFocus()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    internal fun goBack() {
        if (customView != null) {
            hideCustom()
        } else {
            activeWeb()?.let { if (it.canGoBack()) it.goBack() }
        }
        scheduleHide()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        dust?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        dust = null
        closeWindow()
        hideCircles()
        hideBubble()
        bubble = null
        handleView = null
        dockView = null
        instance = null
        super.onDestroy()
    }
}
