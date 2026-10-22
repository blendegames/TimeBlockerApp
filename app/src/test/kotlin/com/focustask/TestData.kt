package com.focustask

import android.os.Looper
import com.focustask.data.Repository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking

/**
 * The settings store outlives a Robolectric test, the database does not, and the seeded blocks sit at
 * fixed hours. So make sure that something is due right now, whatever the clock says.
 */
object TestData {
    private fun waitFor(ms: Long, repo: Repository, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end && !ok()) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    fun ensure(app: FocusApp) = runBlocking {
        val repo = app.repo
        waitFor(2500, repo) { !repo.data.value?.tasks.isNullOrEmpty() }
        val now = LocalDateTime.now()
        val dueNow = repo.data.value?.blocksFor(now.toLocalDate())?.any { it.isActiveAt(now) } == true
        if (!dueNow) {
            val start = if (now.hour == 0) LocalTime.MIDNIGHT else now.toLocalTime().minusHours(1)
            val end = if (now.hour == 23) LocalTime.of(23, 59) else now.toLocalTime().plusHours(1)
            val list = repo.addList("Right now")
            repo.saveBlock(null, list, start, end, DayOfWeek.values().toSet(), 0)
            val main = repo.addTask(list, "Write the Q3 budget summary", now.plusHours(2))
            repo.addTask(list, "Pull the numbers", now.plusHours(1), main)
            repo.addTask(list, "Draft the summary", now.plusHours(1).plusMinutes(30), main)
            repo.addTask(list, "Review pull request #42", now.plusDays(1))
            repo.addTask(list, "Email Dana", null)
        }
        waitFor(5000, repo) {
            val d = repo.data.value
            d != null && d.blocksFor(now.toLocalDate()).any { it.isActiveAt(now) } && d.tasks.isNotEmpty()
        }
        // right after a long seeded block a long break would lock the app: no long breaks in these tests then
        if (repo.data.value!!.focusAt(now).mode == com.focustask.core.Mode.BREAK) {
            repo.settingsStore.setCycle(long = 0)
            waitFor(5000, repo) { repo.data.value!!.focusAt(LocalDateTime.now()).mode != com.focustask.core.Mode.BREAK }
        }
        repo.data.value!!
    }
}
