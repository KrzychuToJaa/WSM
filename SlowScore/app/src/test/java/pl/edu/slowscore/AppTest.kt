package pl.edu.slowscore

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.edu.slowscore.domain.*
import pl.edu.slowscore.ui.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: ScoreViewModel
    private fun start(repository: ScoreRepository = DemoRepository()) {
        model = ScoreViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        compose.setContent { SlowScoreApp(model) }
        compose.waitForIdle()
    }
    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        try { node.performScrollTo() } catch (_: AssertionError) { /* Dolna nawigacja nie jest przewijana. */ }
        node.performClick(); compose.waitForIdle()
    }
    private fun action(block: suspend ScoreController.() -> Unit) { compose.runOnIdle { model.run(block) }; compose.waitForIdle() }

    @Test fun guestCanFilterBrowseAndReachLogin() {
        start()
        compose.onNodeWithText("Na kampusie").assertExists()
        click("Koszykówka"); compose.onNodeWithText("Liga wydziałów").assertExists()
        click("Siatkówka"); compose.onNodeWithText("Akademicka siatkówka").assertExists()
        click("Wszystkie"); click("Puchar kampusu")
        click("Drużyny"); compose.onNodeWithText("1. Informatyka").assertExists()
        click("Drabinka"); compose.onAllNodesWithText("Awans → runda 2, mecz 1").onFirst().assertExists()
        click("Mecze"); click("← Lista turniejów")
        click("Moje"); compose.onNodeWithText("Twoje konto").assertExists()
        click("organizer — właściciel"); compose.onNodeWithText("Zalogowano jako organizer").assertExists()
        click("Wyloguj"); compose.onNodeWithText("Zalogowano jako organizer").assertDoesNotExist()
    }
    @Test fun organizerCreatesPublishesAndFinishesATournament() {
        start(); action { demoUser("demo_owner") }
        click("Utwórz turniej")
        compose.onNodeWithText("Nazwa turnieju").performTextInput("Test Cup")
        compose.onNodeWithText("Drużyny — każda w osobnym wierszu").performTextInput("Team A\nTeam B\nTeam C\nTeam D")
        click("Zapisz szkic i utwórz drabinkę")
        compose.onNodeWithText("Test Cup").assertExists()
        click("Losuj pary ponownie")
        compose.onNodeWithText("Login edytora").performTextInput("editor")
        click("Nadaj uprawnienia")
        click("Odbierz"); click("Anuluj")
        click("Odbierz"); click("Potwierdź")
        click("Opublikuj turniej"); click("Potwierdź")
        click("Obserwuj turniej"); click("Przestań obserwować")
        // Pierwszy mecz zapisujemy przez rzeczywisty formularz.
        val item = model.controller.state.value.selected!!
        val firstMatch = item.matches.first()
        val homeName = item.teams.first { it.id == firstMatch.homeTeamId }.name
        val awayName = item.teams.first { it.id == firstMatch.awayTeamId }.name
        compose.onAllNodesWithText("Wpisz wynik").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Wynik: $homeName").performTextInput("2")
        compose.onNodeWithText("Wynik: $awayName").performTextInput("0")
        click("Zapisz wynik")
        compose.onNodeWithText("Wynik spotkania").assertDoesNotExist()
        action {
            val item = state.value.selected!!
            for (id in listOf("0_1", "1_0")) {
                val match = state.value.selected!!.matches.first { it.id == id }
                saveResult(match.id, MatchResult(2, 0, match.homeTeamId!!), match.version)
            }
        }
        click("Archiwizuj"); click("Potwierdź")
        assertEquals(TournamentStatus.ARCHIVED, model.controller.state.value.selected!!.status)
        click("Historia"); click("Miesiąc"); click("← Poprzedni"); click("Następny →"); click("Tydzień")
        click("Moje"); click("Śledzone")
        compose.onNodeWithText("Brak turniejów dla wybranych filtrów. Zmień dyscyplinę lub okres.").assertExists()
    }
    @Test fun resultFormRejectsInvalidValuesAndShowsVolleyballSets() {
        start(); action { demoUser("demo_owner"); open("sample_2") }
        compose.onAllNodesWithText("Wpisz wynik").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Sety, np. 25:20 — każdy w nowym wierszu").performTextInput("25:24\n25:0\n25:0")
        click("Zapisz wynik")
        compose.onNodeWithText("Set do 25 punktów wymaga przewagi dwóch punktów.").assertExists()
        compose.onNodeWithText("Sety, np. 25:20 — każdy w nowym wierszu").performTextReplacement("25:20\n25:21\n25:19")
        click("Zapisz wynik"); compose.onNodeWithText("25:20 • 25:21 • 25:19").assertExists()
    }
    @Test fun firebaseAccountFormsAndProfileRecoveryAreReachable() {
        val demo = DemoRepository()
        val repository = object : ScoreRepository by demo {
            override val isDemo = false
            override suspend fun signIn(email: String, password: String): UserProfile { demo.useDemoUser("demo_owner"); return demo.currentUser()!! }
            override suspend fun signUp(email: String, password: String, username: String) = signIn(email, password)
            override suspend fun resetPassword(email: String) {}
        }
        start(repository); click("Konto")
        compose.onNodeWithText("E-mail").performTextInput("user@example.com")
        compose.onNodeWithText("Hasło").performTextInput("password123")
        click("Pokaż hasło"); click("Załóż konto")
        compose.onNodeWithText("Login (a–z, cyfry, _)").performTextInput("player")
        click("Nie pamiętam hasła"); click("Utwórz konto")
        compose.onNodeWithText("Zalogowano jako organizer").assertExists()
        click("Wyloguj")
        compose.onNodeWithText("E-mail").performTextInput("user@example.com")
        compose.onNodeWithText("Hasło").performTextInput("password123")
        click("Zaloguj")
        compose.onNodeWithText("Zalogowano jako organizer").assertExists()
    }
    @Test fun invalidDraftPreservesInputAndCanBeCancelled() {
        start(); action { demoUser("demo_owner") }; click("Utwórz turniej")
        compose.onNodeWithText("Termin: dd.MM.rrrr GG:mm").performTextReplacement("bad date")
        click("Zapisz szkic i utwórz drabinkę"); compose.onNodeWithText("Format daty: dd.MM.rrrr GG:mm.").assertExists()
        click("Anuluj"); compose.onNodeWithText("Na kampusie").assertExists()
        action { open("missing") }; compose.onNodeWithText("Nie znaleziono turnieju.").assertExists()
        click("Odśwież dane")
    }
}
