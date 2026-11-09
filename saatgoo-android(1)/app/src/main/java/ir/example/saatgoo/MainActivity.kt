package ir.example.saatgoo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ir.example.saatgoo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var speaker: TimeSpeaker
    private val prefs by lazy { getSharedPreferences("saatgoo", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        speaker = TimeSpeaker(this) { hasFarsi -> if (!hasFarsi) promptInstallFarsiVoice() }

        askNotificationPermission()

        val intervals = listOf(5, 15, 30, 60)
        b.intervalSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("هر ۵ دقیقه", "هر ۱۵ دقیقه", "هر ۳۰ دقیقه", "هر ساعت")
        )
        b.intervalSpinner.setSelection(intervals.indexOf(prefs.getInt("interval", 60)).coerceAtLeast(0))

        b.periodicSwitch.isChecked = prefs.getBoolean("periodic", false)
        b.periodicSwitch.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("periodic", on).apply()
            if (on) {
                val interval = intervals[b.intervalSpinner.selectedItemPosition]
                prefs.edit().putInt("interval", interval).apply()
                AnnounceService.start(this, interval)
            } else {
                AnnounceService.stop(this)
            }
        }

        b.sayNowButton.setOnClickListener { speaker.speakNow(hour12 = false) }

        b.genderGroup.check(if (prefs.getString("gender", "female") == "male") b.radioMale.id else b.radioFemale.id)
        b.genderGroup.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("gender", if (id == b.radioMale.id) "male" else "female").apply()
        }
        b.offlineCheck.isChecked = prefs.getBoolean("offline", false)
        b.offlineCheck.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("offline", on).apply()
            if (on) {
                OfflinePersianTts.unavailableReason(this, male = true)?.let {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
        }
        b.greetingCheck.isChecked = prefs.getBoolean("greeting", true)
        b.greetingCheck.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("greeting", on).apply() }
        b.dateCheck.isChecked = prefs.getBoolean("date", true)
        b.dateCheck.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("date", on).apply() }

        val h = prefs.getInt("alarm_h", -1)
        val m = prefs.getInt("alarm_m", -1)
        if (h >= 0) {
            b.timePicker.hour = h
            b.timePicker.minute = m
        }
        b.setAlarmButton.setOnClickListener {
            val hh = b.timePicker.hour
            val mm = b.timePicker.minute
            prefs.edit().putInt("alarm_h", hh).putInt("alarm_m", mm).apply()
            AlarmScheduler.schedule(this, hh, mm)
            Toast.makeText(this, "هشدار برای %02d:%02d تنظیم شد".format(hh, mm), Toast.LENGTH_SHORT).show()
        }
        b.cancelAlarmButton.setOnClickListener {
            prefs.edit().remove("alarm_h").remove("alarm_m").apply()
            AlarmScheduler.cancel(this)
            Toast.makeText(this, "هشدار لغو شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptInstallFarsiVoice() {
        Toast.makeText(this, "صدای فارسی روی این گوشی نصب نیست. نصب آن را باز می‌کنیم.", Toast.LENGTH_LONG).show()
        try {
            startActivity(android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        } catch (_: Exception) {
            Toast.makeText(this, "در تنظیمات گوشی، بخش Text-to-speech را باز کن و بسته‌ی فارسی را نصب کن.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (isFinishing) speaker.shutdown()
        super.onDestroy()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
