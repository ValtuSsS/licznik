package pl.licznik.km

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WomenColor = Color(0xFFD81B60)
private val MenColor = Color(0xFF1E88E5)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun CounterScreen(
    counts: Counts,
    saving: Boolean,
    volumeKeysOn: Boolean,
    batteryUnrestricted: Boolean,
    onAddWoman: () -> Unit,
    onRemoveWoman: () -> Unit,
    onAddMan: () -> Unit,
    onRemoveMan: () -> Unit,
    onSave: () -> Unit,
    onStartOver: () -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onFixBattery: () -> Unit,
) {
    var askReset by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CounterColumn(
                    label = "Kobiety",
                    value = counts.women,
                    color = WomenColor,
                    female = true,
                    onPlus = onAddWoman,
                    onMinus = onRemoveWoman,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                CounterColumn(
                    label = "Mężczyźni",
                    value = counts.men,
                    color = MenColor,
                    female = false,
                    onPlus = onAddMan,
                    onMinus = onRemoveMan,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            Text(
                text = "Razem: ${counts.total}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(if (saving) "Zapisuję…" else "Zapisz", fontSize = 18.sp)
                }
                OutlinedButton(
                    onClick = { askReset = true },
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text("Od nowa", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Liczenie przyciskami głośności", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Głośniej = kobieta +1, ciszej = mężczyzna +1. Działa też przy zablokowanym ekranie.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = volumeKeysOn, onCheckedChange = onVolumeKeysChange)
            }

            if (volumeKeysOn && !batteryUnrestricted) {
                TextButton(onClick = onFixBattery) {
                    Text("Zezwól na działanie w tle (ustawienia baterii)")
                }
            }
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Zacząć od nowa?") },
            text = {
                Text(
                    "Kobiety ${counts.women}, mężczyźni ${counts.men} zostaną zapisane do pliku " +
                        "jako koniec sesji (z datą startu i końca), a liczniki wyzerowane."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askReset = false
                    onStartOver()
                }) { Text("Tak, od nowa") }
            },
            dismissButton = {
                TextButton(onClick = { askReset = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun CounterColumn(
    label: String,
    value: Int,
    color: Color,
    female: Boolean,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        GenderSymbol(female = female, color = color, modifier = Modifier.size(72.dp))
        Text(text = label, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = onPlus,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        ) {
            Text("+", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = value.toString(),
            fontSize = numberSize(value),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
        OutlinedButton(
            onClick = onMinus,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("−", fontSize = 36.sp, color = color)
        }
    }
}

/** Mniejsza czcionka dla długich liczb, żeby zawsze mieściły się w kolumnie. */
private fun numberSize(value: Int): TextUnit = when {
    value < 1_000 -> 64.sp
    value < 10_000 -> 52.sp
    value < 100_000 -> 44.sp
    else -> 36.sp
}

/** Symbol ♀ / ♂ rysowany ręcznie – wygląda tak samo na każdym telefonie. */
@Composable
private fun GenderSymbol(female: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = s * 0.09f
        val r = s * 0.27f
        if (female) {
            val c = Offset(s * 0.5f, s * 0.33f)
            drawCircle(color = color, radius = r, center = c, style = Stroke(width = stroke))
            drawLine(color, Offset(c.x, c.y + r), Offset(c.x, s * 0.97f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(s * 0.32f, s * 0.80f), Offset(s * 0.68f, s * 0.80f), strokeWidth = stroke, cap = StrokeCap.Round)
        } else {
            val c = Offset(s * 0.40f, s * 0.60f)
            drawCircle(color = color, radius = r, center = c, style = Stroke(width = stroke))
            val d = r * 0.7071f
            val tip = Offset(s * 0.93f, s * 0.07f)
            drawLine(color, Offset(c.x + d, c.y - d), tip, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, tip, Offset(tip.x - s * 0.30f, tip.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, tip, Offset(tip.x, tip.y + s * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
