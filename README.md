# FocusTask

A task manager that shows you ONE task at a time.

## Rules
- Todo lists belong to a named time block (e.g. "Deep work", 09:00-11:00).
- The task shown is chosen like this:
  1. The most overdue task, from any list or block (worst offender first).
  2. Otherwise, inside the active block: soonest deadline, then your manual order.
  3. Otherwise, tasks of lists with no time block ("anytime").
- No snooze: you change the list order instead.

## Status
- [x] Phase 1: core logic and tests (`core` module)
- [ ] Phase 2: app screens
- [ ] Phase 3: widget
- [ ] Phase 4: notifications
- [ ] Phase 5: polish

## Checking the tests on GitHub
1. Create a new empty repository on github.com.
2. Upload all files from this folder (keep the `.github` folder).
3. Open the **Actions** tab. The "CI" workflow runs by itself; a green check means all tests passed.
