package com.example.floatapp

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences(FloatingService.PREFS, MODE_PRIVATE) }
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var serviceSwitch: GlassSwitch
    private lateinit var bubbleSwitch: GlassSwitch
    private lateinit var siteValue: TextView
    private lateinit var sizeValue: TextView
    private lateinit var winValue: TextView
    private lateinit var logo: LogoView
    private var logoAnim: ObjectAnimator? = null

    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFF9A9CA3.toInt()
    private val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
    private val match = LinearLayout.LayoutParams.MATCH_PARENT

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun header(t: String) = label(t, 14f, gray).apply {
        setPadding(dp(10), 0, dp(10), dp(8))
    }

    private fun pressable(v: View, onClick: () -> Unit) {
        v.setOnClickListener { onClick() }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.97f).scaleY(0.97f).setStartDelay(0).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).start()
            }
            false
        }
    }

    private fun card(vararg rows: View): LinearLayout {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassPanelDrawable(dp(26).toFloat())
            clipToOutline = true
        }
        for ((i, r) in rows.withIndex()) {
            if (i > 0) {
                g.addView(View(this).apply {
                    setBackgroundColor(0x1FFFFFFF)
                    layoutParams = LinearLayout.LayoutParams(match, 1).apply {
                        marginStart = dp(74)
                        marginEnd = dp(16)
                    }
                })
            }
            g.addView(r)
        }
        return g
    }

    private fun row(icon: View, content: View, trailing: View?): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minimumHeight = dp(72)
        }
        r.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        r.addView(content, LinearLayout.LayoutParams(0, wrap, 1f).apply {
            marginStart = dp(14)
            marginEnd = dp(10)
        })
        if (trailing != null) r.addView(trailing)
        return r
    }

    private fun sliderRow(kind: Int, title: String, seek: SeekBar, value: TextView): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minimumHeight = dp(68)
        }
        r.addView(GlassIconView(this, kind), LinearLayout.LayoutParams(dp(44), dp(44)))
        r.addView(label(title, 16f, white), LinearLayout.LayoutParams(wrap, wrap).apply {
            marginStart = dp(12)
        })
        r.addView(seek, LinearLayout.LayoutParams(0, wrap, 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
        })
        value.minWidth = dp(54)
        value.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        r.addView(value)
        return r
    }

    private fun silverSeek(max: Int, start: Int, onChange: (Int) -> Unit, onStop: (Int) -> Unit) =
        SeekBar(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            this.max = max
            progress = start
            val track = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = dp(2).toFloat()
                setSize(1, dp(4))
            }
            val fill = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF7C7E85.toInt(), 0xFFF5F5F7.toInt())
            ).apply {
                cornerRadius = dp(2).toFloat()
                setSize(1, dp(4))
            }
            val layer = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)))
            layer.setId(0, android.R.id.background)
            layer.setId(1, android.R.id.progress)
            progressDrawable = layer
            thumb = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(dp(22), dp(22))
                setStroke(dp(1), 0x66FFFFFF)
            }
            splitTrack = false
            setPadding(dp(11), 0, dp(11), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = onChange(p)
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) = onStop(s.progress)
            })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF040405.toInt()
        window.navigationBarColor = 0xFF0D0E11.toInt()

        siteValue = label("", 14f, gray)
        sizeValue = label("", 15f, 0xFFD6D8DD.toInt())
        winValue = label("", 15f, 0xFFD6D8DD.toInt())

        logo = LogoView(this)
        val title = label("Float\nLauncher", 40f, white, true).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            setLineSpacing(0f, 0.92f)
            paint.shader = LinearGradient(
                0f, 0f, 0f, dp(110).toFloat(), white, 0xFF8E9096.toInt(), Shader.TileMode.CLAMP
            )
        }
        val sub = label("نوافذ عائمة فوق أي لعبة", 15f, 0xFFB5B7BD.toInt()).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(title)
        titleCol.addView(sub)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(logo, LinearLayout.LayoutParams(dp(132), dp(132)))
        top.addView(titleCol, LinearLayout.LayoutParams(0, wrap, 1f))

        statusTitle = label("الخدمة متوقفة", 18f, white, true)
        statusSub = label("قم بتفعيل الخدمة لتشغيل التطبيق", 12.5f, gray)
        val statusCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusCol.addView(statusTitle)
        statusCol.addView(statusSub)
        serviceSwitch = GlassSwitch(this)
        serviceSwitch.onChange = { on ->
            if (on) {
                if (!startFloating()) serviceSwitch.setChecked(false, true)
            } else {
                stopService(Intent(this, FloatingService::class.java))
            }
            window.decorView.postDelayed({ refreshStatus() }, 600)
        }

        bubbleSwitch = GlassSwitch(this)
        bubbleSwitch.setChecked(prefs.getBoolean(FloatingService.KEY_BUBBLE, true), false)
        bubbleSwitch.onChange = { on ->
            prefs.edit().putBoolean(FloatingService.KEY_BUBBLE, on).apply()
            FloatingService.instance?.setBubbleEnabled(on)
        }

        val siteTrail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        siteTrail.addView(siteValue)
        siteTrail.addView(label("›", 26f, 0x88FFFFFF.toInt()).apply { setPadding(dp(10), 0, 0, dp(3)) })
        val siteRow = row(GlassIconView(this, GlassIconView.GLOBE), label("الموقع", 17f, white), siteTrail)
        pressable(siteRow) { pickSite() }

        val iconSeek = silverSeek(
            110, prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE) - 40,
            onChange = { p ->
                sizeValue.text = "${p + 40} dp"
                FloatingService.instance?.setSize(p + 40)
            },
            onStop = { p -> prefs.edit().putInt(FloatingService.KEY_SIZE, p + 40).apply() }
        )
        val winSeek = silverSeek(
            70, prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN) - 30,
            onChange = { p ->
                winValue.text = "${p + 30}%"
                FloatingService.instance?.setWindowPercent(p + 30)
            },
            onStop = { p -> prefs.edit().putInt(FloatingService.KEY_WIN, p + 30).apply() }
        )

        val tips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        tips.addView(GlassIconView(this, GlassIconView.INFO, false), LinearLayout.LayoutParams(dp(30), dp(30)))
        tips.addView(
            label(
                "اضغط على اللسان لإظهار الأزرار واسحبه لتغيير الحجم. اضغط مطولاً على النافذة لتحريكها. اضغط ثانيتين على الأيقونة لإغلاق التطبيق.",
                12.5f, gray
            ),
            LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(12) }
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(36))
        }
        content.addView(top)
        content.addView(gap(14))
        content.addView(card(row(GlassIconView(this, GlassIconView.SHIELD), statusCol, serviceSwitch)))
        content.addView(gap(22))
        content.addView(header("التشغيل"))
        content.addView(card(row(GlassIconView(this, GlassIconView.BOLT), label("الأيقونة العائمة", 17f, white), bubbleSwitch)))
        content.addView(gap(22))
        content.addView(header("التطبيق الافتراضي"))
        content.addView(card(siteRow))
        content.addView(gap(22))
        content.addView(header("الحجم"))
        content.addView(card(
            sliderRow(GlassIconView.EXPAND, "حجم الأيقونة", iconSeek, sizeValue),
            sliderRow(GlassIconView.WINDOWS, "حجم النافذة", winSeek, winValue)
        ))
        content.addView(gap(22))
        content.addView(tips)

        val root = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        root.addView(BackgroundView(this), FrameLayout.LayoutParams(match, match))
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        })
        setContentView(root)

        refreshLabels()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        for (i in 0 until content.childCount) {
            val c = content.getChildAt(i)
            c.alpha = 0f
            c.translationY = dp(28).toFloat()
            c.animate().alpha(1f).translationY(0f)
                .setStartDelay(70L * i).setDuration(480)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        logoAnim?.cancel()
        logoAnim = ObjectAnimator.ofFloat(logo, "translationY", 0f, -dp(6).toFloat()).apply {
            duration = 2600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onPause() {
        logoAnim?.cancel()
        super.onPause()
    }

    private fun fadeText(tv: TextView, s: String) {
        if (tv.text.toString() == s) return
        tv.animate().alpha(0f).setStartDelay(0).setDuration(120).withEndAction {
            tv.text = s
            tv.animate().alpha(1f).setStartDelay(0).setDuration(180).start()
        }.start()
    }

    private fun refreshStatus() {
        val on = FloatingService.instance != null
        fadeText(statusTitle, if (on) "الخدمة تعمل" else "الخدمة متوقفة")
        fadeText(statusSub, if (on) "الأيقونة العائمة جاهزة على الشاشة" else "قم بتفعيل الخدمة لتشغيل التطبيق")
        if (serviceSwitch.checked != on) serviceSwitch.setChecked(on, true)
        bubbleSwitch.setChecked(prefs.getBoolean(FloatingService.KEY_BUBBLE, true), false)
    }

    private fun refreshLabels() {
        val idx = prefs.getInt(FloatingService.KEY_SITE, 0).coerceIn(0, FloatingService.SITES.lastIndex)
        siteValue.text = FloatingService.SITES[idx].first
        sizeValue.text = "${prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE)} dp"
        winValue.text = "${prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN)}%"
    }

    private fun tile(k: Int, selected: Boolean, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            if (selected) background = GlassPanelDrawable(dp(24).toFloat())
        }
        val img = ImageView(this).apply { setImageDrawable(SiteGlassDrawable(k)) }
        box.addView(img, FrameLayout.LayoutParams(dp(64), dp(64)))
        col.addView(box)
        col.addView(label(FloatingService.SITES[k].first, 12f, if (selected) white else gray).apply {
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(4), 0, 0)
        })
        pressable(col, onClick)
        col.layoutParams = LinearLayout.LayoutParams(dp(94), wrap)
        return col
    }

    private fun pickSite() {
        val cur = prefs.getInt(FloatingService.KEY_SITE, 0)
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(20), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(0xF2101114.toInt())
                cornerRadius = dp(28).toFloat()
            }
            foreground = GlassPanelDrawable(dp(28).toFloat())
        }
        panel.addView(label("اختر الموقع", 18f, white, true))
        panel.addView(gap(12))
        val sites = FloatingService.SITES
        var i = 0
        while (i < sites.size) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            for (k in i until minOf(i + 3, sites.size)) {
                line.addView(tile(k, k == cur) {
                    prefs.edit().putInt(FloatingService.KEY_SITE, k).apply()
                    refreshLabels()
                    FloatingService.instance?.onSiteChanged()
                    dlg.dismiss()
                })
            }
            panel.addView(line)
            i += 3
        }
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.7f)
            setLayout(dp(330), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        panel.alpha = 0f
        panel.scaleX = 0.9f
        panel.scaleY = 0.9f
        dlg.setOnShowListener {
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        dlg.show()
    }

    private fun startFloating(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات الأخرى» ثم ارجع", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return false
        }
        if (FloatingService.instance == null) {
            startForegroundService(Intent(this, FloatingService::class.java))
        }
        return true
    }
}
