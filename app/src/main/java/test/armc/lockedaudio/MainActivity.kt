package test.armc.lockedaudio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One screen: two start buttons, Stop, and the on-screen log. No data leaves the phone. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private var pendingMode: String? = null

    private val refresh = object : Runnable {
        override fun run() {
            logView.text = TestLog.read(this@MainActivity).ifEmpty { "(log is empty)" }
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            fitsSystemWindows = true
        }

        root.addView(TextView(this).apply {
            text = "Locked-screen audio test.\nPress a button, lock the phone, put it in your pocket. " +
                "A cue plays every minute for 10 minutes. Unlock afterwards and read the log below."
            textSize = 15f
        })
        root.addView(makeButton("Speak a cue every minute for 10 minutes") { start(CueService.MODE_SPEECH) })
        root.addView(makeButton("Play a short clip every minute for 10 minutes") { start(CueService.MODE_CLIP) })
        root.addView(makeButton("Stop") {
            startService(Intent(this, CueService::class.java).setAction(CueService.ACTION_STOP))
        })
        root.addView(makeButton("Clear log") { TestLog.clear(this) })

        logView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.START
        }
        root.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun start(mode: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMode = mode
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        launch(mode)
    }

    private fun launch(mode: String) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        TestLog.add(this, "Start pressed ($mode); notification permission granted: $granted")
        startForegroundService(Intent(this, CueService::class.java).putExtra(CueService.EXTRA_MODE, mode))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        pendingMode?.let { launch(it) }
        pendingMode = null
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
