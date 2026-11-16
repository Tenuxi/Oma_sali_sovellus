package fi.omasali.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: SaliViewModel
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    @Before fun setup() {
        app.deleteDatabase("oma-sali.db")
        vm = SaliViewModel(app)
    }
    private fun awaitUi(condition: () -> Boolean) {
        compose.waitUntil(10000) {
            // Room invalidations resume on Android's paused main looper, outside Compose's clock.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            assertNull(vm.error)
            condition()
        }
    }
    private fun screenshot(name: String) {
        val directory = File("build/reports/ui").apply { mkdirs() }
        // PixelCopy waits for a hardware frame that Robolectric doesn't schedule. Draw the
        // actual laid-out Android/Compose view into a native bitmap instead.
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun compactScreensAndSetEntrySurviveExerciseSwitch() {
        var scene by mutableStateOf("home")
        var dark by mutableStateOf(true)
        var sid = ""
        var pid = ""
        compose.setContent {
            val rows by vm.rows.collectAsState()
            MaterialTheme(colorScheme = if(dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB5E875)) else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF426900))) {
                Surface {
                    if (vm.ready) when(scene) {
                        "home" -> Home(vm,rows,Settings(activeProgram=pid)) {}
                        "library" -> Page("Liikekirjasto") { ExerciseLibrary(vm,rows) }
                        "program" -> ProgramEditor(vm,rows,pid)
                        "workout" -> WorkoutEditor(vm,rows,Settings(),sid)
                        "stats" -> Statistics(vm,rows)
                    }
                }
            }
        }
        awaitUi { vm.ready }
        runBlocking {
            vm.repo.template(3)
            val p=vm.repo.all().first { it.kind=="program" }.value<TrainingProgram>()
            pid=p.id; sid=vm.repo.start(p.id,p.days.first().id)
        }
        awaitUi { vm.rows.value.any { it.id==sid } }
        compose.onNodeWithText("▶  Aloita treeni").assertExists()
        screenshot("01-home-dark")
        compose.runOnIdle { scene="library" }
        compose.onNodeWithText("Penkkipunnerrus").assertExists()
        screenshot("04-library-dark")
        compose.runOnIdle { scene="program" }
        compose.onNodeWithText("＋ Lisää liike").assertExists()
        screenshot("03-program-dark")
        compose.runOnIdle { scene="workout" }
        compose.onAllNodesWithContentDescription("Sarja 1: paino kg").assertCountEquals(1)
        compose.onNodeWithContentDescription("Sarja 1: paino kg").performTextReplacement("62,5")
        compose.onNodeWithContentDescription("Sarja 1: toistot").performTextReplacement("12")
        compose.onNodeWithContentDescription("Sarja 1 tehty").performClick()
        awaitUi { vm.rows.value.first { it.id==sid }.value<WorkoutSession>().exercises.first().sets.first().done }
        screenshot("02-workout-dark")
        val first=vm.rows.value.first { it.id==sid }.value<WorkoutSession>().exercises.first()
        compose.onNodeWithText("Seuraava liike →").performScrollTo().performClick()
        compose.runOnIdle { vm.openExercise(sid,first.id) }
        compose.onNodeWithContentDescription("Sarja 1: paino kg").assertTextEquals("62,5")
        compose.onNodeWithContentDescription("Sarja 1 tehty").assertIsOn()
        compose.runOnIdle { scene="stats"; vm.stats { it.copy(exerciseId=first.exercise.id) } }
        compose.onNodeWithText("62,5 kg").assertExists()
        screenshot("05-stats-dark")
        compose.runOnIdle { dark=false }
        screenshot("05-stats-light")
        compose.runOnIdle { scene="workout" }
        screenshot("02-workout-light")
        // Database reopen confirms the UI used the real persistence path, not only local text state.
        runBlocking {
            val independent=Repository(SaliDatabase.open(app))
            try {
                val set=independent.all().first { it.id==sid }.value<WorkoutSession>().exercises.first().sets.first()
                assertEquals(62500L,set.grams); assertEquals(12,set.reps); assertTrue(set.done)
            } finally { independent.db.close() }
        }
    }
}
