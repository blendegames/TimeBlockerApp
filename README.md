# FocusTask

A task manager that shows you ONE task at a time: on the home screen, in a widget and in a notification.

## How it works
- Every **time block** (e.g. 09:00-11:00) shows exactly one **todo list**. The block is named after its list.
- The task shown is chosen like this:
  1. The most overdue task, from any list or block (worst offender first).
  2. Otherwise, inside the active block: soonest deadline, then your manual order.
  3. Otherwise, tasks of lists with no time block ("anytime").
- No snooze. You change the list order instead.
- Tasks can have **subtasks** with their own deadline (they inherit the parent's when they have none).
  While you work on a subtask, the main task is shown above and the carousel cycles through the steps.
- Today shows a three-row **carousel**: the task you just finished (struck through), the current one and the next one.
- The **focus timer** sits above the tabs on every screen: 25 min work + 5 min break per cycle, a chime at each
  cycle, a softer cue when work and break swap, and a big red countdown before the block ends.
  All timings can be changed in Settings.
- After every block there is a mandatory **long break** (15 min by default). While it runs the other tabs are locked.
  When you finish everything early you can tap **Break until <next block> starts**: a long break that lasts exactly
  until then, however short or long.
- **Day** changes apply to today only. **Week** changes repeat every week.
- **Alerts** follow the phone: no notification permission means nothing plays, and silent or vibrate mode means
  vibration only. The round button next to the timer switches Sound and Vibrate.
- **Alert sounds**: pick one of the six built-in sounds or any audio file (MP3, OGG, WAV, M4A, FLAC) for the cycle chime,
  the work/break cue and the block-ending alert. Long files are cut at a limit you choose, with an optional fade.

## Project layout
| Folder | What is in it |
| --- | --- |
| `core/` | Pure Kotlin logic (task selection, cycle clock, breaks, alert planner, carousel). No Android code, fully unit tested. |
| `app/` | The Android app: Jetpack Compose screens, Room database, alarms, notification, Glance widgets. |

## Build it
- **Android Studio**: open this folder and press Run. (Needs JDK 17 and Android SDK 35.)
- **Command line**: `./gradlew :app:assembleDebug`, the APK lands in `app/build/outputs/apk/debug/`.
- **GitHub Actions**: every push builds the app. Open the run in the **Actions** tab and download the
  `focustask-debug-apk` artifact, then install it on your phone. The `screenshots` artifact has a picture of every screen.
- **Tests**: `./gradlew :core:test :app:testDebugUnitTest`

On first start the app fills itself with a few sample lists and blocks so you can see how it works. Delete them in Lists and Week.

## Status
- [x] Phase 1: core logic and tests (`core` module)
- [x] Phase 2: app screens (Today, Day, Week, Lists, Settings, Alert sounds, block editor)
- [x] Phase 3: widgets (small, large with carousel, block-ending and overdue looks)
- [x] Phase 4: notification with countdown and Done button
- [x] Phase 5: alert sounds, vibration, fade, long breaks

## Known limits
- Widgets show minutes (they refresh every minute), the app and the notification count seconds.
- Android 14+ may ask you to allow "alarms & reminders" so alerts arrive on the exact second. Settings shows a link when it is off.
- The text uses the phone's default font.
