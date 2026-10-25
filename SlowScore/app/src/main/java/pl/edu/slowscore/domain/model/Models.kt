package pl.edu.slowscore.domain.model

import java.time.Instant

// Modele domenowe; mapowanie na dokumenty Firestore będzie osobną warstwą.
enum class Sport { FOOTBALL, BASKETBALL, VOLLEYBALL }
enum class TournamentStatus { DRAFT, UPCOMING, ONGOING, COMPLETED, ARCHIVED }
enum class TournamentFormat { KNOCKOUT }
enum class MatchStatus { SCHEDULED, COMPLETED }

data class UserProfile(val userId: String, val username: String)
data class Team(val id: String, val name: String)

data class Tournament(
    val id: String,
    val name: String,
    val sport: Sport,
    val ownerId: String,
    val startDate: Instant,
    val status: TournamentStatus = TournamentStatus.DRAFT,
    val format: TournamentFormat = TournamentFormat.KNOCKOUT,
    val endDate: Instant? = null,
)

// Dla piłki/koszykówki: punkty/bramki; dla siatkówki: poszczególne sety.
// Reguły poprawności i rozstrzygnięcia remisu będą w przypadkach użycia.
data class SetScore(val home: Int, val away: Int)
data class MatchResult(
    val homeScore: Int,
    val awayScore: Int,
    val winnerId: String,
    val sets: List<SetScore> = emptyList(),
)

data class Match(
    val id: String,
    val round: Int,
    val position: Int,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val result: MatchResult? = null,
    val version: Long = 0,
)

// Półotwarty zakres [start, end), wyliczany z lokalnego kalendarza.
data class HistoryRange(val start: Instant, val end: Instant)
