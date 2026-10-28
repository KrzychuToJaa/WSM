package pl.edu.slowscore.domain

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ScoreControllerTest {
    private val now = 1_800_000_000_000L
    private fun controller() = ScoreController(DemoRepository(clock = { now }), clock = { now })
    @Test fun `guest filters periods detail and errors preserve state`() = runBlocking {
        val c = controller()
        c.refresh(); assertEquals(3, c.visibleItems.size)
        c.filter(Sport.FOOTBALL); assertEquals(1, c.visibleItems.size)
        c.filter(null); c.period(true, -1); assertTrue(c.state.value.monthly); assertEquals(-1, c.state.value.offset)
        c.feed(Feed.HISTORY); assertNotNull(c.state.value.error); assertEquals(Feed.PUBLIC, c.state.value.feed)
        c.clearNotice(); assertNull(c.state.value.error)
        c.open("sample_0"); assertEquals("sample_0", c.state.value.selected!!.id)
        c.refresh(); assertNotNull(c.state.value.selected)
        c.close(); assertNull(c.state.value.selected)
        c.open("missing"); assertNotNull(c.state.value.error); assertFalse(c.state.value.busy)
        c.shuffle(); assertNotNull(c.state.value.error)
        c.signIn("email", "pass"); assertNotNull(c.state.value.error)
        c.signUp("email", "short", "viewer"); assertNotNull(c.state.value.error)
        c.signUp("email", "password123", "viewer"); assertNotNull(c.state.value.error)
        c.finishProfile("viewer"); assertNotNull(c.state.value.error)
        c.resetPassword("invalid"); assertNotNull(c.state.value.error)
    }
    @Test fun `organizer complete tournament flow reloads lists and permissions`() = runBlocking {
        val c = controller()
        c.demoUser("demo_owner"); assertEquals("organizer", c.state.value.user!!.username)
        c.feed(Feed.MINE); assertEquals(4, c.visibleItems.size)
        c.create("New Cup", Sport.BASKETBALL, now + 10000, listOf("AA", "BB", "CC", "DD"))
        val id = c.state.value.selected!!.id
        c.shuffle(); c.addEditor("viewer"); assertTrue("demo_viewer" in c.state.value.selected!!.editors)
        c.removeEditor("demo_viewer"); assertFalse("demo_viewer" in c.state.value.selected!!.editors)
        c.publish(); assertEquals(TournamentStatus.UPCOMING, c.state.value.selected!!.status)
        c.follow(); assertTrue(id in c.state.value.followed)
        c.follow(); assertFalse(id in c.state.value.followed)
        for (matchId in listOf("0_0", "0_1", "1_0")) {
            val match = c.state.value.selected!!.matches.first { it.id == matchId }
            c.saveResult(matchId, MatchResult(80, 60, match.homeTeamId!!), match.version)
            assertNull(c.state.value.error)
        }
        c.archive(); assertEquals(TournamentStatus.ARCHIVED, c.state.value.selected!!.status)
        assertEquals("Zapisano zmiany.", c.state.value.message)
        c.feed(Feed.HISTORY); assertTrue(c.visibleItems.any { it.id == id })
        c.signOut(); assertNull(c.state.value.user); assertEquals(Feed.PUBLIC, c.state.value.feed); assertNull(c.state.value.selected)
    }
    @Test fun `account successes and cancellation are handled without fake success`() = runBlocking {
        val demo = DemoRepository(clock = { now })
        val repository = object : ScoreRepository by demo {
            override suspend fun signIn(email: String, password: String): UserProfile { demo.useDemoUser("demo_owner"); return demo.currentUser()!! }
            override suspend fun signUp(email: String, password: String, username: String) = signIn(email, password)
            override suspend fun finishProfile(username: String) = signIn("", "")
            override suspend fun resetPassword(email: String) {}
        }
        val c = ScoreController(repository) { now }
        c.signIn("a@b.pl", "password"); assertNotNull(c.state.value.user)
        c.signOut(); c.signUp("a@b.pl", "password", "owner"); assertNotNull(c.state.value.user)
        c.finishProfile("owner"); c.resetPassword("a@b.pl"); assertNotNull(c.state.value.message)
        c.clearNotice(); assertNull(c.state.value.message)
        val cancel = ScoreController(object : ScoreRepository by demo { override suspend fun currentUser(): UserProfile? { throw CancellationException() } })
        try { cancel.refresh(); fail("Cancellation expected") } catch (_: CancellationException) { assertFalse(cancel.state.value.busy) }
    }
    @Test fun `double submit is blocked while repository is running`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val demo = DemoRepository(clock = { now })
        var calls = 0
        val c = ScoreController(object : ScoreRepository by demo {
            override suspend fun currentUser(): UserProfile? { calls++; started.complete(Unit); gate.await(); return null }
        })
        val first = launch { c.refresh() }; started.await()
        assertTrue(c.state.value.busy); c.refresh(); assertEquals(1, calls)
        gate.complete(Unit); first.join(); assertFalse(c.state.value.busy)
    }
}
