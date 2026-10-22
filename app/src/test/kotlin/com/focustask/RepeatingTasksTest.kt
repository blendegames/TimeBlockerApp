package com.focustask

import androidx.test.core.app.ApplicationProvider
import com.focustask.core.Repeat
import com.focustask.core.RepeatUnit
import com.focustask.data.toRepeat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** "Permanent" tasks: complete them today and they come back, not done, subtasks included. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class RepeatingTasksTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()
    private val repo get() = app.repo

    // 2037-10-19 is a Monday (in the future, so the running app never resets these tasks by itself)
    private val monday2pm = LocalDateTime.of(2037, 10, 19, 14, 0)

    private suspend fun task(id: Long) = repo.snapshot().tasks.first { it.id == id }

    /** A repeating main task with two steps, everything completed on [doneAt]. */
    private suspend fun doneTask(rule: Repeat?, doneAt: LocalDateTime): Triple<Long, Long, Long> {
        val list = repo.addList("Routine ${System.nanoTime()}", 0)
        val main = repo.addTask(list, "Take vitamins", null, null, rule)
        val a = repo.addTask(list, "Morning", null, main)
        val b = repo.addTask(list, "Evening", null, main)
        repo.complete(main, doneAt)
        return Triple(main, a, b)
    }

    @Test fun aCompletedDailyTaskAndItsStepsComeBackTheNextDay() = runBlocking {
        val (main, a, b) = doneTask(Repeat(1, RepeatUnit.DAY), monday2pm)
        assertTrue(task(main).done); assertTrue(task(a).done); assertTrue(task(b).done)

        assertEquals("still the same day", 0, repo.resetRepeating(monday2pm.withHour(23).withMinute(59)))
        assertTrue(task(main).done)

        assertEquals(1, repo.resetRepeating(monday2pm.plusDays(1).toLocalDate().atStartOfDay()))
        listOf(main, a, b).forEach {
            assertFalse("task $it is open again", task(it).done)
            assertNull(task(it).completedAt)
        }
        assertEquals("the rule stays", Repeat(1, RepeatUnit.DAY), task(main).toRepeat())
        assertEquals("nothing left to reset", 0, repo.resetRepeating(monday2pm.plusDays(5)))
    }

    @Test fun everyTwoDaysWaitsTwoDays() = runBlocking {
        val (main, _, _) = doneTask(Repeat(2, RepeatUnit.DAY), monday2pm)
        assertEquals(0, repo.resetRepeating(monday2pm.plusDays(1).withHour(23)))
        assertTrue(task(main).done)
        assertEquals(1, repo.resetRepeating(monday2pm.plusDays(2).withHour(0)))
        assertFalse(task(main).done)
    }

    @Test fun everyWeekAndEveryMonth() = runBlocking {
        val (week, _, _) = doneTask(Repeat(1, RepeatUnit.WEEK), monday2pm)
        val (month, _, _) = doneTask(Repeat(1, RepeatUnit.MONTH), monday2pm)
        assertEquals(0, repo.resetRepeating(LocalDateTime.of(2037, 10, 25, 23, 59)))
        assertEquals(1, repo.resetRepeating(LocalDateTime.of(2037, 10, 26, 0, 0)))
        assertFalse(task(week).done); assertTrue(task(month).done)
        assertEquals(1, repo.resetRepeating(LocalDateTime.of(2037, 11, 19, 0, 0)))
        assertFalse(task(month).done)
    }

    @Test fun aTaskThatDoesNotRepeatIsLeftAlone() = runBlocking {
        val (main, a, _) = doneTask(null, monday2pm)
        assertEquals(0, repo.resetRepeating(monday2pm.plusYears(1)))
        assertTrue(task(main).done); assertTrue(task(a).done)
    }

    @Test fun anOpenRepeatingTaskKeepsItsProgress() = runBlocking {
        val list = repo.addList("Progress ${System.nanoTime()}", 0)
        val main = repo.addTask(list, "Clean", null, null, Repeat(1, RepeatUnit.DAY))
        val a = repo.addTask(list, "Kitchen", null, main)
        val b = repo.addTask(list, "Bath", null, main)
        repo.complete(a, monday2pm)
        assertEquals(0, repo.resetRepeating(monday2pm.plusDays(3)))
        assertTrue("a half-done task is not reset", task(a).done)
        assertFalse(task(b).done); assertFalse(task(main).done)
    }

    @Test fun onlyMainTasksRepeat() = runBlocking {
        val list = repo.addList("Steps ${System.nanoTime()}", 0)
        val main = repo.addTask(list, "Main", null)
        val step = repo.addTask(list, "Step", null, main, Repeat(1, RepeatUnit.DAY))
        assertNull(task(step).toRepeat())
        repo.setRepeat(step, Repeat(1, RepeatUnit.DAY))
        assertNull(task(step).toRepeat())
        repo.setRepeat(main, Repeat(3, RepeatUnit.WEEK))
        assertEquals(Repeat(3, RepeatUnit.WEEK), task(main).toRepeat())
        repo.setRepeat(main, null)
        assertNull(task(main).toRepeat())
    }

    @Test fun stoppingTheRepeatKeepsACompletedTaskCompleted() = runBlocking {
        val (main, _, _) = doneTask(Repeat(1, RepeatUnit.DAY), monday2pm)
        repo.setRepeat(main, null)
        assertEquals(0, repo.resetRepeating(monday2pm.plusDays(10)))
        assertTrue(task(main).done)
    }

    @Test fun makingACompletedTaskRepeatCountsFromWhenItWasCompleted() = runBlocking {
        val (main, _, _) = doneTask(null, monday2pm)
        repo.setRepeat(main, Repeat(1, RepeatUnit.DAY))
        assertEquals(1, repo.resetRepeating(monday2pm.plusDays(1).withHour(0)))
        assertFalse(task(main).done)
    }

    @Test fun reopenedByHandBeforeTheResetNothingHappens() = runBlocking {
        val (main, _, _) = doneTask(Repeat(1, RepeatUnit.DAY), monday2pm)
        repo.reopen(main)
        assertEquals(0, repo.resetRepeating(monday2pm.plusDays(2)))
        assertFalse(task(main).done)
    }
}
