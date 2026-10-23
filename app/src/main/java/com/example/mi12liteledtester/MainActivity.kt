package com.example.mi12liteledtester

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
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
        label("Mi 12 Lite • Front LED Tester", 24)
        label("Each test switches off automatically after 3 seconds. Close other camera apps first.", 15)
        button("Rescan cameras") { scan() }
        button("TURN OFF") { turnOff() }
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

    private fun scan() {
        turnOff()
        cameraButtons.forEach { content.removeView(it) }
        cameraButtons.clear()
        logView.text = ""
        try {
            val ids = manager.cameraIdList
            log("Camera IDs: ${ids.joinToString()}")
            ids.forEach { id ->
                try {
                    val chars = manager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val front = facing == CameraCharacteristics.LENS_FACING_FRONT
                    log("ID $id: ${if (front) "FRONT" else "OTHER"}, flash=$flash")
                    if (front && flash) cameraButtons.add(button("TEST FRONT CAMERA $id (3s)") { test(id) })
                } catch (e: Exception) { log("ID $id scan error: ${e.javaClass.simpleName}: ${e.message}") }
            }
        } catch (e: Exception) { log("Scan failed: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun test(id: String) {
        turnOff()
        try {
            manager.setTorchMode(id, true)
            activeId = id
            log("ID $id: setTorchMode(true) SUCCESS — check LEDs physically")
            val task = Runnable { turnOff() }
            offTask = task
            handler.postDelayed(task, 3000)
        } catch (e: Exception) {
            log("ID $id: FAILED ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun turnOff() {
        offTask?.let { handler.removeCallbacks(it) }
        offTask = null
        val id = activeId ?: return
        activeId = null
        try { manager.setTorchMode(id, false); log("ID $id: torch OFF") }
        catch (e: Exception) { log("ID $id OFF error: ${e.javaClass.simpleName}: ${e.message}") }
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
