package pl.edu.slowscore.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import pl.edu.slowscore.domain.*

/** Odczyt z Firestore; zapisy waliduje funkcja serwerowa score. */
class FirebaseRepository(app: FirebaseApp, emulator: Boolean = false) : ScoreRepository {
    override val isDemo = false
    private val auth = FirebaseAuth.getInstance(app)
    private val db = FirebaseFirestore.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(app, "europe-central2")
    private val gson = Gson()
    init {
        if (emulator) {
            auth.useEmulator("10.0.2.2", 9099)
            db.useEmulator("10.0.2.2", 8080)
            functions.useEmulator("10.0.2.2", 5001)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
    private fun uid() = requireNotNull(auth.currentUser?.uid) { "Zaloguj się, aby kontynuować." }
    private suspend fun call(action: String, args: Map<String, Any?> = emptyMap()): Any? = try {
        functions.getHttpsCallable("score").call(args + ("action" to action)).await().data
    } catch (error: CancellationException) { throw error }
    catch (error: FirebaseFunctionsException) {
        val message = when (error.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.PERMISSION_DENIED, FirebaseFunctionsException.Code.ALREADY_EXISTS,
            FirebaseFunctionsException.Code.NOT_FOUND, FirebaseFunctionsException.Code.ABORTED -> error.message
            else -> "Nie można połączyć się z serwerem. Sprawdź sieć i konfigurację Firebase."
        }
        throw IllegalStateException(message, error)
    }
    private suspend fun <T> account(block: suspend () -> T): T = try { block() }
    catch (error: FirebaseAuthException) {
        throw IllegalArgumentException(when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Nie można utworzyć konta dla tego adresu. Spróbuj się zalogować."
            "ERROR_WEAK_PASSWORD" -> "Wybierz silniejsze hasło (minimum 8 znaków)."
            "ERROR_INVALID_EMAIL" -> "Wpisz poprawny adres e-mail."
            "ERROR_TOO_MANY_REQUESTS" -> "Zbyt wiele prób. Spróbuj ponownie później."
            else -> "Logowanie nie powiodło się. Sprawdź e-mail i hasło."
        })
    }
    override suspend fun currentUser(): UserProfile? {
        val user = auth.currentUser ?: return null
        val profile = db.collection("users").document(user.uid).get(Source.SERVER).await()
        return UserProfile(user.uid, profile.getString("username").orEmpty())
    }
    override suspend fun signIn(email: String, password: String): UserProfile = account {
        auth.signInWithEmailAndPassword(email.trim(), password).await(); requireNotNull(currentUser())
    }
    override suspend fun signUp(email: String, password: String, username: String): UserProfile = account {
        require(password.length >= 8) { "Hasło musi mieć co najmniej 8 znaków." }
        val name = TournamentRules.username(username)
        auth.createUserWithEmailAndPassword(email.trim(), password).await(); finishProfile(name)
    }
    override suspend fun finishProfile(username: String): UserProfile {
        call("profile", mapOf("username" to TournamentRules.username(username))); return requireNotNull(currentUser())
    }
    override suspend fun resetPassword(email: String) = account { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
    override suspend fun signOut() = auth.signOut()
    override suspend fun useDemoUser(userId: String) { error("To konto działa wyłącznie w trybie demonstracyjnym.") }
    private fun parse(snapshot: DocumentSnapshot): Tournament {
        require(snapshot.exists()) { "Nie znaleziono turnieju." }
        return gson.fromJson(gson.toJson(snapshot.data), Tournament::class.java)
    }
    override suspend fun load(feed: Feed): List<Tournament> {
        val items = db.collection("tournaments")
        return when (feed) {
            Feed.PUBLIC -> items.whereIn("status", listOf("UPCOMING", "ONGOING")).get(Source.SERVER).await().documents.map(::parse)
            Feed.HISTORY -> { uid(); items.whereIn("status", listOf("COMPLETED", "ARCHIVED")).get(Source.SERVER).await().documents.map(::parse) }
            Feed.MINE -> {
                val userId = uid()
                (items.whereEqualTo("ownerId", userId).get(Source.SERVER).await().documents +
                    items.whereArrayContains("editorIds", userId).get(Source.SERVER).await().documents).distinctBy { it.id }.map(::parse)
            }
            Feed.FOLLOWED -> followedIds().mapNotNull { id ->
                val item = items.document(id).get(Source.SERVER).await(); if (item.exists()) parse(item) else null
            }
        }
    }
    override suspend fun get(id: String): Tournament = parse(db.collection("tournaments").document(id).get(Source.SERVER).await())
    override suspend fun create(name: String, sport: Sport, startDate: Long, teamNames: List<String>): String {
        val result = call("create", mapOf("name" to name, "sport" to sport.name, "startDate" to startDate, "teamNames" to teamNames)) as Map<*, *>
        return result["id"] as String
    }
    override suspend fun shuffle(id: String) { call("shuffle", mapOf("id" to id)) }
    override suspend fun publish(id: String) { call("publish", mapOf("id" to id)) }
    override suspend fun saveResult(id: String, matchId: String, result: MatchResult, version: Int) {
        val score = mapOf("homeScore" to result.homeScore, "awayScore" to result.awayScore,
            "winnerId" to result.winnerId, "sets" to result.sets.map { mapOf("home" to it.home, "away" to it.away) })
        call("result", mapOf("id" to id, "matchId" to matchId, "result" to score, "version" to version))
    }
    override suspend fun addEditor(id: String, username: String) { call("addEditor", mapOf("id" to id, "username" to username)) }
    override suspend fun removeEditor(id: String, userId: String) { call("removeEditor", mapOf("id" to id, "userId" to userId)) }
    override suspend fun archive(id: String) { call("archive", mapOf("id" to id)) }
    override suspend fun follow(id: String, enabled: Boolean) { call("follow", mapOf("id" to id, "enabled" to enabled)) }
    override suspend fun followedIds(): Set<String> = db.collection("users").document(uid()).collection("followed")
        .get(Source.SERVER).await().documents.map { it.id }.toSet()
}
