package pl.licznik.km

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

/**
 * Bardzo słaba, krótka wibracja po naciśnięciu przycisku głośności – żeby w kieszeni
 * czuć, że policzono. Kobieta = krótkie stuknięcie, mężczyzna = jedna dłuższa wibracja.
 * Wibracja nie blokuje wątku – system wykonuje ją w tle.
 */
object Feedback {
    // Siła 1–255. Jeśli wibracja będzie za słaba / za mocna, zmień tylko te liczby.
    private const val WOMAN_STRENGTH = 50
    private const val MAN_STRENGTH = 130

    // Czasy w ms: [przerwa, wibracja]
    private val womanTimings = longArrayOf(0, 15)
    private val womanAmplitudes = intArrayOf(0, WOMAN_STRENGTH)
    private val manTimings = longArrayOf(0, 80)
    private val manAmplitudes = intArrayOf(0, MAN_STRENGTH)

    fun woman(context: Context) = vibrate(context, womanTimings, womanAmplitudes)

    fun man(context: Context) = vibrate(context, manTimings, manAmplitudes)

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                // Nowa wibracja przerywa poprzednią, więc szybkie klikanie się nie "kolejkuje".
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (e: Exception) {
            // Brak wibracji nie może zatrzymać liczenia.
        }
    }
}

fun showToast(context: Context, text: String) {
    try {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
    }
}
