package pl.edu.slowscore.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TournamentRulesTest {
    private val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private fun teams(size: Int = 4) = (1..size).map { Team("$it", "Team $it") }
    private fun tournament(size: Int = 4, sport: Sport = Sport.FOOTBALL): Tournament {
        val teams = teams(size)
        return Tournament("id", "Campus Cup", sport, "owner", now + 10000, TournamentStatus.UPCOMING,
            teams = teams, matches = TournamentRules.bracket(teams), editors = mapOf("editor" to "editor"))
    }
    private fun reject(block: () -> Unit) { assertThrows(Exception::class.java, block) }
    private fun result(match: Match, home: Int = 2, away: Int = 0) = MatchResult(home, away, if (home >= away) match.homeTeamId!! else match.awayTeamId!!)

    @Test fun `usernames are normalized and validated`() {
        assertEquals("player_123", TournamentRules.username(" Player_123 "))
        listOf("ab", "A B", "ąbc", "a".repeat(25), "foo/bar", "").forEach { reject { TournamentRules.username(it) } }
    }
    @Test fun `draft validates team limits names ids and date`() {
        TournamentRules.validateDraft(" Campus Cup ", now + 1, teams(), now)
        for (size in listOf(4, 8, 16)) TournamentRules.validateDraft("Cup", now + 1, teams(size), now)
        reject { TournamentRules.validateDraft("ab", now + 1, teams(), now) }
        reject { TournamentRules.validateDraft("x".repeat(81), now + 1, teams(), now) }
        reject { TournamentRules.validateDraft("Cup", now, teams(), now) }
        reject { TournamentRules.validateDraft("Cup", now - 1, teams(), now) }
        reject { TournamentRules.validateDraft("Cup", now + 1, teams(3), now) }
        for (bad in listOf("x", "x".repeat(41))) reject { TournamentRules.validateDraft("Cup", now + 1, teams().map { it.copy(name = bad) }, now) }
        reject { TournamentRules.validateDraft("Cup", now + 1, teams().map { it.copy(id = "") }, now) }
        reject { TournamentRules.validateDraft("Cup", now + 1, teams().map { it.copy(id = "same") }, now) }
        reject { TournamentRules.validateDraft("Cup", now + 1, teams().mapIndexed { i, team -> team.copy(name = if (i % 2 == 0) " Same " else "SAME") }, now) }
    }
    @Test fun `brackets contain unique entries stable seeds and n minus one matches`() {
        for (size in listOf(4, 8, 16)) {
            val bracket = TournamentRules.bracket(teams(size), 42)
            assertEquals(size - 1, bracket.size)
            assertEquals(size - 1, bracket.map { it.id }.distinct().size)
            assertEquals(teams(size).map { it.id }.toSet(), bracket.filter { it.round == 0 }.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet())
            assertTrue(bracket.filter { it.round > 0 }.all { it.homeTeamId == null && it.awayTeamId == null })
            assertEquals(bracket, TournamentRules.bracket(teams(size), 42))
        }
        reject { TournamentRules.bracket(teams(5)) }
        reject { TournamentRules.bracket(teams().map { it.copy(id = "same") }) }
    }
    @Test fun `permissions distinguish owner editor guest and stranger`() {
        val item = tournament()
        assertTrue(TournamentRules.canEdit(item, "owner")); assertTrue(TournamentRules.canEdit(item, "editor"))
        assertFalse(TournamentRules.canEdit(item, null)); assertFalse(TournamentRules.canEdit(item, "viewer"))
        TournamentRules.requireOwner(item, "owner")
        reject { TournamentRules.requireOwner(item, "editor") }; reject { TournamentRules.requireOwner(item, null) }
    }
    @Test fun `football supports penalties while basketball rejects draws`() {
        val match = tournament().matches.first()
        TournamentRules.validateResult(Sport.FOOTBALL, match, result(match, 1, 1))
        TournamentRules.validateResult(Sport.FOOTBALL, match, result(match, 0, 3))
        TournamentRules.validateResult(Sport.BASKETBALL, match, result(match, 99, 80))
        reject { TournamentRules.validateResult(Sport.BASKETBALL, match, result(match, 1, 1)) }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match, result(match).copy(winnerId = "outsider")) }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match, result(match).copy(winnerId = match.awayTeamId!!)) }
        for (score in listOf(-1, 1000)) {
            reject { TournamentRules.validateResult(Sport.FOOTBALL, match, result(match).copy(homeScore = score)) }
            reject { TournamentRules.validateResult(Sport.FOOTBALL, match, result(match).copy(awayScore = score)) }
        }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match, result(match).copy(sets = listOf(SetScore(25, 0)))) }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match.copy(homeTeamId = null), result(match)) }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match.copy(awayTeamId = null), result(match)) }
        reject { TournamentRules.validateResult(Sport.FOOTBALL, match.copy(awayTeamId = match.homeTeamId), result(match)) }
    }
    @Test fun `volleyball validates extensions match end and tie break`() {
        val match = tournament().matches.first()
        fun score(sets: List<SetScore>) = MatchResult(sets.count { it.home > it.away }, sets.count { it.away > it.home },
            if (sets.count { it.home > it.away } > sets.count { it.away > it.home }) match.homeTeamId!! else match.awayTeamId!!, sets)
        val win = listOf(SetScore(25, 23), SetScore(27, 25), SetScore(25, 0))
        TournamentRules.validateResult(Sport.VOLLEYBALL, match, score(win))
        TournamentRules.validateResult(Sport.VOLLEYBALL, match, score(win.map { SetScore(it.away, it.home) }))
        val five = listOf(SetScore(25, 0), SetScore(0, 25), SetScore(25, 0), SetScore(0, 25), SetScore(17, 15))
        TournamentRules.validateResult(Sport.VOLLEYBALL, match, score(five))
        val invalid = listOf(emptyList(), win.take(2), win + SetScore(25, 20),
            win.map { SetScore(25, 24) }, win.map { SetScore(26, 23) }, win.map { SetScore(-1, 25) },
            win.map { SetScore(25, -1) }, win.map { SetScore(24, 22) }, five.dropLast(1) + SetScore(15, 14))
        invalid.forEach { sets -> reject { TournamentRules.validateResult(Sport.VOLLEYBALL, match, score(sets)) } }
        reject { TournamentRules.validateResult(Sport.VOLLEYBALL, match, score(five).copy(homeScore = 9)) }
    }
    @Test fun `winner advances atomically and final completes tournament`() {
        var item = tournament(8)
        for (round in 0..2) {
            for (match in item.matches.filter { it.round == round }) {
                val old = item
                item = TournamentRules.saveResult(item, "editor", match.id, result(match), match.version, now)
                assertEquals(old.version + 1, item.version)
                assertEquals(match.version + 1, item.matches.first { it.id == match.id }.version)
            }
        }
        assertEquals(TournamentStatus.COMPLETED, item.status)
        assertEquals(now, item.endDate)
        assertTrue(item.matches.all { it.result != null })
    }
    @Test fun `correction updates winner but stale or downstream writes fail`() {
        var item = tournament(8)
        val first = item.matches.first()
        item = TournamentRules.saveResult(item, "owner", first.id, result(first), 0, now)
        val current = item.matches.first()
        reject { TournamentRules.saveResult(item, "owner", first.id, result(current), 0, now) }
        item = TournamentRules.saveResult(item, "owner", first.id, result(current, 0, 2), current.version, now)
        assertEquals(current.awayTeamId, item.matches.first { it.id == "1_0" }.homeTeamId)
        val second = item.matches.first { it.id == "0_1" }
        item = TournamentRules.saveResult(item, "owner", second.id, result(second), second.version, now)
        val next = item.matches.first { it.id == "1_0" }
        item = TournamentRules.saveResult(item, "owner", next.id, result(next), next.version, now)
        reject { TournamentRules.saveResult(item, "owner", first.id, result(current), 2, now) }
        reject { TournamentRules.saveResult(item, null, first.id, result(current), 2, now) }
        reject { TournamentRules.saveResult(item, "viewer", first.id, result(current), 2, now) }
        reject { TournamentRules.saveResult(item.copy(status = TournamentStatus.DRAFT), "owner", first.id, result(current), 2, now) }
        reject { TournamentRules.saveResult(item, "owner", "unknown", result(current), 2, now) }
    }
    @Test fun `history respects calendar DST leap year and half open bounds`() {
        val week = TournamentRules.historyRange(now, false)
        assertEquals(Instant.parse("2026-10-18T22:00:00Z").toEpochMilli(), week.start)
        assertEquals(Instant.parse("2026-10-25T23:00:00Z").toEpochMilli(), week.end)
        assertEquals(169, (week.end - week.start) / 3_600_000)
        val month = TournamentRules.historyRange(Instant.parse("2024-02-15T00:00:00Z").toEpochMilli(), true)
        assertEquals(29, (month.end - month.start) / 86_400_000)
        assertEquals(week.end, TournamentRules.historyRange(now, false, 1).start)
        assertEquals(month.start, TournamentRules.historyRange(Instant.parse("2024-03-15T00:00:00Z").toEpochMilli(), true, -1).start)
        val item = tournament().copy(status = TournamentStatus.COMPLETED, endDate = week.start)
        val items = listOf(item, item.copy(id = "end", endDate = week.end), item.copy(id = "missing", endDate = null),
            item.copy(id = "active", status = TournamentStatus.ONGOING), item.copy(id = "archived", status = TournamentStatus.ARCHIVED))
        assertEquals(setOf("id", "archived"), TournamentRules.filter(items, null, week).map { it.id }.toSet())
        assertTrue(TournamentRules.filter(items, Sport.BASKETBALL, week).isEmpty())
        assertEquals(items.size, TournamentRules.filter(items, Sport.FOOTBALL).size)
    }
}
