package ir.example.saatgoo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Calendar
import java.util.Locale

/** Builds Persian time text and speaks it with the system TTS engine. */
class TimeSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("fa", "IR"))
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) tts.language = Locale.getDefault()
            ready = true
            pending?.let { speak(it) }
            pending = null
        }
    }

    fun speakNow(hour12: Boolean = false) {
        val text = timeText(Calendar.getInstance(), hour12)
        if (ready) speak(text) else pending = text
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saatgoo-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts.stop(); tts.shutdown()
    }

    companion object {
        fun timeText(cal: Calendar, hour12: Boolean = false): String {
            var h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            var part = ""
            if (hour12) {
                part = when {
                    h < 5 -> "نیمه‌شب"
                    h < 12 -> "صبح"
                    h < 18 -> "بعدازظهر"
                    else -> "شب"
                }
                h = h % 12
                if (h == 0) h = 12
            }
            val minutePart = if (m == 0) "" else " و $m دقیقه"
            return "ساعت $h$minutePart${if (part.isEmpty()) "" else " $part"}"
        }
    }
}
