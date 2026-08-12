package com.sazanx.mouseconfigurator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.OutputStream
import java.util.Locale

// Configuration Data Model
data class Config(
    var pointer: Float = 1.0f,
    var x: Float = 1.0f,
    var y: Float = 1.0f,
    var acceleration: Boolean = false,
    var accel: Float = 0.0f,
    var smoothing: Float = 0.35f,
    var curve: String = "Linear",
    var raw: Boolean = true,
    var scroll: Float = 1.0f,
    var invertY: Boolean = false
)

// ==========================================
// 1. MAIN ACTIVITY (UI, Tester & Controller)
// ==========================================
class MainActivity : Activity() {
    private val cfg = Config()
    private lateinit var status: TextView
    private lateinit var log: TextView
    private var lastNs = 0L
    private var events = 0
    private var windowStart = 0L
    private val shizukuPermissionCode = 1004

    private fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    private fun isShizukuGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
            checkSystemOverlayPermission()
        } catch (e: Exception) {
            val tv = TextView(this)
            tv.text = "Error starting app:\n${e.localizedMessage}"
            tv.setTextColor(Color.RED)
            tv.setPadding(32, 32, 32, 32)
            setContentView(tv)
        }
    }

    private fun checkSystemOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
            setBackgroundColor(Color.rgb(11, 15, 20))
        }

        val title = TextView(this).apply {
            text = "Mouse Configurator v4"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(title)

        status = TextView(this).apply {
            text = "Checking input + Shizuku…"
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }
        root.addView(status)

        val serviceControlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        
        val btnStart = Button(this).apply {
            text = "START GLOBAL STABILIZER"
            setOnClickListener { startMouseService() }
        }
        val btnStop = Button(this).apply {
            text = "STOP SERVICE"
            setOnClickListener { stopMouseService() }
        }

        serviceControlLayout.addView(btnStart, LinearLayout.LayoutParams(0, -2, 1f))
        serviceControlLayout.addView(btnStop, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(serviceControlLayout)

        val tabs = LinearLayout(this)
        val configBtn = Button(this).apply { text = "PC SETTINGS" }
        val testBtn = Button(this).apply { text = "INPUT TEST" }
        val deviceBtn = Button(this).apply { text = "DEVICES" }
        tabs.addView(configBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(testBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(deviceBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun showConfig() {
            body.removeAllViews()
            addHeader(body, "PC-style mouse behavior")
            addSeek(body, "Pointer speed", 0.1f, 3f, cfg.pointer) { cfg.pointer = it; updateServiceConfig() }
            addSeek(body, "X sensitivity", 0.1f, 3f, cfg.x) { cfg.x = it; updateServiceConfig() }
            addSeek(body, "Y sensitivity", 0.1f, 3f, cfg.y) { cfg.y = it; updateServiceConfig() }
            addSwitch(body, "Raw input mode", cfg.raw) { cfg.raw = it; updateServiceConfig() }
            addSwitch(body, "Mouse acceleration", cfg.acceleration) { cfg.acceleration = it; updateServiceConfig() }
            addSeek(body, "Acceleration strength", 0f, 1f, cfg.accel) { cfg.accel = it; updateServiceConfig() }
            addSeek(body, "Smoothing / filter", 0f, 1f, cfg.smoothing) { cfg.smoothing = it; updateServiceConfig() }
            addHeader(body, "Response curve")
            val rg = RadioGroup(this@MainActivity)
            listOf("Linear", "Soft", "Windows-like", "Aggressive").forEach { name ->
                val r = RadioButton(this@MainActivity).apply { 
                    text = name
                    setTextColor(Color.WHITE)
                    isChecked = (name == cfg.curve) 
                }
                r.setOnClickListener { cfg.curve = name; updateServiceConfig() }
                rg.addView(r)
            }
            body.addView(rg)
            addSeek(body, "Scroll speed", 0.25f, 3f, cfg.scroll) { cfg.scroll = it; updateServiceConfig() }
            addSwitch(body, "Invert Y", cfg.invertY) { cfg.invertY = it; updateServiceConfig() }
            val note = TextView(this@MainActivity).apply {
                text = "Note: Global injection runs cross-app using the background Shizuku pipeline."
                setTextColor(Color.LTGRAY); textSize = 12f; setPadding(0, 16, 0, 16)
            }
            body.addView(note)
        }

        fun showTest() {
            body.removeAllViews()
            addHeader(body, "Raw input tester")
            val info = TextView(this@MainActivity).apply {
                text = "Move the mouse over this screen. Events reaching this app appear below."
                setTextColor(Color.WHITE)
            }
            body.addView(info)
            log = TextView(this@MainActivity).apply {
                text = "Waiting for mouse/keyboard…"
                setTextColor(Color.rgb(180, 220, 255))
                textSize = 12f
                setPadding(0, 16, 0, 16)
            }
            body.addView(log)
        }

        fun showDevices() {
            body.removeAllViews()
            addHeader(body, "Connected Input Devices")
            try {
                InputDevice.getDeviceIds()
                    .map { id -> InputDevice.getDevice(id) }
                    .filterNotNull()
                    .filter { dev -> (dev.sources and (InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_KEYBOARD)) != 0 }
                    .forEach { dev ->
                        val t = TextView(this@MainActivity).apply {
                            text = "${dev.name} (ID ${dev.id}) - sources 0x${Integer.toHexString(dev.sources)}"
                            setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 8, 0, 8)
                        }
                        body.addView(t)
                    }
            } catch (_: Throwable) {}

            if (body.childCount == 1) {
                addText(body, "No mouse/keyboard detected. Connect USB/Bluetooth device.")
            }

            addHeader(body, "Shizuku")
            val sh = when {
                !isShizukuRunning() -> "Not running"
                isShizukuGranted() -> "Running • permission granted"
                else -> "Running • permission required"
            }
            addText(body, sh)
            if (isShizukuRunning() && !isShizukuGranted()) {
                val b = Button(this@MainActivity).apply { text = "Request Shizuku permission" }
                b.setOnClickListener { try { Shizuku.requestPermission(shizukuPermissionCode) } catch (_: Throwable) {} }
                body.addView(b)
            }
        }

        configBtn.setOnClickListener { showConfig() }
        testBtn.setOnClickListener { showTest() }
        deviceBtn.setOnClickListener { showDevices() }

        setContentView(root)
        showConfig()
        status.text = if (isShizukuRunning()) "Shizuku: running" else "Shizuku: not running"
    }

    private fun addHeader(p: LinearLayout, s: String) {
        val t = TextView(this).apply {
            text = s; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(0, 18, 0, 8)
        }
        p.addView(t)
    }

    private fun addText(p: LinearLayout, s: String) {
        val t = TextView(this).apply { text = s; setTextColor(Color.LTGRAY); textSize = 13f; setPadding(0, 8, 0, 8) }
        p.addView(t)
    }

    private fun addSeek(p: LinearLayout, label: String, min: Float, max: Float, initial: Float, change: (Float) -> Unit) {
        val tv = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f }
        val sb = SeekBar(this)
        sb.max = 1000
        fun update(v: Int) {
            val x = min + (max - min) * (v / 1000f)
            tv.text = "$label  ${String.format(Locale.US, "%.2f", x)}"
            change(x)
        }
        sb.progress = ((initial - min) / (max - min) * 1000).toInt().coerceIn(0, 1000)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { update(v) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        update(sb.progress)
        p.addView(tv); p.addView(sb)
    }

    private fun addSwitch(p: LinearLayout, label: String, initial: Boolean, change: (Boolean) -> Unit) {
        val sw = Switch(this).apply { text = label; setTextColor(Color.WHITE); isChecked = initial }
        sw.setOnCheckedChangeListener { _, v -> change(v) }
        p.addView(sw)
    }

    private fun startMouseService() {
        if (!isShizukuRunning()) {
            Toast.makeText(this, "Shizuku must be running to enable global injection!", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MouseStabilizerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Global Mouse Stabilizer Started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopMouseService() {
        val intent = Intent(this, MouseStabilizerService::class.java)
        stopService(intent)
        Toast.makeText(this, "Service Stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceConfig() {
        MouseStabilizerService.currentConfig = cfg
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            val now = System.nanoTime()
            if (windowStart == 0L) windowStart = now
            events++
            val rate = if (now - windowStart >= 1_000_000_000L) {
                val r = events
                events = 0; windowStart = now; r
            } else 0
            
            val rawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) e.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else e.getAxisValue(MotionEvent.AXIS_X)
            val rawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else e.getAxisValue(MotionEvent.AXIS_Y)
            val wheel = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val dt = if (lastNs == 0L) 0.0 else (now - lastNs) / 1_000_000.0
            lastNs = now

            MouseStabilizerService.instance?.processAndInjectInput(rawX, rawY)

            if (::log.isInitialized) {
                log.text = "Raw: dx=${"%.2f".format(rawX)}  dy=${"%.2f".format(rawY)}  wheel=${"%.2f".format(wheel)}\nΔt=${"%.2f".format(dt)} ms  event-rate≈${if (rate > 0) rate else "measuring"} Hz\n\n" + log.text.take(3000)
            }
        }
        return super.dispatchGenericMotionEvent(e)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && ::log.isInitialized) {
            val d = InputDevice.getDevice(e.deviceId)
            log.text = "KEY ${KeyEvent.keyCodeToString(e.keyCode)} • ${d?.name ?: "Unknown"}\n\n" + log.text.take(3000)
        }
        return super.dispatchKeyEvent(e)
    }
}

// ==========================================
// 2. BACKGROUND SERVICE (Global Injector)
// ==========================================
class MouseStabilizerService : Service() {

    companion object {
        var instance: MouseStabilizerService? = null
        var currentConfig: Config = Config()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var process: Process? = null
    private var outputStream: OutputStream? = null

    private var smoothedX = 0f
    private var smoothedY = 0f
    private var remainderX = 0f
    private var remainderY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundServiceNotification()
        initShizukuShell()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "mouse_stabilizer_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mouse Precision Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mouse Stabilizer Active")
            .setContentText("Global input precision model active across all apps.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun initShizukuShell() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    process = Shizuku.newProcess(arrayOf("sh"), null, null)
                    outputStream = process?.outputStream
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun processAndInjectInput(rawDeltaX: Float, rawDeltaY: Float) {
        serviceScope.launch(Dispatchers.Default) {
            val cfg = currentConfig
            
            var adjustedX = rawDeltaX * cfg.pointer * cfg.x
            var adjustedY = rawDeltaY * cfg.pointer * cfg.y * (if (cfg.invertY) -1f else 1f)

            val alpha = (1.0f - cfg.smoothing).coerceIn(0.05f, 1.0f)
            smoothedX = (alpha * adjustedX) + ((1f - alpha) * smoothedX)
            smoothedY = (alpha * adjustedY) + ((1f - alpha) * smoothedY)

            val totalX = smoothedX + remainderX
            val totalY = smoothedY + remainderY

            val injectX = totalX.toInt()
            val injectY = totalY.toInt()

            remainderX = totalX - injectX
            remainderY = totalY - injectY

            if (injectX != 0 || injectY != 0) {
                sendShizukuCommand("input swipe 0 0 $injectX $injectY 1\n")
            }
        }
    }

    private fun sendShizukuCommand(cmd: String) {
        try {
            outputStream?.let {
                it.write(cmd.toByteArray())
                it.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        try {
            outputStream?.close()
            process?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
