package pl.edu.slowscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Główne ustawienia wyglądu. Kolory zapisujemy jako 0xFF + kod HEX.
object RetroStyle {
    val paper = Color(0xFFF4E9CD)
    val panel = Color(0xFFFFF8E4)
    val ink = Color(0xFF252D29)
    val red = Color(0xFFA33628)
    val green = Color(0xFF365A46)
    val gold = Color(0xFFE6BB50)
    val muted = Color(0xFF625F50)
    val line = Color(0xFFB8AB8C)
    val corner = 4.dp
    val border = 2.dp
    val displayFont = FontFamily.Monospace
}

private val retroColors = lightColorScheme(
    primary = RetroStyle.red, onPrimary = RetroStyle.panel,
    primaryContainer = Color(0xFFF3D6BE), onPrimaryContainer = RetroStyle.ink,
    secondary = RetroStyle.green, onSecondary = RetroStyle.panel,
    secondaryContainer = Color(0xFFDCE4CC), onSecondaryContainer = RetroStyle.ink,
    tertiary = RetroStyle.gold, onTertiary = RetroStyle.ink,
    tertiaryContainer = RetroStyle.gold, onTertiaryContainer = RetroStyle.ink,
    background = RetroStyle.paper, onBackground = RetroStyle.ink,
    surface = RetroStyle.panel, onSurface = RetroStyle.ink,
    surfaceVariant = Color(0xFFE8DCBD), onSurfaceVariant = RetroStyle.muted,
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE0D4B6), surfaceBright = RetroStyle.panel,
    surfaceContainerLowest = RetroStyle.panel, surfaceContainerLow = RetroStyle.panel,
    surfaceContainer = RetroStyle.paper, surfaceContainerHigh = Color(0xFFE8DCBD),
    surfaceContainerHighest = Color(0xFFE0D4B6),
    outline = RetroStyle.ink, outlineVariant = RetroStyle.line,
    inverseSurface = RetroStyle.ink, inverseOnSurface = RetroStyle.panel,
    inversePrimary = RetroStyle.gold,
    error = Color(0xFFA32924), onError = RetroStyle.panel,
    errorContainer = Color(0xFFF5D6C8), onErrorContainer = Color(0xFF601F19),
)

// Monospace przypomina tablicę wyników; dłuższe teksty zachowują zwykłą, czytelną czcionkę.
private val retroTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Black),
        headlineSmall = base.headlineSmall.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = RetroStyle.displayFont, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun SlowScoreTheme(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(RetroStyle.corner)
    MaterialTheme(colorScheme = retroColors, typography = retroTypography,
        shapes = Shapes(extraSmall = shape, small = shape, medium = shape, large = shape, extraLarge = shape), content = content)
}

// Wspólne obramowanie kart na liście turniejów, w drabince i w komunikatach.
internal fun Modifier.retroPanel(): Modifier = drawBehind {
    drawRect(RetroStyle.ink.copy(alpha = 0.16f), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = size)
}.border(RetroStyle.border, RetroStyle.ink, RoundedCornerShape(RetroStyle.corner))

// Podgląd bez Firebase: otwórz ten plik w Android Studio i wybierz Split albo Design.
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RetroStylePreview() {
    SlowScoreTheme {
        Column(Modifier.background(RetroStyle.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = RetroStyle.ink, contentColor = RetroStyle.panel) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("SlowScore", style = MaterialTheme.typography.headlineSmall)
                    Text("TURNIEJE AKADEMICKIE", color = RetroStyle.gold, style = MaterialTheme.typography.labelMedium)
                }
            }
            Title("Na kampusie", "Bądź na bieżąco z wynikami")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, {}, label = { Text("Wszystkie") })
                FilterChip(false, {}, label = { Text("Piłka nożna") })
            }
            Card(Modifier.fillMaxWidth().retroPanel(), colors = CardDefaults.cardColors(containerColor = RetroStyle.panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PIŁKA NOŻNA", color = RetroStyle.red, style = MaterialTheme.typography.labelMedium)
                    Text("Puchar kampusu", style = MaterialTheme.typography.titleLarge)
                    Text("Informatyka — Automatyka")
                    Surface(color = RetroStyle.ink, contentColor = RetroStyle.gold) {
                        Text("02 : 01", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            Button(onClick = {}) { Text("Utwórz turniej") }
            OutlinedTextField("organizer", {}, label = { Text("Login") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
