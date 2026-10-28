package pl.edu.slowscore.domain

import java.util.UUID

/** Dane demonstracyjne są oddzielone od kont i danych Firebase. */
class DemoRepository(
    savedData: DemoData? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val persist: (DemoData) -> Unit = {},
) : ScoreRepository {
    override val isDemo = true
    private var user: UserProfile? = null
    private var data = savedData ?: sampleData(clock())
    private val users = listOf(UserProfile("demo_owner", "organizer"),
        UserProfile("demo_editor", "editor"), UserProfile("demo_viewer", "viewer"))

    private fun userId(): String = requireNotNull(user?.userId) { "Zaloguj się, aby kontynuować." }
    private fun store(next: DemoData) { persist(next); data = next }
    private fun update(id: String, change: (Tournament) -> Tournament) {
        val old = data.tournaments.find { it.id == id } ?: error("Nie znaleziono turnieju.")
        val next = change(old)
        store(data.copy(tournaments = data.tournaments.map { if (it.id == id) next else it }))
    }
    override suspend fun currentUser() = user
    override suspend fun signIn(email: String, password: String): UserProfile = error("W trybie demo wybierz jedno z kont demonstracyjnych.")
    override suspend fun signUp(email: String, password: String, username: String): UserProfile = error("Rejestracja jest dostępna w trybie Firebase.")
    override suspend fun finishProfile(username: String): UserProfile = error("Konta demonstracyjne mają już login.")
    override suspend fun resetPassword(email: String) { error("Odzyskiwanie hasła jest dostępne w trybie Firebase.") }
    override suspend fun signOut() { user = null }
    override suspend fun useDemoUser(userId: String) {
        user = users.find { it.userId == userId } ?: error("Nie znaleziono konta demonstracyjnego.")
    }
    override suspend fun load(feed: Feed): List<Tournament> = when (feed) {
        Feed.PUBLIC -> data.tournaments.filter { it.status in listOf(TournamentStatus.UPCOMING, TournamentStatus.ONGOING) }
        Feed.MINE -> { val uid = userId(); data.tournaments.filter { it.ownerId == uid || uid in it.editors } }
        Feed.FOLLOWED -> { val ids = followedIds(); data.tournaments.filter { it.id in ids && it.status != TournamentStatus.DRAFT } }
        Feed.HISTORY -> { userId(); data.tournaments.filter { it.status in listOf(TournamentStatus.COMPLETED, TournamentStatus.ARCHIVED) } }
    }
    override suspend fun get(id: String): Tournament {
        val item = data.tournaments.find { it.id == id } ?: error("Nie znaleziono turnieju.")
        if (item.status == TournamentStatus.DRAFT) require(TournamentRules.canEdit(item, user?.userId)) { "Nie masz dostępu do tego szkicu." }
        if (item.status in listOf(TournamentStatus.COMPLETED, TournamentStatus.ARCHIVED)) userId()
        return item
    }
    override suspend fun create(name: String, sport: Sport, startDate: Long, teamNames: List<String>): String {
        val uid = userId()
        val teams = teamNames.map { Team(UUID.randomUUID().toString(), it.trim()) }
        TournamentRules.validateDraft(name, startDate, teams, clock())
        val item = Tournament(UUID.randomUUID().toString(), name.trim(), sport, uid, startDate,
            teams = teams, matches = TournamentRules.bracket(teams))
        store(data.copy(tournaments = data.tournaments + item))
        return item.id
    }
    override suspend fun shuffle(id: String) = update(id) {
        TournamentRules.requireOwner(it, user?.userId)
        require(it.status == TournamentStatus.DRAFT) { "Drabinkę można zmieniać wyłącznie w szkicu." }
        it.copy(matches = TournamentRules.bracket(it.teams, kotlin.random.Random.nextInt()), version = it.version + 1)
    }
    override suspend fun publish(id: String) = update(id) {
        TournamentRules.requireOwner(it, user?.userId)
        require(it.status == TournamentStatus.DRAFT) { "Turniej został już opublikowany." }
        TournamentRules.validateDraft(it.name, it.startDate, it.teams, clock())
        it.copy(status = TournamentStatus.UPCOMING, version = it.version + 1)
    }
    override suspend fun saveResult(id: String, matchId: String, result: MatchResult, version: Int) = update(id) {
        TournamentRules.saveResult(it, user?.userId, matchId, result, version, clock())
    }
    override suspend fun addEditor(id: String, username: String) {
        val name = TournamentRules.username(username)
        update(id) { item ->
            TournamentRules.requireOwner(item, user?.userId)
            val editor = users.find { it.username == name } ?: error("Nie znaleziono użytkownika o tym loginie.")
            require(editor.userId != item.ownerId) { "Właściciel ma już dostęp do wyników." }
            item.copy(editors = item.editors + (editor.userId to editor.username), version = item.version + 1)
        }
    }
    override suspend fun removeEditor(id: String, userId: String) = update(id) {
        TournamentRules.requireOwner(it, user?.userId)
        it.copy(editors = it.editors - userId, version = it.version + 1)
    }
    override suspend fun archive(id: String) = update(id) {
        TournamentRules.requireOwner(it, user?.userId)
        require(it.status == TournamentStatus.COMPLETED) { "Archiwizować można zakończony turniej." }
        it.copy(status = TournamentStatus.ARCHIVED, version = it.version + 1)
    }
    override suspend fun follow(id: String, enabled: Boolean) {
        val uid = userId()
        require(get(id).status != TournamentStatus.DRAFT) { "Nie można obserwować szkicu." }
        val ids = data.followed[uid].orEmpty()
        store(data.copy(followed = data.followed + (uid to if (enabled) ids + id else ids - id)))
    }
    override suspend fun followedIds(): Set<String> = data.followed[userId()].orEmpty()

    companion object {
        fun sampleData(now: Long): DemoData {
            val teams = listOf("Informatyka", "Mechanika", "Ekonomia", "Architektura").mapIndexed { index, name -> Team("team_$index", name) }
            val active = Sport.entries.mapIndexed { index, sport ->
                Tournament("sample_$index", listOf("Puchar kampusu", "Liga wydziałów", "Akademicka siatkówka")[index],
                    sport, "demo_owner", now + (index + 1) * 86_400_000L, TournamentStatus.UPCOMING,
                    teams = teams, matches = TournamentRules.bracket(teams), editors = mapOf("demo_editor" to "editor"))
            }
            var past = active.first().copy(id = "sample_past", name = "Jesienny puchar", startDate = now - 172_800_000)
            for (id in listOf("0_0", "0_1", "1_0")) {
                val match = past.matches.first { it.id == id }
                past = TournamentRules.saveResult(past, "demo_owner", id, MatchResult(2, 0, match.homeTeamId!!), match.version, now - 86_400_000)
            }
            return DemoData(active + past)
        }
    }
}
