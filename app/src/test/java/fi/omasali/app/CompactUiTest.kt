package fi.omasali.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
        var width by mutableStateOf(360.dp)
        var chartPoints by mutableStateOf(emptyList<Pair<Long,Double>>())
        var sid = ""
        var pid = ""
        compose.setContent {
            val rows by vm.rows.collectAsState()
            MaterialTheme(colorScheme = if(dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB5E875)) else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF426900))) {
                Surface(Modifier.width(width)) {
                    if (vm.ready) when(scene) {
                        "home" -> Home(vm,rows,Settings(activeProgram=pid)) {}
                        "library" -> Page("Liikekirjasto") { ExerciseLibrary(vm,rows) }
                        "program" -> ProgramEditor(vm,rows,pid)
                        "workout" -> WorkoutEditor(vm,rows,Settings(),sid)
                        "stats" -> Statistics(vm,rows)
                        "chart" -> Page("Kehitys") { Box(Modifier.testTag("chart-card")) { CardBlock { Chart("Suurin paino",chartPoints,"kg") } } }
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
        // Reproduce the supplied partial FINISHED workout and update its history while Home stays open.
        compose.runOnIdle {
            vm.session(sid) { s -> s.copy(status=Status.FINISHED,ended=s.started+1000,exercises=s.exercises.mapIndexed { i,e ->
                if(i==0) e.copy(sets=e.sets.mapIndexed { n,set -> set.copy(grams=if(n==0) 6000 else 5000,reps=if(n==0) 3 else 2,done=n<2) }) else e
            }) }
        }
        awaitUi { vm.rows.value.first { it.id==sid }.value<WorkoutSession>().status==Status.FINISHED }
        compose.runOnIdle { scene="home"; dark=true }
        compose.onNodeWithTag("last-workout-$sid").assertExists()
        compose.onNodeWithTag("last-workout-counts").assertTextEquals("0/3 liikettä valmiina · 2 sarjaa tehty")
        screenshot("06-home-partial-finished")
        compose.runOnIdle { vm.session(sid) { s -> s.copy(exercises=s.exercises.mapIndexed { i,e -> if(i==0) e.copy(sets=e.sets.mapIndexed { n,set -> if(n==1) set.copy(done=false) else set }) else e }) } }
        awaitUi { summarize(vm.rows.value.first { it.id==sid }.value<WorkoutSession>()).completedSets==1 }
        compose.onNodeWithTag("last-workout-counts").assertTextEquals("0/3 liikettä valmiina · 1 sarjaa tehty")
        compose.runOnIdle {
            vm.session(sid) { s -> s.copy(exercises=s.exercises.mapIndexed { i,e -> when(i) {
                0 -> e.copy(sets=e.sets.mapIndexed { n,set -> if(n==1) set.copy(done=true) else set })
                2 -> e.copy(exercise=first.exercise,target=e.target.copy(exerciseId=first.exercise.id))
                else -> e
            } }) }
        }
        awaitUi { summarize(vm.rows.value.first { it.id==sid }.value<WorkoutSession>()).completedSets==2 }
        assertEquals(28000L,vm.rows.value.first { it.id==sid }.value<WorkoutSession>().exercises.sumOf(::volume))
        compose.runOnIdle { scene="workout"; width=320.dp }
        compose.onNodeWithTag("active-exercise-${first.id}").assertExists()
        compose.onAllNodesWithText("1. Penkkipunnerrus").assertCountEquals(1)
        compose.onNodeWithText("3. Penkkipunnerrus").assertExists()
        compose.onNodeWithTag("workout-progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(0f,0f..1f)))
        compose.onNodeWithContentDescription("Sarja 1: paino kg").assertTextEquals("6")
        screenshot("07-workout-320-partial-dark")
        compose.runOnIdle { dark=false }
        screenshot("08-workout-320-partial-light")
        compose.runOnIdle { scene="chart" }
        compose.onNodeWithText("Ei kirjattuja suorituksia valitulla aikavälillä.").assertExists()
        compose.onNodeWithTag("development-chart").assertDoesNotExist()
        compose.runOnIdle { chartPoints=listOf(1_700_000_000_000L to 3.0) }
        compose.onNodeWithText("3 kg").assertExists()
        compose.onNodeWithTag("single-measurement").assertExists()
        compose.onNodeWithTag("development-chart").assertDoesNotExist()
        compose.onNodeWithText("← Edellinen piste").assertDoesNotExist()
        assertTrue("Single measurement card must stay compact", compose.onNodeWithTag("chart-card").fetchSemanticsNode().boundsInRoot.height <= with(compose.density) { 180.dp.toPx() })
        screenshot("09-single-point-320")
        compose.runOnIdle { dark=true; chartPoints=chartPoints+(1_700_086_400_000L to 4.0) }
        compose.onNodeWithTag("development-chart").assertExists().performTouchInput { click(androidx.compose.ui.geometry.Offset(0f,center.y)) }
        compose.onNodeWithText("${date(1_700_000_000_000L)} · 3 kg").assertExists()
        screenshot("10-multiple-points-320")
    }
}
