package com.focustask

import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The settings store outlives a Robolectric test, the database does not: fill it by hand if the seed was skipped. */
object TestData {
    fun ensure(app: FocusApp) = runBlocking {
        val repo = app.repo
        val end = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < end && repo.data.value?.tasks.isNullOrEmpty()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        if (repo.data.value?.tasks.isNullOrEmpty()) {
            val now = LocalDateTime.now()
            val list = repo.addList("Work")
            repo.saveBlock(null, list, now.toLocalTime().minusHours(1).let { if (it > now.toLocalTime()) java.time.LocalTime.of(0, 0) else it },
                now.toLocalTime().plusHours(1).let { if (it < now.toLocalTime()) java.time.LocalTime.of(23, 59) else it },
                DayOfWeek.values().toSet(), 0)
            val main = repo.addTask(list, "Write the Q3 budget summary", now.plusHours(2))
            repo.addTask(list, "Pull the numbers", now.plusHours(1), main)
            repo.addTask(list, "Draft the summary", now.plusHours(1).plusMinutes(30), main)
            repo.addTask(list, "Review pull request #42", now.plusDays(1))
            repo.addTask(list, "Email Dana", null)
        }
        val end2 = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end2 && repo.data.value?.tasks.isNullOrEmpty()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        repo.data.value!!
    }
}
