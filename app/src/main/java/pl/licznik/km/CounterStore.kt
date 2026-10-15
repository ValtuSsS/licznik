package pl.licznik.km

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** startedAt = moment pierwszego kliknięcia "+" w tej sesji (0 = sesja jeszcze się nie zaczęła). */
data class Counts(val women: Int = 0, val men: Int = 0, val startedAt: Long = 0L) {
    val total: Int get() = women + men
}

/**
 * Jedno źródło prawdy dla liczników – używa go ekran i usługa w tle.
 *
 * Po każdej zmianie aktualny stan jest od razu zapisywany do pliku (AtomicFile:
 * zapis albo udaje się w całości, albo zostaje poprzednia wersja). Zapis odbywa się
 * w osobnym wątku, więc nigdy nie blokuje interfejsu.
 */
object CounterStore {
    private const val TAG = "CounterStore"
    private const val STATE_FILE = "stan_licznikow.txt"
    private const val MAX_VALUE = 999_999

    private val counts = MutableStateFlow(Counts())
    val state: StateFlow<Counts> = counts.asStateFlow()

    private val writer = Executors.newSingleThreadExecutor()

    @Volatile
    private var file: AtomicFile? = null

    /** Wczytuje zapisany stan. Można wołać wiele razy – zadziała tylko pierwszy raz. */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = AtomicFile(File(context.applicationContext.filesDir, STATE_FILE))
        counts.value = load(f)
        file = f
    }

    fun addWoman() {
        change { it.started().copy(women = (it.women + 1).coerceAtMost(MAX_VALUE)) }
    }

    fun removeWoman() {
        change { it.copy(women = (it.women - 1).coerceAtLeast(0)) }
    }

    fun addMan() {
        change { it.started().copy(men = (it.men + 1).coerceAtMost(MAX_VALUE)) }
    }

    fun removeMan() {
        change { it.copy(men = (it.men - 1).coerceAtLeast(0)) }
    }

    fun reset() {
        change { Counts() }
    }

    /** Pierwsze "+" w sesji zapamiętuje czas startu. */
    private fun Counts.started(): Counts =
        if (startedAt > 0L) this else copy(startedAt = System.currentTimeMillis())

    private fun change(transform: (Counts) -> Counts) {
        counts.updateAndGet(transform)
        val f = file ?: return
        // Zapisujemy zawsze NAJNOWSZY stan, więc kolejność zadań nie ma znaczenia.
        writer.execute { save(f, counts.value) }
    }

    private fun load(f: AtomicFile): Counts {
        return try {
            val parts = String(f.readFully(), Charsets.UTF_8).trim().split(";")
            Counts(
                women = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, MAX_VALUE) ?: 0,
                men = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, MAX_VALUE) ?: 0,
                startedAt = parts.getOrNull(2)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            )
        } catch (e: Exception) {
            // Brak pliku przy pierwszym uruchomieniu albo uszkodzony plik – zaczynamy od zera.
            Counts()
        }
    }

    private fun save(f: AtomicFile, c: Counts) {
        var out: FileOutputStream? = null
        try {
            out = f.startWrite()
            out.write("${c.women};${c.men};${c.startedAt}".toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się zapisać stanu liczników", e)
            try {
                if (out != null) f.failWrite(out)
            } catch (ignored: Exception) {
            }
        }
    }
}
