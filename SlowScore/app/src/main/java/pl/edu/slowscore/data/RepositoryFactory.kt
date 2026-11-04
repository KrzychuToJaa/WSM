package pl.edu.slowscore.data

import android.content.Context
import com.google.firebase.FirebaseApp
import pl.edu.slowscore.domain.ScoreRepository
import pl.edu.slowscore.data.firebase.FirebaseRepository

object RepositoryFactory {
    fun create(context: Context): ScoreRepository {
        val app = FirebaseApp.getApps(context).find { it.name == FirebaseApp.DEFAULT_APP_NAME }
        checkNotNull(app) { "Brak konfiguracji Firebase. Dodaj app/google-services.json i zbuduj aplikację ponownie." }
        return FirebaseRepository(app)
    }
}
