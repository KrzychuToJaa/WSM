package pl.edu.slowscore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.screens.DetailsScreen
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
internal val zone: ZoneId = ZoneId.of("Europe/Warsaw")
internal fun date(value: Long): String = Instant.ofEpochMilli(value).atZone(zone).format(dateFormat)

@Composable
fun SlowScoreApp(model: ScoreViewModel = viewModel()) {
    val controller = model.controller
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        val job = model.watch()
        onStopOrDispose { job.cancel() }
    }
    var page by rememberSaveable { mutableStateOf("feed") }
    LaunchedEffect(state.selected?.id) { if (state.selected != null && page == "create") page = "feed" }
    BackHandler(enabled = state.selected != null || page != "feed") {
        if (!state.busy) { controller.close(); page = "feed" }
    }
    SlowScoreTheme {
        Scaffold(topBar = {
            Surface(color = RetroStyle.ink, contentColor = RetroStyle.panel) {
                Column {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("SlowScore", style = MaterialTheme.typography.headlineSmall)
                        Text("SPORT AKADEMICKI", color = RetroStyle.gold, style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(thickness = 4.dp, color = RetroStyle.gold)
                }
            }
        }, bottomBar = {
            NavigationBar(containerColor = RetroStyle.ink, tonalElevation = 0.dp) {
                listOf("Turnieje" to Feed.PUBLIC, "Moje" to Feed.MINE, "Śledzone" to Feed.FOLLOWED,
                    "Historia" to Feed.HISTORY, "Konto" to null).forEachIndexed { index, (label, feed) ->
                    NavigationBarItem(selected = if (feed == null) page == "account" else page == "feed" && state.feed == feed,
                        enabled = !state.busy, onClick = {
                            controller.close()
                            if (feed == null || (feed != Feed.PUBLIC && state.user == null)) page = "account"
                            else { page = "feed"; model.run { feed(feed) } }
                        }, colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RetroStyle.ink, selectedTextColor = RetroStyle.gold,
                            indicatorColor = RetroStyle.gold, unselectedIconColor = RetroStyle.panel,
                            unselectedTextColor = RetroStyle.panel),
                        icon = { Text(listOf("●", "◆", "★", "◷", "○")[index]) }, label = { Text(label, maxLines = 1) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (controller.repository.isDemo) Text("Tryb demo • dane zapisane na tym urządzeniu",
                    Modifier.fillMaxWidth().background(RetroStyle.gold).padding(10.dp), style = MaterialTheme.typography.labelSmall)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.error?.let {
                        Card(Modifier.fillMaxWidth().retroPanel(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(enabled = !state.busy, onClick = { model.run { refresh() } }) { Text("Odśwież dane") }
                            }
                        }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    when {
                        state.user?.username == "" -> ProfileForm(model, state)
                        page == "account" -> AccountScreen(model, state)
                        page == "create" -> CreateScreen(model, state) { page = "feed" }
                        state.selected != null -> DetailsScreen(model, state, state.selected!!)
                        else -> FeedScreen(model, state, { page = "create" }, { page = "account" })
                    }
                }
            }
        }
    }
}

@Composable
internal fun Title(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.width(48.dp), thickness = 4.dp, color = MaterialTheme.colorScheme.primary)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FeedScreen(model: ScoreViewModel, state: ScreenState, onCreate: () -> Unit, onLogin: () -> Unit) {
    Title(when (state.feed) { Feed.PUBLIC -> "Na kampusie"; Feed.MINE -> "Moje turnieje"; Feed.FOLLOWED -> "Obserwowane"; Feed.HISTORY -> "Historia wyników" },
        "Drużyny, mecze i emocje — wybierz swój turniej.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !state.busy, onClick = if (state.user == null) onLogin else onCreate) { Text("Utwórz turniej") }
        TextButton(enabled = !state.busy, onClick = { model.run { refresh() } }) { Text("Odśwież") }
    }
    SportFilter(state.sport) { model.controller.filter(it) }
    if (state.feed == Feed.HISTORY) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.monthly, { model.controller.period(false, 0) }, label = { Text("Tydzień") })
            FilterChip(state.monthly, { model.controller.period(true, 0) }, label = { Text("Miesiąc") })
        }
        val range = TournamentRules.historyRange(System.currentTimeMillis(), state.monthly, state.offset)
        Text("${date(range.start).substringBefore(' ')} – ${date(range.end - 1).substringBefore(' ')}")
        Row {
            TextButton(onClick = { model.controller.period(state.monthly, state.offset - 1) }) { Text("← Poprzedni") }
            TextButton(onClick = { model.controller.period(state.monthly, state.offset + 1) }) { Text("Następny →") }
        }
    }
    val items = model.controller.visibleItems
    if (items.isEmpty() && !state.busy) Card(Modifier.fillMaxWidth().retroPanel(), colors = CardDefaults.cardColors(containerColor = RetroStyle.panel)) {
        Text("Brak turniejów dla wybranych filtrów. Zmień dyscyplinę lub okres.", Modifier.padding(20.dp))
    }
    items.forEach { item ->
        Card(onClick = { model.run { open(item.id) } }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().retroPanel(), colors = CardDefaults.cardColors(containerColor = RetroStyle.panel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.sport.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${item.status.title} • drużyny: ${item.teams.size}")
                Text(date(item.startDate), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SportFilter(selected: Sport?, onChange: (Sport?) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == null, { onChange(null) }, label = { Text("Wszystkie") })
            FilterChip(selected == Sport.FOOTBALL, { onChange(Sport.FOOTBALL) }, label = { Text("Piłka nożna") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Sport.BASKETBALL, Sport.VOLLEYBALL).forEach { sport ->
                FilterChip(selected == sport, { onChange(sport) }, label = { Text(sport.title) })
            }
        }
    }
}

@Composable
private fun AccountScreen(model: ScoreViewModel, state: ScreenState) {
    Title("Twoje konto", "Organizuj turnieje i zapisuj obserwowane wydarzenia.")
    if (state.user != null) {
        Text("Zalogowano jako ${state.user?.username}", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !state.busy, onClick = { model.run { signOut() } }) { Text("Wyloguj") }
    }
    if (model.controller.repository.isDemo) {
        Text("Wybierz lokalne konto demonstracyjne. Nie wpisuj prawdziwego hasła.")
        listOf("demo_owner" to "organizer — właściciel", "demo_editor" to "editor — wyniki", "demo_viewer" to "viewer — kibic").forEach { (id, label) ->
            OutlinedButton(enabled = !state.busy, onClick = { model.run { demoUser(id) } }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        Text("Firebase uruchamia się po dodaniu konfiguracji projektu lub z wariantem emulatorów.")
    } else if (state.user == null) {
        var register by rememberSaveable { mutableStateOf(false) }
        var email by rememberSaveable { mutableStateOf("") }
        var username by rememberSaveable { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        if (register) OutlinedTextField(username, { username = it }, label = { Text("Login (a–z, cyfry, _)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Hasło") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(showPassword, { showPassword = it }); Text("Pokaż hasło") }
        Button(enabled = !state.busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            model.run { if (register) signUp(email, password, username) else signIn(email, password) }
        }) { Text(if (register) "Utwórz konto" else "Zaloguj") }
        TextButton(enabled = !state.busy, onClick = { register = !register }) { Text(if (register) "Mam już konto" else "Załóż konto") }
        TextButton(enabled = !state.busy && email.isNotBlank(), onClick = { model.run { resetPassword(email) } }) { Text("Nie pamiętam hasła") }
    }
}

@Composable
private fun ProfileForm(model: ScoreViewModel, state: ScreenState) {
    var username by rememberSaveable { mutableStateOf("") }
    Title("Dokończ profil", "Konto istnieje. Wybierz unikalny login.")
    OutlinedTextField(username, { username = it }, label = { Text("Login") }, singleLine = true)
    Button(enabled = !state.busy, onClick = { model.run { finishProfile(username) } }) { Text("Zapisz login") }
    TextButton(enabled = !state.busy, onClick = { model.run { signOut() } }) { Text("Wyloguj") }
}

@Composable
private fun CreateScreen(model: ScoreViewModel, state: ScreenState, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var sportName by rememberSaveable { mutableStateOf(Sport.FOOTBALL.name) }
    var startDate by rememberSaveable { mutableStateOf(date(System.currentTimeMillis() + 86_400_000)) }
    var teams by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Title("Nowy turniej", "Zapisz szkic i sprawdź drabinkę przed publikacją.")
    OutlinedTextField(name, { name = it }, label = { Text("Nazwa turnieju") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    SportFilter(Sport.valueOf(sportName)) { if (it != null) sportName = it.name }
    OutlinedTextField(startDate, { startDate = it }, label = { Text("Termin: dd.MM.rrrr GG:mm") }, supportingText = { Text("Strefa: Warszawa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(teams, { teams = it }, label = { Text("Drużyny — każda w osobnym wierszu") },
        supportingText = { Text("Dodaj 4, 8 lub 16 drużyn. Kolejność wyznacza pary.") }, minLines = 5, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(enabled = !state.busy, onClick = {
        try {
            val local = LocalDateTime.parse(startDate.trim(), dateFormat)
            require(zone.rules.getValidOffsets(local).size == 1) { "Termin wypada przy zmianie czasu. Wybierz inną godzinę." }
            error = null
            model.run { create(name, Sport.valueOf(sportName), local.atZone(zone).toInstant().toEpochMilli(), teams.lines().filter { it.isNotBlank() }) }
        } catch (cause: Exception) { error = if (cause is java.time.format.DateTimeParseException) "Format daty: dd.MM.rrrr GG:mm." else cause.message }
    }) { Text("Zapisz szkic i utwórz drabinkę") }
    TextButton(enabled = !state.busy, onClick = onBack) { Text("Anuluj") }
}
