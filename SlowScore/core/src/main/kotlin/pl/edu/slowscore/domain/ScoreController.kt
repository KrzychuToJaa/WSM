package pl.edu.slowscore.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenState(
    val user: UserProfile? = null, val feed: Feed = Feed.PUBLIC,
    val sport: Sport? = null, val monthly: Boolean = false, val offset: Int = 0,
    val items: List<Tournament> = emptyList(), val selected: Tournament? = null,
    val followed: Set<String> = emptySet(), val busy: Boolean = false,
    val error: String? = null, val message: String? = null,
)

class ScoreController(val repository: ScoreRepository, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    val visibleItems: List<Tournament> get() = with(mutable.value) {
        TournamentRules.filter(items, sport, if (feed == Feed.HISTORY) TournamentRules.historyRange(clock(), monthly, offset) else null)
    }
    private suspend fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val user = try { repository.currentUser() } catch (_: Exception) { mutable.value.user }
            mutable.value = mutable.value.copy(user = user,
                error = if (error is IllegalArgumentException || error is IllegalStateException)
                    error.message ?: "Nie udało się wykonać operacji. Spróbuj ponownie."
                else "Nie udało się połączyć z serwerem. Sprawdź sieć i spróbuj ponownie.")
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun clearNotice() { mutable.value = mutable.value.copy(error = null, message = null) }
    fun filter(sport: Sport?) { mutable.value = mutable.value.copy(sport = sport) }
    fun period(monthly: Boolean, offset: Int) { mutable.value = mutable.value.copy(monthly = monthly, offset = offset) }
    fun close() { mutable.value = mutable.value.copy(selected = null, error = null) }
    private suspend fun reload() {
        val user = repository.currentUser()
        val feed = if (user == null) Feed.PUBLIC else mutable.value.feed
        val items = repository.load(feed)
        val followed = if (user == null) emptySet() else repository.followedIds()
        mutable.value = mutable.value.copy(user = user, feed = feed, items = items, followed = followed)
    }
    suspend fun refresh() = action { reload(); mutable.value.selected?.id?.let { openData(it) } }
    suspend fun feed(feed: Feed) = action {
        require(feed == Feed.PUBLIC || repository.currentUser() != null) { "Zaloguj się, aby otworzyć tę listę." }
        val items = repository.load(feed)
        mutable.value = mutable.value.copy(feed = feed, items = items, selected = null, offset = 0)
    }
    private suspend fun openData(id: String) { mutable.value = mutable.value.copy(selected = repository.get(id)) }
    suspend fun open(id: String) = action { openData(id) }
    suspend fun signIn(email: String, password: String) = action { repository.signIn(email, password); reload() }
    suspend fun signUp(email: String, password: String, username: String) = action {
        require(password.length >= 8) { "Hasło musi mieć co najmniej 8 znaków." }
        TournamentRules.username(username)
        repository.signUp(email, password, username); reload()
    }
    suspend fun finishProfile(username: String) = action { repository.finishProfile(username); reload() }
    suspend fun resetPassword(email: String) = action {
        require(email.contains('@')) { "Wpisz adres e-mail." }
        repository.resetPassword(email)
        mutable.value = mutable.value.copy(message = "Jeśli konto istnieje, otrzymasz wiadomość do zmiany hasła.")
    }
    suspend fun demoUser(id: String) = action {
        repository.useDemoUser(id)
        mutable.value = ScreenState(busy = true)
        reload()
    }
    suspend fun signOut() = action {
        repository.signOut(); mutable.value = ScreenState(busy = true); reload()
    }
    suspend fun create(name: String, sport: Sport, date: Long, teams: List<String>) = action {
        val id = repository.create(name, sport, date, teams)
        reload(); openData(id)
    }
    private suspend fun edit(operation: suspend (String) -> Unit) = action {
        val id = requireNotNull(mutable.value.selected?.id) { "Wybierz turniej." }
        operation(id); reload(); openData(id)
        mutable.value = mutable.value.copy(message = "Zapisano zmiany.")
    }
    suspend fun shuffle() = edit { repository.shuffle(it) }
    suspend fun publish() = edit { repository.publish(it) }
    suspend fun archive() = edit { repository.archive(it) }
    suspend fun addEditor(username: String) = edit { repository.addEditor(it, username) }
    suspend fun removeEditor(userId: String) = edit { repository.removeEditor(it, userId) }
    suspend fun saveResult(matchId: String, result: MatchResult, version: Int) = edit {
        repository.saveResult(it, matchId, result, version)
    }
    suspend fun follow() = edit { repository.follow(it, it !in mutable.value.followed) }
}
