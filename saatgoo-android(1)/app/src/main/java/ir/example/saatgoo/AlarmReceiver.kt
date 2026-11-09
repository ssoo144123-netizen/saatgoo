package ir.example.saatgoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Fires when the alarm time arrives: speaks the time and re-schedules for tomorrow. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val speaker = TimeSpeaker(context)
        Handler(Looper.getMainLooper()).postDelayed({
            speaker.speakNow(hour12 = true)
        }, 1500)
        try {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play()
        } catch (_: Exception) { }
        Toast.makeText(context, "هشدار ساعت‌گو", Toast.LENGTH_LONG).show()

        // Re-arm for the same time tomorrow.
        val prefs = context.getSharedPreferences("saatgoo", Context.MODE_PRIVATE)
        val h = prefs.getInt("alarm_h", -1)
        val m = prefs.getInt("alarm_m", -1)
        if (h >= 0 && m >= 0) AlarmScheduler.schedule(context, h, m)
    }
}
