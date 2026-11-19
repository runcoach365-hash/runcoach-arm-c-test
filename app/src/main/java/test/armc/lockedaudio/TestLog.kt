package test.armc.lockedaudio

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain text log kept in the app's private storage. Shown on screen; never leaves the phone. */
object TestLog {
    private const val FILE = "cue_log.txt"

    fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date(ms))

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val line = clock(System.currentTimeMillis()) + "  " + msg + "\n"
        try {
            ctx.applicationContext.openFileOutput(FILE, Context.MODE_APPEND).use {
                it.write(line.toByteArray())
            }
        } catch (e: Exception) {
            // Logging must never crash the test.
        }
    }

    @Synchronized
    fun read(ctx: Context): String = try {
        ctx.applicationContext.openFileInput(FILE).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.applicationContext.deleteFile(FILE)
    }
}
