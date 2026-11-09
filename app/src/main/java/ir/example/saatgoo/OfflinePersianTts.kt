package ir.example.saatgoo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors

/**
 * Offline Persian TTS using sherpa-onnx with Piper VITS models stored in assets.
 * Needs the sherpa-onnx Android library (see README).
 */
object OfflinePersianTts {

    // Piper Persian voices. Folder names must match the files extracted into app/src/main/assets.
    const val MALE_MODEL = "vits-piper-fa_IR-amir-medium"
    const val FEMALE_MODEL = "vits-piper-fa_IR-ganji-medium"

    private val worker = Executors.newSingleThreadExecutor()
    private val engines = HashMap<String, OfflineTts>()

    fun isInstalled(context: Context, modelDir: String): Boolean = try {
        context.assets.list(modelDir)?.contains("model.onnx") == true
    } catch (_: Exception) { false }

    fun speak(context: Context, text: String, male: Boolean, speed: Float = 1.0f) {
        val dir = if (male) MALE_MODEL else FEMALE_MODEL
        val app = context.applicationContext
        worker.execute {
            try {
                val tts = engines.getOrPut(dir) { load(app, dir) }
                val audio = tts.generate(text, 0, speed)
                play(audio.samples, audio.sampleRate)
            } catch (_: Throwable) {
                // Model missing or native library not present: caller falls back to system TTS.
            }
        }
    }

    private fun load(context: Context, dir: String): OfflineTts {
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$dir/model.onnx",
                    tokens = "$dir/tokens.txt",
                    dataDir = "$dir/espeak-ng-data"
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        return OfflineTts(context.assets, config)
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        Thread.sleep(samples.size * 1000L / sampleRate + 200)
        track.release()
    }
}
