package ir.example.saatgoo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Calendar
import java.util.Locale

/**
 * Speaks a Persian message: greeting, Persian date and time.
 * Voice gender ("male"/"female") and the greeting/date options are read from preferences.
 */
class TimeSpeaker(
    private val context: Context,
    private val onFarsiCheck: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val prefs = context.getSharedPreferences("saatgoo", Context.MODE_PRIVATE)
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val res = tts.setLanguage(Locale("fa", "IR"))
        val hasFarsi = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        if (!hasFarsi) tts.language = Locale.getDefault()
        onFarsiCheck?.invoke(hasFarsi)
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    fun speakNow(hour12: Boolean = false) {
        val text = buildMessage(Calendar.getInstance(), hour12)
        if (ready) speak(text) else pending = text
    }

    private fun speak(text: String) {
        val male = prefs.getString("gender", "female") == "male"
        if (prefs.getBoolean("offline", false)) {
            val reason = OfflinePersianTts.unavailableReason(context, male)
            if (reason == null) {
                OfflinePersianTts.speak(context, text, male)
                return
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, reason, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        applyGender()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saatgoo-${System.currentTimeMillis()}")
    }

    /** Male = lower pitch, female = higher pitch; also picks an installed voice whose name matches if any. */
    private fun applyGender() {
        val male = prefs.getString("gender", "female") == "male"
        tts.setPitch(if (male) 0.8f else 1.15f)
        tts.setSpeechRate(1.0f)
        val keyword = if (male) "male" else "female"
        val voice = try {
            tts.voices?.firstOrNull {
                val n = it.name.lowercase(Locale.ROOT)
                it.locale.language == "fa" && n.contains(keyword) && !(male && n.contains("female"))
            }
        } catch (_: Exception) { null }
        if (voice != null) tts.voice = voice
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun buildMessage(cal: Calendar, hour12: Boolean): String {
        val parts = mutableListOf<String>()
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)

        if (prefs.getBoolean("greeting", true)) parts += greeting(h24)

        if (prefs.getBoolean("date", true)) {
            val (jy, jm, jd) = gregorianToJalali(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            val weekday = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
            parts += "امروز $weekday $jd ${MONTHS[jm - 1]} $jy است"
        }

        var h = h24
        var part = ""
        if (hour12) {
            part = when {
                h < 5 -> "نیمه‌شب"
                h < 12 -> "صبح"
                h < 18 -> "بعدازظهر"
                else -> "شب"
            }
            h %= 12
            if (h == 0) h = 12
        }
        val minutePart = if (m == 0) "" else " و $m دقیقه"
        parts += "ساعت $h$minutePart${if (part.isEmpty()) "" else " $part"}"

        return parts.joinToString(". ") + "."
    }

    companion object {
        val WEEKDAYS = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
        val MONTHS = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )

        fun greeting(hour: Int): String = when (hour) {
            in 5..11 -> "صبح بخیر"
            in 12..16 -> "روز بخیر"
            in 17..20 -> "عصر بخیر"
            else -> "شب بخیر"
        }

        /** Gregorian to Persian (Jalali) date. Works on every Android version, no ICU needed. */
        fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
            val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            val gy2 = if (gm > 2) gy + 1 else gy
            var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
            var jy = -1595 + 33 * (days / 12053)
            days %= 12053
            jy += 4 * (days / 1461)
            days %= 1461
            if (days > 365) {
                jy += (days - 1) / 365
                days = (days - 1) % 365
            }
            val jm: Int
            val jd: Int
            if (days < 186) {
                jm = 1 + days / 31
                jd = 1 + days % 31
            } else {
                jm = 7 + (days - 186) / 30
                jd = 1 + (days - 186) % 30
            }
            return Triple(jy, jm, jd)
        }
    }
}
