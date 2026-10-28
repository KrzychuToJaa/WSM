package pl.edu.slowscore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.edu.slowscore.data.RepositoryFactory
import pl.edu.slowscore.domain.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalStoreTest {
    @Test fun demoDataSurvivesRepositoryRecreationWithoutPersistingLogin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("slowscore_demo", 0).edit().clear().commit()
        val first = RepositoryFactory.create(context, forceDemo = true)
        assertTrue(first.isDemo)
        first.useDemoUser("demo_owner")
        val id = first.create("Saved Cup", Sport.FOOTBALL, System.currentTimeMillis() + 60000, listOf("AA", "BB", "CC", "DD"))
        first.publish(id); first.follow(id, true)
        val second = RepositoryFactory.create(context, forceDemo = true)
        assertNull(second.currentUser())
        second.useDemoUser("demo_owner")
        assertEquals(first.get(id), second.get(id))
        assertTrue(id in second.followedIds())
    }
    @Test fun damagedLocalCacheFallsBackToSampleData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        for (badData in listOf("not json", "{}", "{\"tournaments\":[]}")) {
            context.getSharedPreferences("slowscore_demo", 0).edit().putString("data", badData).commit()
            val repository = RepositoryFactory.create(context, forceDemo = true)
            assertEquals(3, repository.load(Feed.PUBLIC).size)
        }
    }
}
