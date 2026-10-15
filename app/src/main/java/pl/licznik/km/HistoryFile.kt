package pl.licznik.km

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historia zapisów w pliku CSV (otwiera się w Excelu).
 *
 * 1. Główny plik w pamięci aplikacji – tylko dopisywanie, nic nie jest nadpisywane.
 * 2. Kopia całego pliku w: Pobrane/LicznikKM/licznik_zapisy.csv (widoczna w "Moje pliki").
 *
 * Przykład:
 *   Start;Koniec;Kobiety;Mężczyźni;Razem;Uwagi
 *   2026-10-16 09:00:12;2026-10-16 10:15:40;12;9;21;zapis
 *   2026-10-16 09:00:12;2026-10-16 11:02:03;25;18;43;KONIEC SESJI
 *                                                   <- pusta linia = przerwa między sesjami
 *   2026-10-16 13:30:05;2026-10-16 14:00:00;7;4;11;KONIEC SESJI
 */
object HistoryFile {
    private const val TAG = "HistoryFile"
    const val FILE_NAME = "licznik_zapisy.csv"
    private const val FOLDER = "LicznikKM"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
    private const val HEADER = "﻿Start;Koniec;Kobiety;Mężczyźni;Razem;Uwagi\n"

    data class Result(val savedInApp: Boolean, val copiedToDownloads: Boolean)

    /**
     * endOfSession = true dla "Od nowa": wiersz "KONIEC SESJI" + pusta linia.
     * Wywoływać poza wątkiem UI. Nigdy nie rzuca wyjątku.
     */
    @Synchronized
    fun save(context: Context, counts: Counts, endOfSession: Boolean): Result {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val end = Date()
        // Jeśli nic nie kliknięto na "+", start = koniec.
        val start = if (counts.startedAt > 0L) Date(counts.startedAt) else end
        val line = format.format(start) + ";" + format.format(end) + ";" +
            counts.women + ";" + counts.men + ";" + counts.total + ";" +
            (if (endOfSession) "KONIEC SESJI\n\n" else "zapis\n")

        val appFile = File(context.filesDir, FILE_NAME)
        val savedInApp = appendToAppFile(appFile, line)
        val copied = savedInApp && copyToDownloads(context, appFile)
        return Result(savedInApp, copied)
    }

    private fun appendToAppFile(file: File, line: String): Boolean {
        return try {
            val isNew = !file.exists() || file.length() == 0L
            FileOutputStream(file, true).use { out ->
                if (isNew) out.write(HEADER.toByteArray(Charsets.UTF_8))
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync() // fizyczny zapis na dysk, zanim pokażemy "Zapisano"
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się dopisać do pliku w aplikacji", e)
            false
        }
    }

    private fun copyToDownloads(context: Context, source: File): Boolean {
        return try {
            val bytes = source.readBytes()
            val resolver = context.contentResolver
            val existing = findInDownloads(resolver)
            if (existing != null && writeTo(resolver, existing, bytes)) return true
            // Pliku nie ma (np. usunięty ręcznie) – tworzymy nowy z pełną historią.
            val created = createInDownloads(resolver) ?: return false
            writeTo(resolver, created, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się skopiować do Pobranych", e)
            false
        }
    }

    private fun findInDownloads(resolver: ContentResolver): Uri? {
        return try {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                arrayOf(FILE_NAME, RELATIVE_PATH),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createInDownloads(resolver: ContentResolver): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeTo(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean {
        return try {
            // "wt" = zapis z obcięciem starej zawartości (kopiujemy cały plik).
            val out = resolver.openOutputStream(uri, "wt") ?: return false
            out.use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Obsługa przycisków "Zapisz" i "Od nowa". Działa niezależnie od ekranu,
 * więc obrót / zamknięcie okna nie przerwie zapisu w połowie.
 */
object SaveAction {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e("SaveAction", "Nieoczekiwany błąd", e) }
    )

    private val busyFlow = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = busyFlow.asStateFlow()

    /** startOver = true: zapis jako koniec sesji, potem zerowanie liczników. */
    fun run(context: Context, startOver: Boolean) {
        if (busyFlow.value) return
        val appContext = context.applicationContext
        val snapshot = CounterStore.state.value

        // Pusta sesja (nic nie policzono) – nie ma czego zapisywać, tylko zerujemy.
        if (startOver && snapshot.total == 0 && snapshot.startedAt == 0L) {
            CounterStore.reset()
            showToast(appContext, "Liczniki wyzerowane.")
            return
        }

        busyFlow.value = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { HistoryFile.save(appContext, snapshot, startOver) }
                val numbers = "K: ${snapshot.women}, M: ${snapshot.men}"
                val message = when {
                    !result.savedInApp ->
                        "BŁĄD: nie udało się zapisać!" +
                            if (startOver) "\nLiczniki NIE zostały wyzerowane." else ""
                    result.copiedToDownloads ->
                        "Zapisano ($numbers)\nPlik: Pobrane/LicznikKM/${HistoryFile.FILE_NAME}"
                    else ->
                        "Zapisano w aplikacji ($numbers), ale nie udało się skopiować do folderu Pobrane."
                }
                if (result.savedInApp && startOver) CounterStore.reset()
                showToast(appContext, if (result.savedInApp && startOver) "$message\nNowa sesja – liczniki wyzerowane." else message)
            } catch (e: Exception) {
                showToast(appContext, "BŁĄD zapisu. Spróbuj ponownie.")
            } finally {
                busyFlow.value = false
            }
        }
    }
}
