package com.example.mi12liteledtester

import android.Manifest
import android.app.Activity
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
    private fun button(text: String, click: () -> Unit) {
        content.addView(Button(this).apply {
            this.text = text; setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }
    private fun log(message: String) { logView.append("$message\n") }

    private fun scan() {
        turnOff()
        while (content.childCount > 5) content.removeViewAt(5)
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
                    if (front && flash) button("TEST FRONT CAMERA $id (3s)") { test(id) }
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
    override fun onPause() { turnOff(); super.onPause() }
    override fun onDestroy() { turnOff(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
        else log("Camera permission denied; grant it in app settings and rescan.")
    }
}
