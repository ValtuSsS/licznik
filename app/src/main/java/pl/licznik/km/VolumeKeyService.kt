package pl.licznik.km

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/**
 * Liczenie przyciskami głośności także przy wyłączonym / zablokowanym ekranie.
 *
 * Jak to działa: usługa udaje "odtwarzacz na zdalnym urządzeniu" (MediaSession
 * ze zdalną regulacją głośności). Android wysyła wtedy naciśnięcia przycisków
 * głośności do tej sesji – również przy zgaszonym ekranie – zamiast zmieniać głośność.
 *   Głośniej -> kobieta +1
 *   Ciszej   -> mężczyzna +1
 */
class VolumeKeyService : Service() {

    private var session: MediaSession? = null
    private var lastUpAt = 0L
    private var lastDownAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CounterStore.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        // Najpierw zawsze startForeground – Android wymaga tego po startForegroundService().
        if (!startInForeground() || !isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startSessionIfNeeded()
        // START_STICKY: jeśli system zamknie usługę, sam ją uruchomi ponownie.
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            session?.isActive = false
            session?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd przy zamykaniu sesji", e)
        }
        session = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        return try {
            createChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się uruchomić usługi", e)
            false
        }
    }

    private fun startSessionIfNeeded() {
        if (session != null) return
        try {
            val s = MediaSession(this, "LicznikKM")
            s.setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    onVolumeKey(direction)
                }
            })
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            s.isActive = true
            session = s
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się utworzyć sesji", e)
        }
    }

    /** Wywoływane w wątku głównym; tylko zmiana liczby w pamięci + wibracja, więc natychmiastowe. */
    private fun onVolumeKey(direction: Int) {
        try {
            if (isInCall()) {
                // W trakcie rozmowy przyciski mają zmieniać głośność rozmowy, a nie liczyć.
                if (direction == AudioManager.ADJUST_RAISE || direction == AudioManager.ADJUST_LOWER) {
                    getSystemService(AudioManager::class.java)
                        ?.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, direction, AudioManager.FLAG_SHOW_UI)
                }
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (direction == AudioManager.ADJUST_RAISE && now - lastUpAt >= MIN_GAP_MS) {
                lastUpAt = now
                CounterStore.addWoman()
                Feedback.woman(this)
            } else if (direction == AudioManager.ADJUST_LOWER && now - lastDownAt >= MIN_GAP_MS) {
                lastDownAt = now
                CounterStore.addMan()
                Feedback.man(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd obsługi przycisku", e)
        }
    }

    private fun isInCall(): Boolean {
        return try {
            val mode = getSystemService(AudioManager::class.java)?.mode
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            false
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Liczenie przyciskami głośności",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Stałe powiadomienie – Android go wymaga, żeby usługa mogła działać w tle. */
    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VolumeKeyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            "Wyłącz liczenie przyciskami",
            stop,
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Licznik K/M działa")
            .setContentText("Głośniej = kobieta +1, ciszej = mężczyzna +1")
            .setContentIntent(openApp)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val TAG = "VolumeKeyService"
        private const val CHANNEL_ID = "liczenie"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "pl.licznik.km.STOP"
        private const val PREFS = "ustawienia"
        private const val KEY_ENABLED = "przyciski_glosnosci"

        // Ochrona przed podwójnym policzeniem przy przytrzymaniu przycisku.
        private const val MIN_GAP_MS = 100L

        fun isEnabled(context: Context): Boolean {
            return try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
            } catch (e: Exception) {
                false
            }
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, enabled).apply()
            } catch (e: Exception) {
            }
        }

        fun start(context: Context): Boolean {
            return try {
                context.startForegroundService(Intent(context, VolumeKeyService::class.java))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się uruchomić usługi", e)
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VolumeKeyService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
