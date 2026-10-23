package com.example.mi12liteledtester

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Build
import android.widget.SeekBar
import rikka.shizuku.Shizuku
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var manager: CameraManager
    private lateinit var content: LinearLayout
    private lateinit var logView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var activeId: String? = null
    private var offTask: Runnable? = null
    private val permissionCode = 42
    private var selectedId: String? = null
    private var maxStrength = 1
    private var desiredStrength = 1
    private var autoOffSeconds = 120
    private lateinit var statusLabel: TextView
    private lateinit var strengthLabel: TextView
    private lateinit var strengthSlider: SeekBar
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == selectedId) {
                runOnUiThread {
                    statusLabel.text = "Camera $cameraId: unavailable (another camera user may be active)"
                    if (activeId == cameraId) { activeId = null; cancelTimeout() }
                    log("Torch unavailable for camera $cameraId")
                }
            }
        }
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == selectedId) {
                runOnUiThread {
                    statusLabel.text = "Camera $cameraId: ${if (enabled) "ON" else "OFF"}"
                    if (!enabled && activeId == cameraId) { activeId = null; cancelTimeout() }
                }
            }
        }
    }
    private val shizukuRequestCode = 43
    private val cameraButtons = mutableListOf<Button>()
    private var remote: ILedShellService? = null
    private var shizukuBound = false
    private val shizukuArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(this, LedShellService::class.java))
            .daemon(false).processNameSuffix("ledshell").version(1)
    }
    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote = ILedShellService.Stub.asInterface(binder)
            shizukuBound = true
            log("Shizuku shell service connected.")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            remote = null
            shizukuBound = false
            log("Shizuku shell service disconnected.")
        }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuRequestCode) {
            log(if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku permission granted." else "Shizuku permission denied.")
            if (grantResult == PackageManager.PERMISSION_GRANTED) bindShizuku()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        scroll.addView(content)
        setContentView(scroll)
        label("Mi 12 Lite • Front LED Controller v2", 24)
        label("Both selfie LEDs • Camera2 mode • no root. Lighting stops when app goes into background.", 14)
        statusLabel = TextView(this).apply { text = "Camera: scanning"; textSize = 17f; setTextColor(Color.WHITE); setPadding(0, 14, 0, 14) }
        content.addView(statusLabel)
        button("LEDs ON") { turnOn() }
        button("LEDs OFF") { turnOff() }
        strengthLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; text = "Brightness: checking support" }
        content.addView(strengthLabel)
        strengthSlider = SeekBar(this).apply {
            max = 1
            progress = 1
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    desiredStrength = progress + 1
                    updateStrengthLabel()
                    if (activeId != null) applyStrength()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        content.addView(strengthSlider)
        label("Auto-off safety timer", 15)
        button("30 seconds") { autoOffSeconds = 30; log("Safety timer: 30 seconds"); restartTimeout() }
        button("2 minutes (default)") { autoOffSeconds = 120; log("Safety timer: 2 minutes"); restartTimeout() }
        button("5 minutes") { autoOffSeconds = 300; log("Safety timer: 5 minutes"); restartTimeout() }
        button("Rescan front cameras") { scan() }
        logView = TextView(this).apply {
            setTextColor(Color.rgb(147, 197, 253))
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }
        content.addView(logView)
        button("SHIZUKU: Connect / Request permission") { connectShizuku() }
        button("SHIZUKU: Read-only LED diagnostics") { shizukuDiagnostics() }
        try { Shizuku.addRequestPermissionResultListener(permissionListener) }
        catch (e: Exception) { log("Shizuku initialization: ${e.message}") }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
        else scan()
        manager.registerTorchCallback(torchCallback, handler)
    }

    private fun label(text: String, size: Int) {
        content.addView(TextView(this).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        })
    }
    private fun button(text: String, click: () -> Unit): Button {
        val newButton = Button(this).apply {
            this.text = text; setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        content.addView(newButton)
        return newButton
    }
    private fun log(message: String) { logView.append("$message\n") }

    private fun updateStrengthLabel() {
        strengthLabel.text = if (maxStrength > 1)
            "Brightness: level $desiredStrength / $maxStrength (Camera2)"
        else "Brightness: fixed by camera driver (strength control unavailable)"
    }

    private fun scan() {
        turnOff()
        cameraButtons.forEach { content.removeView(it) }
        cameraButtons.clear()
        try {
            val candidates = manager.cameraIdList.filter { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT &&
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            selectedId = if (candidates.contains("1")) "1" else candidates.firstOrNull()
            if (selectedId == null) {
                statusLabel.text = "No front camera with torch available"
                log("No front-facing camera reporting flash support")
                return
            }
            val id = selectedId!!
            maxStrength = if (Build.VERSION.SDK_INT >= 33)
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            else 1
            maxStrength = maxStrength.coerceAtLeast(1)
            desiredStrength = 1
            strengthSlider.max = maxStrength - 1
            strengthSlider.progress = 0
            strengthSlider.isEnabled = maxStrength > 1
            updateStrengthLabel()
            statusLabel.text = "Front camera $id selected • LEDs OFF"
            log("Front cameras: ${candidates.joinToString()}, selected=$id, maxStrength=$maxStrength")
            candidates.filter { it != id }.forEach { otherId ->
                cameraButtons.add(button("Select front camera $otherId") {
                    selectedId = otherId
                    scanSelected(otherId)
                })
            }
        } catch (e: Exception) {
            log("Camera scan failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun scanSelected(id: String) {
        turnOff()
        try {
            selectedId = id
            maxStrength = if (Build.VERSION.SDK_INT >= 33)
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            else 1
            maxStrength = maxStrength.coerceAtLeast(1)
            desiredStrength = 1
            strengthSlider.max = maxStrength - 1
            strengthSlider.progress = 0
            strengthSlider.isEnabled = maxStrength > 1
            updateStrengthLabel()
            statusLabel.text = "Front camera $id selected • LEDs OFF"
        } catch (e: Exception) { log("Selection failed: ${e.message}") }
    }

    private fun turnOn() {
        val id = selectedId
        if (id == null) { log("No front camera selected. Rescan first."); return }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
            return
        }
        if (activeId == id) { log("LEDs already ON"); return }
        try {
            if (Build.VERSION.SDK_INT >= 33 && maxStrength > 1) {
                manager.turnOnTorchWithStrengthLevel(id, desiredStrength)
            } else {
                manager.setTorchMode(id, true)
            }
            activeId = id
            statusLabel.text = "Front LEDs ON • camera $id"
            log("LEDs ON: camera=$id strength=$desiredStrength / $maxStrength")
            restartTimeout()
        } catch (e: Exception) {
            statusLabel.text = "Unable to activate LED: camera may be occupied"
            log("ON failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun applyStrength() {
        val id = activeId ?: return
        if (Build.VERSION.SDK_INT < 33 || maxStrength <= 1) return
        try {
            manager.turnOnTorchWithStrengthLevel(id, desiredStrength)
            log("Brightness set to $desiredStrength / $maxStrength")
        } catch (e: Exception) {
            log("Brightness rejected by device: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun cancelTimeout() {
        offTask?.let { handler.removeCallbacks(it) }
        offTask = null
    }

    private fun restartTimeout() {
        cancelTimeout()
        if (activeId == null) return
        val task = Runnable {
            log("Safety timeout reached")
            turnOff()
        }
        offTask = task
        handler.postDelayed(task, autoOffSeconds * 1000L)
    }

    private fun turnOff() {
        cancelTimeout()
        val id = activeId ?: return
        activeId = null
        try {
            manager.setTorchMode(id, false)
            statusLabel.text = "Front LEDs OFF"
            log("LEDs OFF")
        } catch (e: Exception) {
            statusLabel.text = "Torch off command failed"
            log("OFF failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                log("Shizuku not running. Start it via Wireless Debugging first.")
                return
            }
            if (Shizuku.isPreV11()) { log("Shizuku v11+ required."); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) log("Permission denied previously; enable app in Shizuku.")
                else Shizuku.requestPermission(shizukuRequestCode)
                return
            }
            bindShizuku()
        } catch (e: Exception) { log("Shizuku error: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun bindShizuku() {
        if (shizukuBound) { log("Shizuku already connected."); return }
        try {
            Shizuku.bindUserService(shizukuArgs, shizukuConnection)
            log("Shizuku shell service binding requested.")
        } catch (e: Exception) { log("Shizuku bind failed: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun shizukuDiagnostics() {
        val service = remote
        if (service == null) { log("Connect Shizuku first and retry."); return }
        log("Reading LED interfaces with shell identity...")
        Thread {
            val result = try { service.diagnostics() }
            catch (e: Exception) { "Diagnostics failed: ${e.javaClass.simpleName}: ${e.message}" }
            runOnUiThread { log(result) }
        }.start()
    }

    override fun onPause() { turnOff(); super.onPause() }
    override fun onDestroy() {
        turnOff()
        manager.unregisterTorchCallback(torchCallback)
        try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (_: Exception) {}
        if (shizukuBound) {
            try { Shizuku.unbindUserService(shizukuArgs, shizukuConnection, true) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
        else log("Camera permission denied; grant it in app settings and rescan.")
    }
}
