package pl.edu.slowscore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.edu.slowscore.data.firebase.FirebaseRepository
import pl.edu.slowscore.domain.*

@RunWith(AndroidJUnit4::class)
class FirebaseFlowTest {
    @Test fun realAndroidSdkCompletesFirebaseTournamentLifecycle() = runBlocking {
        assertTrue("Test wymaga -PfirebaseEmulator=true", BuildConfig.USE_FIREBASE_EMULATORS)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-slowscore")
            .setApiKey("AIzaSy" + "0".repeat(33)).setApplicationId("1:123456789:android:abcdef123456").build(), "integration-${System.nanoTime()}")
        val repo = FirebaseRepository(app, true)
        val suffix = System.currentTimeMillis().toString()
        val ownerEmail = "owner$suffix@example.com"
        val password = "test-password-123"
        val owner = repo.signUp(ownerEmail, password, "owner$suffix")
        assertEquals(owner, repo.currentUser())
        val id = repo.create("Android SDK Cup", Sport.FOOTBALL, System.currentTimeMillis() + 3600000,
            listOf("Team A", "Team B", "Team C", "Team D"))
        assertEquals(3, repo.get(id).matches.size)
        repo.shuffle(id); repo.publish(id)
        val editor = repo.signUp("editor$suffix@example.com", password, "editor$suffix")
        try {
            repo.addEditor(id, owner.username)
            fail("Obcy nie może nadać uprawnień")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("właściciel"))
        }
        repo.signIn(ownerEmail, password); repo.addEditor(id, editor.username)
        repo.signIn("editor$suffix@example.com", password)
        assertTrue(repo.load(Feed.MINE).any { it.id == id })
        repo.follow(id, true); assertTrue(id in repo.followedIds())
        for (matchId in listOf("0_0", "0_1", "1_0")) {
            val match = repo.get(id).matches.first { it.id == matchId }
            repo.saveResult(id, matchId, MatchResult(2, 0, match.homeTeamId!!), match.version)
        }
        assertEquals(TournamentStatus.COMPLETED, repo.get(id).status)
        assertTrue(repo.load(Feed.HISTORY).any { it.id == id })
        repo.signIn(ownerEmail, password); repo.archive(id)
        assertEquals(TournamentStatus.ARCHIVED, repo.get(id).status)
        repo.signOut(); assertNull(repo.currentUser())
        assertTrue(repo.load(Feed.PUBLIC).none { it.id == id })
        app.delete()
    }
}
