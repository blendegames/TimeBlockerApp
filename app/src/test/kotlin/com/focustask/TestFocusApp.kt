package com.focustask

import com.focustask.data.Repository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The app as the tests run it: with example lists (Work, Admin, Home, Someday) to work with.
 * The real app has none of this; it starts empty.
 */
class TestFocusApp : FocusApp() {
    override suspend fun seedExampleData(repo: Repository) {
        if (repo.settingsStore.flow.first().seeded || !repo.hasNoUserData()) {
            repo.settingsStore.setSeeded()
            return
        }
        val now = LocalDateTime.now()
        fun at(days: Long, h: Int, m: Int = 0) = now.toLocalDate().plusDays(days).atTime(h, m)
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val work = repo.addList("Work", 0)
        val admin = repo.addList("Admin", 1)
        val home = repo.addList("Home", 2)
        repo.addList("Someday", 3)
        repo.saveBlock(null, work, LocalTime.of(9, 0), LocalTime.of(11, 0), weekdays, 0)
        repo.saveBlock(null, admin, LocalTime.of(11, 30), LocalTime.of(12, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1)
        repo.saveBlock(null, home, LocalTime.of(18, 0), LocalTime.of(20, 0), DayOfWeek.values().toSet(), 2)
        val budget = repo.addTask(work, "Write the Q3 budget summary", at(0, 12))
        repo.addTask(work, "Pull the Q3 numbers", at(0, 9, 30), budget)
        repo.addTask(work, "Draft the summary", at(0, 11), budget)
        repo.addTask(work, "Send it to Dana", at(0, 12), budget)
        repo.addTask(work, "Review pull request #42", at(1, 17))
        repo.addTask(work, "Email Dana about the contract", null)
        repo.addTask(admin, "Submit the expense report", at(2, 12))
        repo.addTask(admin, "Book the dentist", null)
        repo.addTask(home, "Pay the electricity bill", at(3, 19))
        repo.addTask(home, "Water the plants", null)
        repo.settingsStore.setSeeded()
    }
}
