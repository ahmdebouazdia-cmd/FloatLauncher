name: Build APK (all-in-one)

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Generate project files
        run: |
          cat > settings.gradle << 'FILE_EOF'
          pluginManagement {
              repositories {
                  google()
                  mavenCentral()
                  gradlePluginPortal()
              }
          }
          dependencyResolutionManagement {
              repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
              repositories {
                  google()
                  mavenCentral()
              }
          }
          rootProject.name = "FloatLauncher"
          include ':app'
          FILE_EOF
          cat > build.gradle << 'FILE_EOF'
          plugins {
              id 'com.android.application' version '8.2.2' apply false
              id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
          }
          FILE_EOF
          cat > gradle.properties << 'FILE_EOF'
          org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
          android.useAndroidX=true
          kotlin.code.style=official
          FILE_EOF
          mkdir -p app
          cat > app/build.gradle << 'FILE_EOF'
          plugins {
              id 'com.android.application'
              id 'org.jetbrains.kotlin.android'
          }

          android {
              namespace 'com.example.floatapp'
              compileSdk 34

              defaultConfig {
                  applicationId 'com.example.floatapp'
                  minSdk 26
                  targetSdk 33
                  versionCode 1
                  versionName '1.0'
              }

              buildTypes {
                  release {
                      minifyEnabled true
                      shrinkResources true
                      proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
                      signingConfig signingConfigs.debug
                  }
              }

              compileOptions {
                  sourceCompatibility JavaVersion.VERSION_17
                  targetCompatibility JavaVersion.VERSION_17
              }
              kotlinOptions {
                  jvmTarget = '17'
              }
          }
          FILE_EOF
          mkdir -p app/src/main
          cat > app/src/main/AndroidManifest.xml << 'FILE_EOF'
          <?xml version="1.0" encoding="utf-8"?>
          <manifest xmlns:android="http://schemas.android.com/apk/res/android">

              <uses-permission android:name="android.permission.INTERNET" />
              <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
              <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
              <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

              <!-- يسمح برؤية التطبيقات المثبتة (لعرض أيقونة التطبيق) -->
              <queries>
                  <intent>
                      <action android:name="android.intent.action.MAIN" />
                      <category android:name="android.intent.category.LAUNCHER" />
                  </intent>
              </queries>

              <application
                  android:allowBackup="false"
                  android:supportsRtl="true"
                  android:icon="@drawable/ic_launcher"
                  android:label="Float Launcher"
                  android:theme="@android:style/Theme.Material.NoActionBar">

                  <activity
                      android:name=".MainActivity"
                      android:exported="true">
                      <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                  </activity>

                  <service
                      android:name=".FloatingService"
                      android:exported="false" />
              </application>
          </manifest>
          FILE_EOF
          mkdir -p app/src/main/res/drawable
          cat > app/src/main/res/drawable/ic_launcher.xml << 'FILE_EOF'
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:width="96dp"
              android:height="96dp"
              android:viewportWidth="96"
              android:viewportHeight="96">
              <path
                  android:fillColor="#0F1729"
                  android:pathData="M20,0h56a20,20 0 0 1 20,20v56a20,20 0 0 1 -20,20h-56a20,20 0 0 1 -20,-20v-56a20,20 0 0 1 20,-20z" />
              <path
                  android:fillColor="#D29BFF"
                  android:pathData="M40,20h34a4,4 0 0 1 4,4v34a4,4 0 0 1 -4,4h-34a4,4 0 0 1 -4,-4v-34a4,4 0 0 1 4,-4z" />
              <path
                  android:fillColor="#FFA81F"
                  android:pathData="M30,30h34a4,4 0 0 1 4,4v34a4,4 0 0 1 -4,4h-34a4,4 0 0 1 -4,-4v-34a4,4 0 0 1 4,-4z" />
              <path
                  android:fillColor="#19D3FF"
                  android:pathData="M20,40h34a4,4 0 0 1 4,4v34a4,4 0 0 1 -4,4h-34a4,4 0 0 1 -4,-4v-34a4,4 0 0 1 4,-4z" />
          </vector>
          FILE_EOF
          mkdir -p app/src/main/java/com/example/floatapp
          cat > app/src/main/java/com/example/floatapp/GlassCircleView.kt << 'FILE_EOF'
          package com.example.floatapp

          import android.content.Context
          import android.graphics.Canvas
          import android.graphics.Color
          import android.graphics.Paint
          import android.graphics.Path
          import android.graphics.RadialGradient
          import android.graphics.Shader
          import android.view.View

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
          FILE_EOF
          mkdir -p app/src/main/java/com/example/floatapp
          cat > app/src/main/java/com/example/floatapp/MainActivity.kt << 'FILE_EOF'
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
          import android.view.View
          import android.widget.*

          class MainActivity : Activity() {

              private val prefs by lazy { getSharedPreferences(FloatingService.PREFS, MODE_PRIVATE) }
              private lateinit var siteValue: TextView
              private lateinit var sizeValue: TextView
              private lateinit var winValue: TextView
              private lateinit var status: TextView

              private val cyan = 0xFF22D3EE.toInt()
              private val violet = 0xFFA78BFA.toInt()

              private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

              // خلفية زجاجية: تدرج أبيض شفاف + حد رفيع
              private fun glass(radius: Int = 22) = GradientDrawable(
                  GradientDrawable.Orientation.TL_BR, intArrayOf(0x33FFFFFF, 0x0DFFFFFF)
              ).apply {
                  cornerRadius = dp(radius).toFloat()
                  setStroke(dp(1), 0x40FFFFFF)
              }

              private fun ripple(bg: GradientDrawable) =
                  RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), bg, null)

              private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
                  text = t
                  textSize = size
                  setTextColor(color)
                  textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                  if (bold) typeface = Typeface.DEFAULT_BOLD
              }

              private fun card(vararg children: View) = LinearLayout(this).apply {
                  orientation = LinearLayout.VERTICAL
                  setPadding(dp(18), dp(16), dp(18), dp(18))
                  background = glass()
                  children.forEach { addView(it) }
              }

              private fun gap(h: Int) = View(this).apply {
                  layoutParams = LinearLayout.LayoutParams(1, dp(h))
              }

              private fun seek(max: Int, start: Int, onChange: (Int) -> Unit, onStop: (Int) -> Unit) =
                  SeekBar(this).apply {
                      layoutDirection = View.LAYOUT_DIRECTION_LTR
                      this.max = max
                      progress = start
                      progressTintList = ColorStateList.valueOf(cyan)
                      thumbTintList = ColorStateList.valueOf(violet)
                      progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
                      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                          override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = onChange(p)
                          override fun onStartTrackingTouch(s: SeekBar) {}
                          override fun onStopTrackingTouch(s: SeekBar) = onStop(s.progress)
                      })
                  }

              private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
                  text = label
                  isAllCaps = false
                  textSize = 16f
                  typeface = Typeface.DEFAULT_BOLD
                  setTextColor(Color.WHITE)
                  stateListAnimator = null
                  background = ripple(
                      GradientDrawable(
                          GradientDrawable.Orientation.LEFT_RIGHT,
                          intArrayOf(0xFF0EA5E9.toInt(), 0xFF8B5CF6.toInt())
                      ).apply { cornerRadius = dp(18).toFloat() }
                  )
                  setOnClickListener { onClick() }
              }

              private fun glassButton(label: String, onClick: () -> Unit) = Button(this).apply {
                  text = label
                  isAllCaps = false
                  textSize = 15f
                  setTextColor(Color.WHITE)
                  stateListAnimator = null
                  background = ripple(glass(18))
                  setOnClickListener { onClick() }
              }

              override fun onCreate(savedInstanceState: Bundle?) {
                  super.onCreate(savedInstanceState)
                  window.statusBarColor = 0xFF070B1A.toInt()
                  window.navigationBarColor = 0xFF0A2233.toInt()

                  siteValue = text("", 22f, Color.WHITE, true)
                  sizeValue = text("", 15f, 0xCCFFFFFF.toInt())
                  winValue = text("", 15f, 0xCCFFFFFF.toInt())
                  status = text("", 13f, 0xFFFFFFFF.toInt()).apply {
                      setPadding(dp(14), dp(6), dp(14), dp(6))
                      background = glass(30)
                  }

                  val iconSeek = seek(
                      110, prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE) - 40,
                      onChange = { p ->
                          sizeValue.text = "حجم الأيقونة: ${p + 40} dp"
                          FloatingService.instance?.setSize(p + 40)
                      },
                      onStop = { p -> prefs.edit().putInt(FloatingService.KEY_SIZE, p + 40).apply() }
                  )
                  val winSeek = seek(
                      70, prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN) - 30,
                      onChange = { p ->
                          winValue.text = "حجم النافذة: ${p + 30}% من الشاشة"
                          FloatingService.instance?.setWindowPercent(p + 30)
                      },
                      onStop = { p -> prefs.edit().putInt(FloatingService.KEY_WIN, p + 30).apply() }
                  )

                  val content = LinearLayout(this).apply {
                      orientation = LinearLayout.VERTICAL
                      setPadding(dp(20), dp(56), dp(20), dp(28))
                  }
                  content.addView(text("Float Launcher", 30f, Color.WHITE, true))
                  content.addView(text("نافذة عائمة فوق أي لعبة", 14f, 0xB3FFFFFF.toInt()))
                  content.addView(gap(14))
                  content.addView(status, LinearLayout.LayoutParams(
                      LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                  ).apply { gravity = Gravity.START })
                  content.addView(gap(18))

                  content.addView(card(
                      text("الموقع", 13f, 0x99FFFFFF.toInt()),
                      siteValue,
                      gap(10),
                      glassButton("اختر الموقع") { pickSite() }
                  ))
                  content.addView(gap(14))
                  content.addView(card(sizeValue, gap(6), iconSeek))
                  content.addView(gap(14))
                  content.addView(card(winValue, gap(6), winSeek))
                  content.addView(gap(22))

                  content.addView(primaryButton("تشغيل الأيقونة العائمة") { startFloating() },
                      LinearLayout.LayoutParams(-1, dp(54)))
                  content.addView(gap(10))
                  content.addView(glassButton("إيقاف") {
                      stopService(Intent(this, FloatingService::class.java))
                      refreshStatus()
                  }, LinearLayout.LayoutParams(-1, dp(50)))
                  content.addView(gap(18))
                  content.addView(text(
                      "نصائح: اضغط مطولاً على دائرة التكبير لإظهار الأزرار • اضغط مطولاً على النافذة لتحريكها • اضغط 4 ثوان على الأيقونة لإغلاق التطبيق",
                      12f, 0x99FFFFFF.toInt()
                  ))

                  // خلفية متدرجة مع توهجات ملوّنة تعطي عمقاً للزجاج
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
              }

              override fun onResume() {
                  super.onResume()
                  refreshStatus()
              }

              private fun refreshStatus() {
                  val on = FloatingService.instance != null
                  status.text = if (on) "●  الخدمة تعمل" else "○  الخدمة متوقفة"
                  status.setTextColor(if (on) 0xFF4ADE80.toInt() else 0xCCFFFFFF.toInt())
              }

              private fun refreshLabels() {
                  val idx = prefs.getInt(FloatingService.KEY_SITE, 0).coerceIn(0, FloatingService.SITES.lastIndex)
                  siteValue.text = FloatingService.SITES[idx].first
                  sizeValue.text = "حجم الأيقونة: ${prefs.getInt(FloatingService.KEY_SIZE, FloatingService.DEFAULT_SIZE)} dp"
                  winValue.text = "حجم النافذة: ${prefs.getInt(FloatingService.KEY_WIN, FloatingService.DEFAULT_WIN)}% من الشاشة"
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

              private fun startFloating() {
                  if (!Settings.canDrawOverlays(this)) {
                      Toast.makeText(this, "فعّل «العرض فوق التطبيقات الأخرى» ثم ارجع", Toast.LENGTH_LONG).show()
   
