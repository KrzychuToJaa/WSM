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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.*

@Composable
internal fun DetailsScreen(model: ScoreViewModel, state: ScreenState, item: Tournament) {
    var tab by rememberSaveable(item.id) { mutableStateOf("matches") }
    var editor by rememberSaveable(item.id) { mutableStateOf("") }
    var scoreId by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val owner = state.user?.userId == item.ownerId
    TextButton(enabled = !state.busy, onClick = { model.controller.close() }) { Text("← Lista turniejów") }
    Title(item.name, "${item.sport.title} • ${item.status.title}\n${date(item.startDate)}")
    if (state.user != null && item.status != TournamentStatus.DRAFT) OutlinedButton(enabled = !state.busy,
        onClick = { model.run { follow() } }) { Text(if (item.id in state.followed) "Przestań obserwować" else "Obserwuj turniej") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("matches" to "Mecze", "bracket" to "Drabinka", "teams" to "Drużyny").forEach { (value, title) ->
            FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(title) })
        }
    }
    if (tab == "teams") item.teams.forEachIndexed { index, team -> Text("${index + 1}. ${team.name}") }
    else item.matches.groupBy { it.round }.toSortedMap().forEach { (round, matches) ->
        Text(if (matches.size == 1) "Finał" else "Runda ${round + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        matches.sortedBy { it.position }.forEach { match ->
            val home = item.teams.find { it.id == match.homeTeamId }?.name ?: "Zwycięzca poprzedniej rundy"
            val away = item.teams.find { it.id == match.awayTeamId }?.name ?: "Zwycięzca poprzedniej rundy"
            val next = item.matches.find { it.round == match.round + 1 && it.position == match.position / 2 }
            Card(Modifier.fillMaxWidth().retroPanel(), colors = CardDefaults.cardColors(containerColor = RetroStyle.panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$home — $away", fontWeight = FontWeight.SemiBold)
                    if (match.result != null) {
                        Surface(color = RetroStyle.ink, contentColor = RetroStyle.gold, shape = MaterialTheme.shapes.small) {
                            Text("${match.result!!.homeScore} : ${match.result!!.awayScore}",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.displaySmall)
                        }
                    } else Text("Oczekiwanie na wynik", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    match.result?.let { result ->
                        Text("Zwycięzca: ${item.teams.find { it.id == result.winnerId }?.name}")
                        if (result.sets.isNotEmpty()) Text(result.sets.joinToString(" • ") { "${it.home}:${it.away}" })
                    }
                    if (tab == "bracket" && next != null) Text("Awans → runda ${next.round + 1}, mecz ${next.position + 1}", style = MaterialTheme.typography.bodySmall)
                    if (TournamentRules.canEdit(item, state.user?.userId) && item.status in listOf(TournamentStatus.UPCOMING, TournamentStatus.ONGOING)
                        && match.homeTeamId != null && match.awayTeamId != null && next?.result == null) {
                        TextButton(enabled = !state.busy, onClick = { scoreId = match.id }) { Text(if (match.result == null) "Wpisz wynik" else "Popraw wynik") }
                    }
                }
            }
        }
    }
    if (owner) {
        HorizontalDivider()
        Text("Zarządzanie turniejem", style = MaterialTheme.typography.titleLarge)
        if (item.status == TournamentStatus.DRAFT) {
            OutlinedButton(enabled = !state.busy, onClick = { model.run { shuffle() } }) { Text("Losuj pary ponownie") }
            Button(enabled = !state.busy, onClick = { confirm = "Po publikacji drabinka zostanie zablokowana." to { model.run { publish() } } }) { Text("Opublikuj turniej") }
        }
        Text("Edytorzy mogą wprowadzać wyniki wyłącznie tego turnieju.")
        OutlinedTextField(editor, { editor = it }, label = { Text("Login edytora") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !state.busy && editor.isNotBlank(), onClick = { model.run { addEditor(editor) } }) { Text("Nadaj uprawnienia") }
        item.editors.forEach { (id, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f))
                TextButton(enabled = !state.busy, onClick = { confirm = "Odebrać uprawnienia użytkownikowi $name?" to { model.run { removeEditor(id) } } }) { Text("Odbierz") }
            }
        }
        if (item.status == TournamentStatus.COMPLETED) OutlinedButton(enabled = !state.busy,
            onClick = { confirm = "Turniej pozostanie w historii i obserwowanych." to { model.run { archive() } } }) { Text("Archiwizuj") }
    }
    confirm?.let { (message, action) ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Potwierdź zmianę") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Potwierdź") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Anuluj") } })
    }
    scoreId?.let { id -> item.matches.find { it.id == id }?.let { match ->
        ScoreDialog(model, item, match, state.busy, onClose = { scoreId = null })
    } }
}

@Composable
private fun ScoreDialog(model: ScoreViewModel, item: Tournament, match: Match, busy: Boolean, onClose: () -> Unit) {
    var home by rememberSaveable(match.id) { mutableStateOf(match.result?.homeScore?.toString() ?: "") }
    var away by rememberSaveable(match.id) { mutableStateOf(match.result?.awayScore?.toString() ?: "") }
    var sets by rememberSaveable(match.id) { mutableStateOf(match.result?.sets?.joinToString("\n") { "${it.home}:${it.away}" } ?: "") }
    var winner by rememberSaveable(match.id) { mutableStateOf(match.result?.winnerId ?: match.homeTeamId!!) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val initialVersion = remember(match.id) { match.version }
    val state by model.controller.state.collectAsState()
    // Formularz znika dopiero po potwierdzeniu zapisu; błąd zachowuje wpisane dane.
    LaunchedEffect(state.busy, submitted) {
        if (submitted && !state.busy) {
            if (state.error == null && state.selected?.matches?.find { it.id == match.id }?.version != initialVersion) onClose()
            else { submitted = false; error = state.error }
        }
    }
    val homeName = item.teams.first { it.id == match.homeTeamId }.name
    val awayName = item.teams.first { it.id == match.awayTeamId }.name
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
                listOf(match.homeTeamId!! to homeName, match.awayTeamId!! to awayName).forEach { (id, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winner == id, onClick = { winner = id }); Text(name)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy && !submitted, onClick = {
            try {
                val setScores = if (item.sport == Sport.VOLLEYBALL) sets.lines().filter { it.isNotBlank() }.map { line ->
                    val values = line.trim().split(':'); require(values.size == 2) { "Każdy set wpisz jako 25:20." }
                    SetScore(values[0].trim().toInt(), values[1].trim().toInt())
                } else emptyList()
                val h = if (setScores.isNotEmpty()) setScores.count { it.home > it.away } else home.toInt()
                val a = if (setScores.isNotEmpty()) setScores.count { it.away > it.home } else away.toInt()
                val result = MatchResult(h, a, if (h == a) winner else if (h > a) match.homeTeamId!! else match.awayTeamId!!, setScores)
                TournamentRules.validateResult(item.sport, match, result)
                submitted = true
                model.run { saveResult(match.id, result, initialVersion) }
            } catch (cause: Exception) { error = if (cause is NumberFormatException) "Wpisz poprawne liczby całkowite." else cause.message }
        }) { Text("Zapisz wynik") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Anuluj") } })
}
