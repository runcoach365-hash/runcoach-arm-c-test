package test.armc.lockedaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Foreground service (type: media playback) that gives one cue a minute for 10 minutes,
 * either spoken by the phone's own voice or as a short chime clip.
 * Every cue is logged with its due time, the time it fired, and the phone's power state.
 */
class CueService : Service() {

    companion object {
        const val ACTION_STOP = "test.armc.lockedaudio.STOP"
        const val EXTRA_MODE = "mode"
        const val MODE_SPEECH = "speech"
        const val MODE_CLIP = "clip"
        const val TOTAL_CUES = 10
        const val INTERVAL_MS = 60_000L
        private const val CHANNEL_ID = "cues"
        private const val NOTIFICATION_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mode = MODE_SPEECH
    private var running = false
    private var startWall = 0L
    private var startUptime = 0L

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TestLog.add(this, "Stop pressed")
            finish()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_SPEECH

        showForegroundNotification()
        startWall = System.currentTimeMillis()
        startUptime = SystemClock.uptimeMillis()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        TestLog.add(this, "---- START mode=$mode, phone=${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        TestLog.add(this, "Battery optimisation ignored for this app: ${pm.isIgnoringBatteryOptimizations(packageName)}; battery saver on: ${pm.isPowerSaveMode}")

        if (mode == MODE_SPEECH) startSpeechEngine()
        scheduleCue(1)
        return START_NOT_STICKY
    }

    private fun showForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Cue test", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Cue test running")
            .setContentText("A cue every minute for 10 minutes")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun startSpeechEngine() {
        tts = TextToSpeech(applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setAudioAttributes(attrs)
                val langResult = engine.setLanguage(Locale.getDefault())
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        TestLog.add(this@CueService, "Speech started ($utteranceId)")
                    }

                    override fun onDone(utteranceId: String?) {
                        TestLog.add(this@CueService, "Speech finished ($utteranceId)")
                    }

                    @Deprecated("Required by the abstract class")
                    override fun onError(utteranceId: String?) {
                        TestLog.add(this@CueService, "SPEECH ERROR ($utteranceId)")
                    }
                })
                ttsReady = true
                TestLog.add(this, "Speech engine ready (language result $langResult)")
            } else {
                TestLog.add(this, "SPEECH ENGINE FAILED TO START (status $status)")
            }
        }
    }

    private fun scheduleCue(index: Int) {
        handler.postAtTime({ fireCue(index) }, startUptime + index * INTERVAL_MS)
    }

    private fun fireCue(index: Int) {
        if (!running) return
        val due = startWall + index * INTERVAL_MS
        val now = System.currentTimeMillis()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        TestLog.add(
            this,
            "Cue $index due ${TestLog.clock(due)}, fired ${TestLog.clock(now)}, late ${(now - due) / 1000} s; " +
                "screen on: ${pm.isInteractive}; doze: ${pm.isDeviceIdleMode}"
        )
        if (mode == MODE_SPEECH) speak(index) else playClip(index)
        if (index < TOTAL_CUES) {
            scheduleCue(index + 1)
        } else {
            handler.postDelayed({
                TestLog.add(this, "---- FINISHED all $TOTAL_CUES cues")
                finish()
            }, 15_000L)
        }
    }

    private fun speak(index: Int) {
        val engine = tts
        if (!ttsReady || engine == null) {
            TestLog.add(this, "Cue $index NOT SPOKEN: speech engine not ready")
            return
        }
        val text = "Cue $index of $TOTAL_CUES. $index minutes in. Keep a steady pace."
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue$index")
        TestLog.add(this, "Cue $index speak() returned $result (0 = accepted)")
    }

    private fun playClip(index: Int) {
        val player = MediaPlayer.create(this, R.raw.cue_clip, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
        if (player == null) {
            TestLog.add(this, "Cue $index CLIP COULD NOT BE CREATED")
            return
        }
        player.setOnCompletionListener {
            TestLog.add(this, "Clip $index finished")
            it.release()
        }
        player.setOnErrorListener { mp, what, extra ->
            TestLog.add(this, "CLIP ERROR cue $index (what $what, extra $extra)")
            mp.release()
            true
        }
        player.start()
        TestLog.add(this, "Clip $index started")
    }

    private fun finish() {
        running = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        TestLog.add(this, "App swiped away from Recents (service still running: $running)")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        TestLog.add(this, "Service destroyed")
        tts?.shutdown()
        super.onDestroy()
    }
}
