package pl.edu.slowscore.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.*
import java.time.format.DateTimeParseException

@Composable
internal fun TournamentForm(model: ScoreViewModel, state: ScreenState,
                            item: Tournament? = null, onClose: () -> Unit) {
    val editing = item != null
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name ?: "") }
    var sportName by rememberSaveable(item?.id) { mutableStateOf(item?.sport?.name ?: Sport.FOOTBALL.name) }
    var startDate by rememberSaveable(item?.id) { mutableStateOf(date(item?.startDate ?: (System.currentTimeMillis() + 86_400_000))) }
    var teams by rememberSaveable(item?.id) { mutableStateOf(item?.teams?.joinToString("\n") { it.name } ?: "") }
    var error by rememberSaveable(item?.id) { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val initialVersion = remember(item?.id) { item?.version ?: 0 }

    BackHandler { if (!state.busy) onClose() }
    LaunchedEffect(submitted, state.busy, state.error, state.selected?.version) {
        if (submitted && !state.busy) {
            val saved = if (editing) state.selected?.id == item?.id && state.selected?.version != initialVersion
                else state.selected != null
            if (state.error == null && saved) onClose()
            else { submitted = false; error = state.error }
        }
    }

    Title(if (editing) "Edytuj turniej" else "Nowy turniej",
        if (editing) "Nazwę, drużyny i termin możesz zmieniać przed rozpoczęciem."
        else "Zapisz szkic i sprawdź drabinkę przed publikacją.")
    OutlinedTextField(name, { name = it }, label = { Text("Nazwa turnieju") },
        enabled = !state.busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
    if (editing) Text("Dyscyplina: ${Sport.valueOf(sportName).title}")
    else SportFilter(Sport.valueOf(sportName)) { if (it != null) sportName = it.name }
    OutlinedTextField(startDate, { startDate = it }, label = { Text("Termin: dd.MM.rrrr GG:mm") },
        supportingText = { Text("Strefa: Warszawa") }, enabled = !state.busy,
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(teams, { teams = it }, label = { Text("Drużyny — każda w osobnym wierszu") },
        supportingText = { Text("Dodaj 4, 8 lub 16 drużyn.") }, enabled = !state.busy,
        minLines = 5, modifier = Modifier.fillMaxWidth())
    if (editing) Text("Przy tej samej liczbie drużyn pary zostają zachowane. Zmiana liczby drużyn tworzy nową drabinkę i nie przywraca dodatkowego losowania.")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(enabled = !state.busy && !submitted, onClick = {
        try {
            val time = parseDate(startDate)
            val names = teams.lines().map { it.trim() }.filter { it.isNotEmpty() }
            TournamentRules.validateDraft(name, time, names.mapIndexed { index, team -> Team(index.toString(), team) }, System.currentTimeMillis())
            error = null
            submitted = true
            model.run {
                if (editing) updateDetails(name, time, names, initialVersion)
                else create(name, Sport.valueOf(sportName), time, names)
            }
        } catch (cause: Exception) {
            error = if (cause is DateTimeParseException) "Format daty: dd.MM.rrrr GG:mm." else cause.message
        }
    }) { Text(if (editing) "Zapisz zmiany" else "Zapisz szkic i utwórz drabinkę") }
    TextButton(enabled = !state.busy, onClick = onClose) { Text("Anuluj") }
}
