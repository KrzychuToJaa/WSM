package pl.edu.slowscore.domain

interface ScoreRepository {
    val isDemo: Boolean
    suspend fun currentUser(): UserProfile?
    suspend fun signIn(email: String, password: String): UserProfile
    suspend fun signUp(email: String, password: String, username: String): UserProfile
    suspend fun finishProfile(username: String): UserProfile
    suspend fun resetPassword(email: String)
    suspend fun signOut()
    suspend fun useDemoUser(userId: String)
    suspend fun load(feed: Feed): List<Tournament>
    suspend fun get(id: String): Tournament
    suspend fun create(name: String, sport: Sport, startDate: Long, teamNames: List<String>): String
    suspend fun shuffle(id: String)
    suspend fun publish(id: String)
    suspend fun saveResult(id: String, matchId: String, result: MatchResult, version: Int)
    suspend fun addEditor(id: String, username: String)
    suspend fun removeEditor(id: String, userId: String)
    suspend fun archive(id: String)
    suspend fun follow(id: String, enabled: Boolean)
    suspend fun followedIds(): Set<String>
}
