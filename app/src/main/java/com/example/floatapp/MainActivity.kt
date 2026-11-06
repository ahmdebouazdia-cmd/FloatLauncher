package com.example.floatapp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences(FloatingService.PREFS, MODE_PRIVATE) }
    private lateinit var siteValue: TextView
    private lateinit var sizeValue: TextView
    private lateinit var winValue: TextView
    private lateinit var status: TextView
    private lateinit var power: Switch
    private var updatingSwitch = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- أدوات الشكل الزجاجي (مثل إعدادات آيفون) ----------

    private fun glass(radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(0x38FFFFFF, 0x12FFFFFF)
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0x45FFFFFF)
    }

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

    private fun pressable(v: View, onClick: () -> Unit) {
        v.setOnClickListener { onClick() }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.98f).scaleY(0.98f).setStartDelay(0).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start()
            }
            false
        }
    }

    private fun iconSquare(glyph: String, color: Int) = TextView(this).apply {
        text = glyph
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
    }

    private fun header(t: String) = label(t, 13f, 0x99FFFFFF.toInt()).apply {
        setPadding(dp(18), 0, dp(18), dp(6))
    }

    // مجموعة بطاقات شفافة بزوايا دائرية، بينها فواصل رفيعة
    private fun group(vararg rows: View): LinearLayout {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(22)
            clipToOutline = true
        }
        for ((i, r) in rows.withIndex()) {
            if (i > 0) {
                g.addView(View(this).apply {
                    setBackgroundColor(0x26FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { marginStart = dp(58) }
                })
            }
            g.addView(r)
        }
        return g
    }

    private fun row(glyph: String, color: Int, title: String, right: View?, onClick: (() -> Unit)? = null): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            minimumHeight = dp(56)
        }
        r.addView(iconSquare(glyph, color))
        val t = label(title, 16f, Color.WHITE)
        t.setPadding(dp(12), 0, dp(12), 0)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) r.addView(right)
        if (onClick != null) {
            r.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, maskDrawable())
            pressable(r, onClick)
        }
        return r
    }

    // قناع للتموج عند الضغط
    private fun maskDrawable() = GradientDrawable().apply { setColor(Color.WHITE) }

    private fun seekRow(glyph: String, color: Int, title: String, valueView: TextView, seek: SeekBar): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(iconSquare(glyph, color))
        val t = label(title, 16f, Color.WHITE)
        t.setPadding(dp(12), 0, dp(12), 0)
        top.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(valueView)
        col.addView(top)
        col.addView(seek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        return col
    }

    private fun seek(max: Int, start: Int, onChange: (Int) -> Unit, onStop: (Int) -> Unit) =
        SeekBar(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            this.max = max
            progress = start
            progressTintList = ColorStateList.valueOf(0xFF0A84FF.toInt())
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(0x40FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = onChange(p)
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) = onStop(s.progress)
            })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF070B1A.toInt()
        window.navigationBarColor = 0xFF0A2233.toInt()

        siteValue = label("", 15f, 0xB3FFFFFF.toInt())
        sizeValue = label("", 15f, 0xB3FFFFFF.toInt())
        winValue = label("", 15f, 0xB3FFFFFF.toInt())
        status = label("", 13f, Color.WHITE).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = glass(30)
        }

        power = Switch(this).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(states, intArrayOf(0xFF34C759.toInt(), 0x55FFFFFF))
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                if (!updatingSwitch) {
                    if (checked) {
                        if (!startFloating()) setSwitch(false)
                    } else {
                        stopService(Intent(this@MainActivity, FloatingService::class.java))
                    }
                    window.decorView.postDelayed({ refreshStatus() }, 500)
                }
            }
        }

        val iconSeek = seek(
            110, prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE) - 40,
            onChange = { p ->
                sizeValue.text = "${p + 40} dp"
                FloatingService.instance?.setSize(p + 40)
            },
            onStop = { p -> prefs.edit().putInt(FloatingService.KEY_SIZE, p + 40).apply() }
        )
        val winSeek = seek(
            70, prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN) - 30,
            onChange = { p ->
                winValue.text = "${p + 30}%"
                FloatingService.instance?.setWindowPercent(p + 30)
            },
            onStop = { p -> prefs.edit().putInt(FloatingService.KEY_WIN, p + 30).apply() }
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(64), dp(18), dp(32))
        }
        content.addView(label("Float Launcher", 34f, Color.WHITE, true))
        content.addView(label("نوافذ عائمة فوق أي لعبة", 15f, 0xB3FFFFFF.toInt()))
        content.addView(gap(14))
        content.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(gap(22))

        content.addView(header("التشغيل"))
        content.addView(group(row("⚡", 0xFF34C759.toInt(), "الأيقونة العائمة", power)))
        content.addView(gap(20))

        content.addView(header("التطبيق الافتراضي"))
        content.addView(group(
            row("🌐", 0xFF0A84FF.toInt(), "الموقع", LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(siteValue)
                addView(label("‹", 24f, 0x66FFFFFF.toInt()).apply { setPadding(dp(8), 0, 0, dp(2)) })
            }) { pickSite() }
        ))
        content.addView(gap(20))

        content.addView(header("الحجم"))
        content.addView(group(
            seekRow("🎯", 0xFFFF9F0A.toInt(), "حجم الأيقونة", sizeValue, iconSeek),
            seekRow("🖼", 0xFFBF5AF2.toInt(), "حجم النافذة", winValue, winSeek)
        ))
        content.addView(gap(18))
        content.addView(label(
            "اضغط على القرط لإظهار الأزرار، واسحبه لتغيير الحجم. اضغط مطولاً على النافذة لتحريكها. اضغط ثانيتين على الأيقونة لإغلاق التطبيق.",
            12f, 0x80FFFFFF.toInt()
        ).apply { setPadding(dp(18), 0, dp(18), 0) })

        val root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF070B1A.toInt(), 0xFF150F2E.toInt(), 0xFF0A2233.toInt())
            )
        }
        fun blob(color: Int, g: Int, size: Int) = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(size / 2).toFloat()
                colors = intArrayOf(color, color and 0x00FFFFFF)
            }
            layoutParams = FrameLayout.LayoutParams(dp(size), dp(size), g)
        }
        root.addView(blob(0x66A78BFA, Gravity.TOP or Gravity.END, 340))
        root.addView(blob(0x5522D3EE, Gravity.BOTTOM or Gravity.START, 360))
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
        setContentView(root)

        refreshLabels()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // دخول متتابع ناعم لكل عنصر
        for (i in 0 until content.childCount) {
            val c = content.getChildAt(i)
            c.alpha = 0f
            c.translationY = dp(26).toFloat()
            c.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L * i).setDuration(420)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun setSwitch(on: Boolean) {
        updatingSwitch = true
        power.isChecked = on
        updatingSwitch = false
    }

    private fun refreshStatus() {
        val on = FloatingService.instance != null
        status.text = if (on) "●  الخدمة تعمل" else "○  الخدمة متوقفة"
        status.setTextColor(if (on) 0xFF4ADE80.toInt() else 0xCCFFFFFF.toInt())
        setSwitch(on)
    }

    private fun refreshLabels() {
        val idx = prefs.getInt(FloatingService.KEY_SITE, 0).coerceIn(0, FloatingService.SITES.lastIndex)
        siteValue.text = FloatingService.SITES[idx].first
        sizeValue.text = "${prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE)} dp"
        winValue.text = "${prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN)}%"
    }

    private fun pickSite() {
        AlertDialog.Builder(this)
            .setTitle("اختر الموقع")
            .setItems(FloatingService.SITES.map { it.first }.toTypedArray()) { _, i ->
                prefs.edit().putInt(FloatingService.KEY_SITE, i).apply()
                refreshLabels()
                FloatingService.instance?.onSiteChanged()
            }
            .show()
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
