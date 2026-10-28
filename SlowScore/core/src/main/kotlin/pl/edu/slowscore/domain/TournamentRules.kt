package pl.edu.slowscore.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.random.Random

object TournamentRules {
    fun username(value: String): String {
        val name = value.trim().lowercase(java.util.Locale.ROOT)
        require(name.matches(Regex("[a-z0-9_]{3,24}"))) { "Login: 3–24 znaki, litery a–z, cyfry lub _." }
        return name
    }

    fun validateDraft(name: String, startDate: Long, teams: List<Team>, now: Long) {
        require(name.trim().length in 3..80) { "Nazwa turnieju musi mieć 3–80 znaków." }
        require(startDate > now) { "Wybierz przyszły termin turnieju." }
        require(teams.size in listOf(4, 8, 16)) { "Dodaj dokładnie 4, 8 lub 16 drużyn." }
        require(teams.all { it.name.trim().length in 2..40 && it.id.isNotBlank() }) { "Nazwa drużyny: 2–40 znaków." }
        require(teams.map { it.id }.distinct().size == teams.size) { "Powtarzające się identyfikatory drużyn." }
        require(teams.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == teams.size) {
            "Nazwy drużyn muszą być różne."
        }
    }

    fun bracket(teams: List<Team>, seed: Int? = null): List<Match> {
        require(teams.size in listOf(4, 8, 16)) { "Drabinka wymaga 4, 8 lub 16 drużyn." }
        require(teams.map { it.id }.distinct().size == teams.size) { "Drużyna nie może wystąpić dwukrotnie." }
        val order = if (seed == null) teams else teams.shuffled(Random(seed))
        val rounds = Integer.numberOfTrailingZeros(teams.size)
        return (0 until rounds).flatMap { round ->
            (0 until (teams.size shr (round + 1))).map { position ->
                Match("${round}_$position", round, position,
                    if (round == 0) order[position * 2].id else null,
                    if (round == 0) order[position * 2 + 1].id else null)
            }
        }
    }

    fun canEdit(tournament: Tournament, userId: String?): Boolean =
        userId != null && (tournament.ownerId == userId || userId in tournament.editors)

    fun requireOwner(tournament: Tournament, userId: String?) {
        require(userId != null && userId == tournament.ownerId) { "Tylko właściciel może wykonać tę operację." }
    }

    fun validateResult(sport: Sport, match: Match, result: MatchResult) {
        val home = requireNotNull(match.homeTeamId) { "Brak gospodarzy meczu." }
        val away = requireNotNull(match.awayTeamId) { "Brak gości meczu." }
        require(home != away) { "Drużyna nie może grać sama ze sobą." }
        require(result.winnerId == home || result.winnerId == away) { "Zwycięzca musi uczestniczyć w meczu." }
        require(result.homeScore in 0..999 && result.awayScore in 0..999) { "Wynik musi mieścić się w zakresie 0–999." }
        if (sport == Sport.VOLLEYBALL) {
            require(result.sets.size in 3..5) { "Siatkówka wymaga 3–5 rozegranych setów." }
            var homeSets = 0
            var awaySets = 0
            result.sets.forEachIndexed { index, set ->
                require(homeSets < 3 && awaySets < 3) { "Mecz zakończył się wcześniej." }
                require(set.home in 0..999 && set.away in 0..999) { "Nieprawidłowe punkty seta." }
                val target = if (index == 4) 15 else 25
                val winner = maxOf(set.home, set.away)
                val loser = minOf(set.home, set.away)
                require(winner >= target && winner - loser >= 2 &&
                    (winner == target || winner - loser == 2)) { "Set do $target punktów wymaga przewagi dwóch punktów." }
                if (set.home > set.away) homeSets++ else awaySets++
            }
            require(maxOf(homeSets, awaySets) == 3) { "Jedna drużyna musi wygrać trzy sety." }
            require(result.homeScore == homeSets && result.awayScore == awaySets) { "Wynik meczu musi odpowiadać setom." }
        } else {
            require(result.sets.isEmpty()) { "Ta dyscyplina nie korzysta z setów." }
            if (sport == Sport.BASKETBALL) require(result.homeScore != result.awayScore) { "Koszykówka wymaga rozstrzygnięcia dogrywki." }
        }
        if (result.homeScore != result.awayScore) {
            require(result.winnerId == if (result.homeScore > result.awayScore) home else away) {
                "Zwycięzca nie zgadza się z wynikiem."
            }
        }
        // Remis w piłce nożnej rozstrzyga wskazany zwycięzca po rzutach karnych.
    }

    fun saveResult(tournament: Tournament, userId: String?, matchId: String,
                   result: MatchResult, expectedVersion: Int, now: Long): Tournament {
        require(canEdit(tournament, userId)) { "Nie masz uprawnień do wyników." }
        require(tournament.status in listOf(TournamentStatus.UPCOMING, TournamentStatus.ONGOING)) { "Turniej nie przyjmuje wyników." }
        val match = tournament.matches.find { it.id == matchId } ?: error("Nie znaleziono meczu.")
        require(match.version == expectedVersion) { "Wynik zmienił się. Odśwież dane i spróbuj ponownie." }
        validateResult(tournament.sport, match, result)
        val next = tournament.matches.find { it.round == match.round + 1 && it.position == match.position / 2 }
        require(next?.result == null) { "Nie można poprawić wyniku po rozstrzygnięciu kolejnej rundy." }
        val matches = tournament.matches.map {
            when (it.id) {
                match.id -> it.copy(result = result, version = it.version + 1)
                next?.id -> if (match.position % 2 == 0) it.copy(homeTeamId = result.winnerId, version = it.version + 1)
                    else it.copy(awayTeamId = result.winnerId, version = it.version + 1)
                else -> it
            }
        }
        return tournament.copy(matches = matches, version = tournament.version + 1,
            status = if (next == null) TournamentStatus.COMPLETED else TournamentStatus.ONGOING,
            endDate = if (next == null) now else null)
    }

    fun historyRange(now: Long, monthly: Boolean, offset: Int = 0, zone: ZoneId = ZoneId.of("Europe/Warsaw")): HistoryRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = if (monthly) today.withDayOfMonth(1).plusMonths(offset.toLong())
            else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        val end = if (monthly) start.plusMonths(1) else start.plusWeeks(1)
        return HistoryRange(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun filter(tournaments: List<Tournament>, sport: Sport?, range: HistoryRange? = null): List<Tournament> =
        tournaments.filter { (sport == null || it.sport == sport) && (range == null ||
            (it.status in listOf(TournamentStatus.COMPLETED, TournamentStatus.ARCHIVED) &&
                it.endDate?.let { date -> date >= range.start && date < range.end } == true)) }
            .sortedBy { if (range == null) it.startDate else -(it.endDate ?: 0) }
}
