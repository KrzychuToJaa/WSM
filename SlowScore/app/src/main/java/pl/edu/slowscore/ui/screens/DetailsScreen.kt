package pl.edu.slowscore.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.*

@Composable
internal fun DetailsScreen(model: ScoreViewModel, state: ScreenState, item: Tournament) {
    var tab by rememberSaveable(item.id) { mutableStateOf("matches") }
    var editor by rememberSaveable(item.id) { mutableStateOf("") }
    var scoreId by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var now by remember(item.id) { mutableLongStateOf(model.controller.now()) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val owner = state.user?.userId == item.ownerId
    val beforeStart = TournamentRules.beforeStart(item, now)
    val canEnterResults = TournamentRules.canEnterResults(item, now)
    LaunchedEffect(item.id, item.version, state.busy, state.now) {
        while (true) {
            now = model.controller.now()
            delay(1000)
        }
    }
    if (editing) {
        TournamentForm(model, state, item, onClose = { editing = false })
        return
    }
    TextButton(enabled = !state.busy, onClick = { model.controller.close() }) { Text("← Lista turniejów") }
    Title(item.name, "${item.sport.title} • ${TournamentRules.statusAt(item, now).title}\n${date(item.startDate)}")
    if (now < item.startDate) Text("Wpisywanie wyników będzie dostępne od ${date(item.startDate)}.")
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
                    if (TournamentRules.canEdit(item, state.user?.userId) && canEnterResults
                        && match.homeTeamId != null && match.awayTeamId != null && next?.result == null) {
                        TextButton(enabled = !state.busy, onClick = { model.controller.clearNotice(); scoreId = match.id }) { Text(if (match.result == null) "Wpisz wynik" else "Popraw wynik") }
                    }
                }
            }
        }
    }
    if (owner) {
        HorizontalDivider()
        Text("Zarządzanie turniejem", style = MaterialTheme.typography.titleLarge)
        if (beforeStart) {
            OutlinedButton(enabled = !state.busy, onClick = { model.controller.clearNotice(); editing = true }) { Text("Edytuj turniej") }
            OutlinedButton(enabled = !state.busy && !item.shuffleUsed, onClick = {
                confirm = "Drabinkę można przelosować tylko raz. Wykorzystać dodatkowe losowanie?" to { model.run { shuffle() } }
            }) { Text("Losuj pary ponownie") }
            Text(if (item.shuffleUsed) "Dodatkowe losowanie zostało wykorzystane." else "Pozostało jedno dodatkowe losowanie.")
        }
        if (item.status == TournamentStatus.DRAFT) {
            Button(enabled = !state.busy && beforeStart,
                onClick = { confirm = "Turniej będzie widoczny dla innych. Wyniki można wpisywać dopiero od podanego terminu." to { model.run { publish() } } }) {
                Text("Opublikuj turniej")
            }
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
