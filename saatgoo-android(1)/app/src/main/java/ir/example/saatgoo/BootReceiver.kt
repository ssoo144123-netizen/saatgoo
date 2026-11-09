package ir.example.saatgoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the alarm and periodic announcements after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("saatgoo", Context.MODE_PRIVATE)
        val h = prefs.getInt("alarm_h", -1)
        val m = prefs.getInt("alarm_m", -1)
        if (h >= 0 && m >= 0) AlarmScheduler.schedule(context, h, m)
        if (prefs.getBoolean("periodic", false)) {
            AnnounceService.start(context, prefs.getInt("interval", 60))
        }
    }
}
