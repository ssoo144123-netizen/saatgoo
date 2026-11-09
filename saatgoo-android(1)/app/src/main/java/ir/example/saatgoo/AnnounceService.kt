package ir.example.saatgoo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Calendar

/** Foreground service that announces the time every N minutes. */
class AnnounceService : Service() {

    private lateinit var speaker: TimeSpeaker
    private val handler = Handler(Looper.getMainLooper())
    private var intervalMin = 60
    private var lastAnnounced = -1

    private val ticker = object : Runnable {
        override fun run() {
            val now = Calendar.getInstance()
            val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (now.get(Calendar.MINUTE) % intervalMin == 0 && minuteOfDay != lastAnnounced) {
                lastAnnounced = minuteOfDay
                speaker.speakNow(hour12 = false)
            }
            // Re-check at the next whole minute boundary.
            val delay = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
            handler.postDelayed(this, delay + 50)
        }
    }

    override fun onCreate() {
        super.onCreate()
        speaker = TimeSpeaker(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        intervalMin = intent?.getIntExtra(EXTRA_INTERVAL, 60)?.coerceAtLeast(1) ?: 60
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "اعلام ساعت", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("ساعت‌گو فعال است")
            .setContentText("اعلام هر $intervalMin دقیقه")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "saatgoo_announce"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "ir.example.saatgoo.STOP"
        const val EXTRA_INTERVAL = "interval"

        fun start(context: Context, intervalMin: Int) {
            val i = Intent(context, AnnounceService::class.java)
                .putExtra(EXTRA_INTERVAL, intervalMin)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AnnounceService::class.java).setAction(ACTION_STOP))
        }
    }
}
