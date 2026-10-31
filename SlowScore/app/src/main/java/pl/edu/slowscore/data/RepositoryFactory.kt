package pl.edu.slowscore.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.gson.Gson
import pl.edu.slowscore.BuildConfig
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.data.firebase.FirebaseRepository

object RepositoryFactory {
    fun create(context: Context, forceDemo: Boolean = false): ScoreRepository {
        if (!forceDemo && BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            val app = FirebaseApp.getApps(context).find { it.name == "slowscore-emulator" }
                ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-slowscore")
                    // Syntaktycznie poprawne wartości testowe; to nie jest klucz projektu w chmurze.
                    .setApiKey("AIzaSy" + "0".repeat(33)).setApplicationId("1:123456789:android:abcdef123456").build(), "slowscore-emulator")
            return FirebaseRepository(app, true)
        }
        if (!forceDemo && FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            val app = FirebaseApp.getInstance()
            return FirebaseRepository(app)
        }
        val preferences = context.getSharedPreferences("slowscore_demo", Context.MODE_PRIVATE)
        val gson = Gson()
        val saved = runCatching {
            preferences.getString("data", null)?.let { text ->
                gson.fromJson(text, DemoData::class.java)?.also {
                    requireNotNull(it.tournaments)
                    requireNotNull(it.followed)
                }
            }
        }.getOrNull()
        return DemoRepository(savedData = saved, persist = { data ->
            check(preferences.edit().putString("data", gson.toJson(data)).commit()) { "Nie udało się zapisać danych demo." }
        })
    }
}
