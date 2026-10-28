package pl.edu.slowscore.domain

enum class Sport(val title: String) {
    FOOTBALL("Piłka nożna"), BASKETBALL("Koszykówka"), VOLLEYBALL("Siatkówka")
}
enum class TournamentStatus(val title: String) {
    DRAFT("Szkic"), UPCOMING("Nadchodzący"), ONGOING("W trakcie"),
    COMPLETED("Zakończony"), ARCHIVED("Archiwum")
}
data class UserProfile(val userId: String, val username: String)
data class Team(val id: String, val name: String)
data class SetScore(val home: Int, val away: Int)
data class MatchResult(
    val homeScore: Int, val awayScore: Int, val winnerId: String,
    val sets: List<SetScore> = emptyList(),
)
data class Match(
    val id: String, val round: Int, val position: Int,
    val homeTeamId: String? = null, val awayTeamId: String? = null,
    val result: MatchResult? = null, val version: Int = 0,
)
data class Tournament(
    val id: String, val name: String, val sport: Sport, val ownerId: String,
    val startDate: Long, val status: TournamentStatus = TournamentStatus.DRAFT,
    val endDate: Long? = null, val teams: List<Team> = emptyList(),
    val matches: List<Match> = emptyList(), val editors: Map<String, String> = emptyMap(),
    val version: Int = 0,
)
data class HistoryRange(val start: Long, val end: Long)
data class DemoData(val tournaments: List<Tournament>, val followed: Map<String, Set<String>> = emptyMap())
enum class Feed { PUBLIC, MINE, FOLLOWED, HISTORY }
