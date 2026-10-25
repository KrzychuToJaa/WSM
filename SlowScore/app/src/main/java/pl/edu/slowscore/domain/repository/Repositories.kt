package pl.edu.slowscore.domain.repository

import pl.edu.slowscore.domain.model.*

// Kontrakty startowe. Autoryzacja musi działać także w regułach Firebase.
interface AuthRepository {
    suspend fun signIn(email: String, password: String): UserProfile
    suspend fun signUp(email: String, password: String, username: String): UserProfile
    suspend fun getCurrentUser(): UserProfile?
    suspend fun signOut()
}

interface TournamentRepository {
    suspend fun getPublished(sport: Sport? = null): List<Tournament>
    suspend fun getById(id: String): Tournament?
    suspend fun create(name: String, sport: Sport, startDate: java.time.Instant): String
    suspend fun getHistory(range: HistoryRange, sport: Sport? = null): List<Tournament>
    suspend fun getMyTournaments(): List<Tournament>
    suspend fun archive(id: String)
}

interface TeamRepository {
    suspend fun getAll(tournamentId: String): List<Team>
    suspend fun add(tournamentId: String, name: String): String
}

interface MatchRepository {
    suspend fun getAll(tournamentId: String): List<Match>
    suspend fun saveBracket(tournamentId: String, matches: List<Match>)
    suspend fun saveResult(
        tournamentId: String,
        matchId: String,
        result: MatchResult,
        expectedVersion: Long,
    )
}

interface PermissionRepository {
    suspend fun addEditor(tournamentId: String, username: String)
    suspend fun removeEditor(tournamentId: String, userId: String)
}

interface FollowRepository {
    suspend fun follow(tournamentId: String)
    suspend fun unfollow(tournamentId: String)
    suspend fun getFollowed(): List<Tournament>
}
