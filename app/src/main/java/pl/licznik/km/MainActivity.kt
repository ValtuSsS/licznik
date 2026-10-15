package pl.licznik.km

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var volumeKeysOn by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(true)

    // Wynik nie jest potrzebny – liczenie działa także bez zgody na powiadomienia.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CounterStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val counts by CounterStore.state.collectAsState()
            val saving by SaveAction.busy.collectAsState()
            AppTheme {
                CounterScreen(
                    counts = counts,
                    saving = saving,
                    volumeKeysOn = volumeKeysOn,
                    batteryUnrestricted = batteryUnrestricted,
                    onAddWoman = { CounterStore.addWoman() },
                    onRemoveWoman = { CounterStore.removeWoman() },
                    onAddMan = { CounterStore.addMan() },
                    onRemoveMan = { CounterStore.removeMan() },
                    onSave = { SaveAction.run(applicationContext, startOver = false) },
                    onStartOver = { SaveAction.run(applicationContext, startOver = true) },
                    onVolumeKeysChange = { setVolumeKeys(it) },
                    onFixBattery = { requestBatteryExemption() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stan mógł się zmienić z powiadomienia ("Wyłącz") albo po restarcie telefonu.
        volumeKeysOn = VolumeKeyService.isEnabled(this)
        batteryUnrestricted = isBatteryUnrestricted()
        if (volumeKeysOn) VolumeKeyService.start(this)
    }

    /**
     * Gdy aplikacja jest na ekranie, przyciski głośności obsługujemy tutaj
     * (usługa w tle obsługuje je, gdy ekran jest zgaszony lub zablokowany).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (volumeKeysOn && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // Liczymy tylko pierwsze naciśnięcie – przytrzymanie nie dolicza kolejnych.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                    CounterStore.addWoman()
                    Feedback.woman(this)
                } else {
                    CounterStore.addMan()
                    Feedback.man(this)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setVolumeKeys(on: Boolean) {
        VolumeKeyService.setEnabled(this, on)
        volumeKeysOn = on
        if (on) {
            askForNotificationPermission()
            if (!VolumeKeyService.start(this)) {
                showToast(this, "Nie udało się uruchomić liczenia w tle. Na otwartej aplikacji przyciski działają.")
            }
        } else {
            VolumeKeyService.stop(this)
        }
    }

    private fun askForNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (e: Exception) {
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        return try {
            getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true
        } catch (e: Exception) {
            true
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                showToast(this, "Ustaw ręcznie: Ustawienia > Aplikacje > Licznik K/M > Bateria > Bez ograniczeń")
            }
        }
    }
}
