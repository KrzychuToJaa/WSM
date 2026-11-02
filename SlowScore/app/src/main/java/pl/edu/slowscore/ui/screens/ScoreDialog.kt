package pl.edu.slowscore.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.ScoreViewModel

@Composable
internal fun ScoreDialog(model: ScoreViewModel, item: Tournament, match: Match, busy: Boolean, onClose: () -> Unit) {
    val homeId = match.homeTeamId
    val awayId = match.awayTeamId
    if (homeId == null || awayId == null) {
        LaunchedEffect(match.id) { onClose() }
        return
    }
    var home by rememberSaveable(match.id) { mutableStateOf(match.result?.homeScore?.toString() ?: "") }
    var away by rememberSaveable(match.id) { mutableStateOf(match.result?.awayScore?.toString() ?: "") }
    var sets by rememberSaveable(match.id) { mutableStateOf(match.result?.sets?.joinToString("\n") { "${it.home}:${it.away}" } ?: "") }
    var winner by rememberSaveable(match.id) { mutableStateOf(match.result?.winnerId ?: homeId) }
    var error by remember { mutableStateOf<String?>(null) }
    val initialVersion = remember(match.id) { match.version }
    val state by model.controller.state.collectAsState()
    val homeName = item.teams.find { it.id == homeId }?.name ?: "Gospodarze"
    val awayName = item.teams.find { it.id == awayId }?.name ?: "Goście"
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("Wynik spotkania") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$homeName — $awayName")
            if (item.sport == Sport.VOLLEYBALL) {
                OutlinedTextField(sets, { sets = it }, label = { Text("Sety, np. 25:20 — każdy w nowym wierszu") }, minLines = 3)
            } else {
                OutlinedTextField(home, { home = it }, label = { Text("Wynik: $homeName") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(away, { away = it }, label = { Text("Wynik: $awayName") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (item.sport == Sport.FOOTBALL) {
                Text("Przy remisie wskaż zwycięzcę po karnych:")
                listOf(homeId to homeName, awayId to awayName).forEach { (id, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winner == id, onClick = { winner = id }); Text(name)
                    }
                }
            }
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            try {
                val setScores = if (item.sport == Sport.VOLLEYBALL) sets.lines().filter { it.isNotBlank() }.map { line ->
                    val values = line.trim().split(':'); require(values.size == 2) { "Każdy set wpisz jako 25:20." }
                    SetScore(values[0].trim().toInt(), values[1].trim().toInt())
                } else emptyList()
                val h = if (setScores.isNotEmpty()) setScores.count { it.home > it.away } else home.toInt()
                val a = if (setScores.isNotEmpty()) setScores.count { it.away > it.home } else away.toInt()
                val result = MatchResult(h, a, if (h == a) winner else if (h > a) homeId else awayId, setScores)
                TournamentRules.validateResult(item.sport, match, result)
                error = null
                model.submit(block = { saveResult(match.id, result, initialVersion) }, onSuccess = onClose)
            } catch (cause: Exception) { error = if (cause is NumberFormatException) "Wpisz poprawne liczby całkowite." else cause.message }
        }) { Text("Zapisz wynik") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Anuluj") } })
}
