package pl.edu.slowscore.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TestRepositoryTest {
    private val now = 1_800_000_000_000L
    private var time = now
    private fun repo(persist: (TestData) -> Unit = {}) = TestRepository(clock = { time }, persist = persist)
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (error: Exception) { assertFalse(error.message.isNullOrBlank()) }
    }
    @Test fun `guests see public data only and testRepository accounts are explicit`() = runBlocking {
        val repo = repo()
        assertNull(repo.currentUser()); assertEquals(3, repo.load(Feed.PUBLIC).size)
        for (feed in listOf(Feed.MINE, Feed.FOLLOWED, Feed.HISTORY)) reject { repo.load(feed) }
        reject { repo.get("sample_past") }; reject { repo.get("missing") }
        reject { repo.create("Cup", Sport.FOOTBALL, now + 1, listOf("AA", "BB", "CC", "DD")) }
        reject { repo.useTestUser("missing") }; reject { repo.signIn("email", "password") }
        reject { repo.signUp("email", "password", "user") }; reject { repo.finishProfile("user") }; reject { repo.resetPassword("email") }
        repo.useTestUser("test_viewer"); assertEquals("viewer", repo.currentUser()!!.username)
        assertEquals(1, repo.load(Feed.HISTORY).size); assertTrue(repo.load(Feed.MINE).isEmpty())
        repo.signOut(); assertNull(repo.currentUser())
    }
    @Test fun `draft publish permissions follow and archive persist`() = runBlocking {
        var saved: TestData? = null
        val repo = repo { saved = it }
        repo.useTestUser("test_owner")
        val id = repo.create(" Campus Cup ", Sport.FOOTBALL, now + 100000, listOf("AA", "BB", "CC", "DD"))
        assertEquals("Campus Cup", repo.get(id).name)
        assertEquals(3, repo.load(Feed.PUBLIC).size)
        repo.shuffle(id); assertEquals(1, repo.get(id).version)
        reject { repo.follow(id, true) }
        reject { repo.addEditor(id, "organizer") }; reject { repo.addEditor(id, "unknown") }
        repo.addEditor(id, "EDITOR"); assertTrue("test_editor" in repo.get(id).editors)
        repo.useTestUser("test_editor"); assertEquals(id, repo.get(id).id)
        reject { repo.publish(id) }; reject { repo.shuffle(id) }; reject { repo.archive(id) }
        reject { repo.addEditor(id, "viewer") }; reject { repo.removeEditor(id, "test_owner") }
        repo.useTestUser("test_viewer"); reject { repo.get(id) }
        repo.useTestUser("test_owner"); repo.publish(id)
        reject { repo.publish(id) }; reject { repo.shuffle(id) }; reject { repo.archive(id) }
        repo.follow(id, true); assertEquals(setOf(id), repo.followedIds())
        assertEquals(id, repo.load(Feed.FOLLOWED).single().id)
        repo.follow(id, false); assertTrue(repo.load(Feed.FOLLOWED).isEmpty())
        repo.removeEditor(id, "test_editor")
        repo.useTestUser("test_editor")
        val match = repo.get(id).matches.first()
        reject { repo.saveResult(id, match.id, MatchResult(2, 0, match.homeTeamId!!), match.version) }
        repo.useTestUser("test_owner")
        repo.addEditor(id, "editor"); repo.useTestUser("test_editor")
        reject { repo.saveResult(id, match.id, MatchResult(2, 0, match.homeTeamId!!), match.version) }
        time = repo.get(id).startDate
        for (matchId in listOf("0_0", "0_1", "1_0")) {
            val current = repo.get(id).matches.first { it.id == matchId }
            repo.saveResult(id, current.id, MatchResult(2, 0, current.homeTeamId!!), current.version)
        }
        assertEquals(TournamentStatus.COMPLETED, repo.get(id).status)
        repo.useTestUser("test_owner"); repo.follow(id, true); repo.archive(id)
        assertEquals(TournamentStatus.ARCHIVED, repo.load(Feed.FOLLOWED).single().status)
        assertEquals(time, repo.get(id).endDate)
        assertTrue(saved!!.tournaments.any { it.id == id })
        val restored = TestRepository(savedData = saved, clock = { now })
        restored.useTestUser("test_owner"); assertEquals(repo.get(id), restored.get(id)); assertEquals(repo.followedIds(), restored.followedIds())
        reject { repo.publish("missing") }
    }
    @Test fun `failed persistence does not acknowledge or change data`() = runBlocking {
        val repo = repo { error("Disk full") }
        repo.useTestUser("test_owner")
        reject { repo.follow("sample_0", true) }
        assertTrue(repo.followedIds().isEmpty())
    }
}
