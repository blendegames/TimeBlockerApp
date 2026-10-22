package com.focustask.data

import com.focustask.core.CycleSettings
import com.focustask.core.RepeatUnit
import com.focustask.core.UserBreak
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Version of the JSON layout. Every file carries it. When the layout changes, bump this and teach
 * [DataFiles.decode] to read the old version, so a copy made by an older app is never refused.
 */
const val DATA_SCHEMA = 1

const val FILE_MANIFEST = "manifest.json"
const val FILE_SETTINGS = "settings.json"
const val FILE_LISTS = "lists.json"
const val FILE_BLOCKS = "timeblocks.json"
const val FILE_CHANGES = "daychanges.json"
const val DIR_SOUNDS = "sounds"

/** Everything the user has set up, as plain rows. This is what the JSON files hold. */
data class DataSnapshot(
    val lists: List<ListEntity>,
    val tasks: List<TaskEntity>,
    val blocks: List<BlockEntity>,
    val weekdayTimes: List<WeekdayTimeEntity>,
    val overrides: List<OverrideEntity>,
    val settings: Settings,
) {
    val isEmpty: Boolean get() = lists.isEmpty() && tasks.isEmpty() && blocks.isEmpty()
}

/** What the manifest says about a copy, without reading the rest of it. */
data class CopyInfo(val savedAt: String, val appVersion: String, val lists: Int, val tasks: Int, val blocks: Int)

/** The copy cannot be used. [message] is meant for the person. */
class DataFormatException(message: String) : Exception(message)

/**
 * Turns the app's data into a handful of readable JSON files and back.
 *
 *  - manifest.json   what this copy is, when it was made, and how much it holds
 *  - settings.json   timings, alert mode, sounds, break
 *  - lists.json      every list with its tasks and their subtasks, in order
 *  - timeblocks.json every time block with its days and its per-weekday times
 *  - daychanges.json changes that apply to a single date
 *
 * Reading ignores fields it does not know and fills in fields that are missing, so a copy made by
 * an older or newer app still loads.
 */
object DataFiles {

    // ------------------------------------------------------------------ writing

    fun encode(s: DataSnapshot, appVersion: String, savedAt: LocalDateTime = LocalDateTime.now()): Map<String, String> {
        val files = linkedMapOf<String, String>()
        files[FILE_SETTINGS] = settingsJson(s.settings).toString(2)
        files[FILE_LISTS] = listsJson(s).toString(2)
        files[FILE_BLOCKS] = blocksJson(s).toString(2)
        files[FILE_CHANGES] = changesJson(s).toString(2)
        // The manifest goes last: a folder without it is a copy that was never finished.
        files[FILE_MANIFEST] = JSONObject()
            .put("format", "focustask-data")
            .put("schema", DATA_SCHEMA)
            .put("appVersion", appVersion)
            .put("savedAt", savedAt.withNano(0).toString())
            .put("lists", s.lists.size)
            .put("tasks", s.tasks.size)
            .put("blocks", s.blocks.size)
            .put("dayChanges", s.overrides.size)
            .put("files", JSONArray(listOf(FILE_SETTINGS, FILE_LISTS, FILE_BLOCKS, FILE_CHANGES)))
            .toString(2)
        return files
    }

    private fun settingsJson(s: Settings): JSONObject {
        val sounds = JSONObject()
        SoundSlot.values().forEach { slot ->
            val c = s.sounds[slot] ?: defaultSounds().getValue(slot)
            sounds.put(
                slot.name,
                JSONObject().put("source", portableSource(c.source)).put("label", c.label).put("enabled", c.enabled),
            )
        }
        return JSONObject()
            .put("schema", DATA_SCHEMA)
            .put("cycle", JSONObject()
                .put("workMin", s.cycle.workMin).put("shortBreakMin", s.cycle.shortBreakMin)
                .put("longBreakMin", s.cycle.longBreakMin).put("countdownMin", s.cycle.countdownMin)
                .put("longBreakAfterMin", s.cycle.longBreakAfterMin))
            .put("alerts", JSONObject().put("mode", s.alertMode.name).put("maxSoundSec", s.maxSoundSec).put("fade", s.fade))
            .put("sounds", sounds)
            .put("userBreak", s.userBreak?.let { JSONObject().put("from", it.from.toString()).put("until", it.until.toString()) } ?: JSONObject.NULL)
            .put("lockWarningDismissed", s.lockWarningDismissed)
            .put("keepScreenOn", s.keepScreenOn)
            .put("wallpaperColors", s.wallpaperColors)
            .put("dimWhenIdle", s.dimWhenIdle)
            .put("dimPercent", s.dimPercent)
            .put("dimAfterSec", s.dimAfterSec)
            .put("timerViewWhenIdle", s.timerViewWhenIdle)
            .put("timerViewAfterSec", s.timerViewAfterSec)
    }

    /** A sound the user added lives in the app's storage; in the copy it is "file:sounds/<name>". */
    fun portableSource(source: String): String =
        if (source.startsWith("file:")) "file:$DIR_SOUNDS/${File(source.removePrefix("file:")).name}" else source

    /** The names (inside the sounds folder) of the files a snapshot's settings point at. */
    fun soundFiles(settings: Settings): List<String> =
        settings.sounds.values.map { portableSource(it.source) }.filter { it.startsWith("file:$DIR_SOUNDS/") }
            .map { it.removePrefix("file:$DIR_SOUNDS/") }.distinct()

    private fun listsJson(s: DataSnapshot): JSONObject {
        val byList = s.tasks.groupBy { it.listId }
        val lists = JSONArray()
        s.lists.sortedWith(compareBy({ it.position }, { it.id })).forEach { l ->
            val own = (byList[l.id] ?: emptyList()).sortedWith(compareBy({ it.position }, { it.id }))
            val ids = own.map { it.id }.toSet()
            val subs = own.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
            // a step whose main task is missing is kept as a main task, so nothing is lost
            val top = own.filter { it.parentId == null || it.parentId !in ids }
            val tasks = JSONArray()
            top.forEach { t ->
                val o = taskJson(t)
                val steps = JSONArray()
                (subs[t.id] ?: emptyList()).forEach { steps.put(taskJson(it)) }
                o.put("subtasks", steps)
                tasks.put(o)
            }
            lists.put(JSONObject().put("id", l.id).put("title", l.title).put("position", l.position).put("color", l.color).put("tasks", tasks))
        }
        return JSONObject().put("schema", DATA_SCHEMA).put("lists", lists)
    }

    private fun taskJson(t: TaskEntity) = JSONObject()
        .put("id", t.id).put("title", t.title).put("position", t.position).put("done", t.done)
        .put("deadline", t.deadline?.fromStored()?.toString() ?: JSONObject.NULL)
        .put("completedAt", t.completedAt?.fromStored()?.toString() ?: JSONObject.NULL)
        .put("repeat", t.toRepeat()?.let { JSONObject().put("every", it.every).put("unit", it.unit.name) } ?: JSONObject.NULL)

    private fun blocksJson(s: DataSnapshot): JSONObject {
        val times = s.weekdayTimes.groupBy { it.blockId }
        val arr = JSONArray()
        s.blocks.sortedBy { it.id }.forEach { b ->
            val dayTimes = JSONObject()
            (times[b.id] ?: emptyList()).sortedBy { it.dayOfWeek }.forEach {
                dayTimes.put(DayOfWeek.of(it.dayOfWeek).name, JSONObject().put("start", clock(it.startMin)).put("end", clock(it.endMin)))
            }
            arr.put(
                JSONObject().put("id", b.id).put("listId", b.listId)
                    .put("start", clock(b.startMin)).put("end", clock(b.endMin))
                    .put("days", JSONArray(maskToDays(b.daysMask).sorted().map { it.name }))
                    .put("dayTimes", dayTimes),
            )
        }
        return JSONObject().put("schema", DATA_SCHEMA).put("blocks", arr)
    }

    private fun changesJson(s: DataSnapshot): JSONObject {
        val arr = JSONArray()
        s.overrides.sortedWith(compareBy({ it.epochDay }, { it.blockId })).forEach {
            arr.put(
                JSONObject().put("blockId", it.blockId).put("date", LocalDate.ofEpochDay(it.epochDay).toString())
                    .put("start", clock(it.startMin)).put("end", clock(it.endMin)).put("skipped", it.skipped),
            )
        }
        return JSONObject().put("schema", DATA_SCHEMA).put("changes", arr)
    }

    private fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    // ------------------------------------------------------------------ reading

    /** Reads the manifest only. */
    fun info(manifest: String?): CopyInfo {
        val m = parse(manifest ?: throw DataFormatException("This folder has no FocusTask copy in it."), FILE_MANIFEST)
        if (m.optString("format") != "focustask-data") throw DataFormatException("This is not a FocusTask copy.")
        checkSchema(m, FILE_MANIFEST)
        return CopyInfo(m.optString("savedAt"), m.optString("appVersion"), m.optInt("lists"), m.optInt("tasks"), m.optInt("blocks"))
    }

    /**
     * Reads a whole copy. [read] gives the text of a file or null when there is no such file.
     * Throws [DataFormatException] when the copy is unfinished, damaged or newer than this app.
     */
    fun decode(read: (String) -> String?): DataSnapshot {
        val manifest = parse(read(FILE_MANIFEST) ?: throw DataFormatException("This folder has no FocusTask copy in it."), FILE_MANIFEST)
        if (manifest.optString("format") != "focustask-data") throw DataFormatException("This is not a FocusTask copy.")
        checkSchema(manifest, FILE_MANIFEST)

        val settings = read(FILE_SETTINGS)?.let { settingsFrom(parse(it, FILE_SETTINGS)) } ?: Settings()

        val listsDoc = parse(read(FILE_LISTS) ?: throw DataFormatException("The copy is missing $FILE_LISTS."), FILE_LISTS)
        checkSchema(listsDoc, FILE_LISTS)
        val lists = mutableListOf<ListEntity>()
        val tasks = mutableListOf<TaskEntity>()
        val listIds = HashSet<Long>()
        val taskIds = HashSet<Long>()
        val ls = listsDoc.optJSONArray("lists") ?: JSONArray()
        for (i in 0 until ls.length()) {
            val l = ls.getJSONObject(i)
            val id = l.getLong("id")
            if (!listIds.add(id)) throw DataFormatException("$FILE_LISTS names list $id twice.")
            lists += ListEntity(id = id, title = l.optString("title", "List"), position = l.optInt("position", i), color = l.optInt("color", 0))
            val ts = l.optJSONArray("tasks") ?: JSONArray()
            for (j in 0 until ts.length()) {
                val t = ts.getJSONObject(j)
                val tid = t.getLong("id")
                if (!taskIds.add(tid)) throw DataFormatException("$FILE_LISTS names task $tid twice.")
                tasks += taskFrom(t, id, null, j)
                val steps = t.optJSONArray("subtasks") ?: JSONArray()
                for (k in 0 until steps.length()) {
                    val st = steps.getJSONObject(k)
                    val sid = st.getLong("id")
                    if (!taskIds.add(sid)) throw DataFormatException("$FILE_LISTS names task $sid twice.")
                    tasks += taskFrom(st, id, tid, k)
                }
            }
        }

        val blocks = mutableListOf<BlockEntity>()
        val weekday = mutableListOf<WeekdayTimeEntity>()
        read(FILE_BLOCKS)?.let { text ->
            val doc = parse(text, FILE_BLOCKS)
            checkSchema(doc, FILE_BLOCKS)
            val arr = doc.optJSONArray("blocks") ?: JSONArray()
            val seen = HashSet<Long>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                val id = b.getLong("id")
                val listId = b.getLong("listId")
                if (!seen.add(id)) throw DataFormatException("$FILE_BLOCKS names block $id twice.")
                if (listId !in listIds) continue     // a block of a list that is not in the copy shows nothing
                val days = b.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { runCatching { DayOfWeek.valueOf(a.getString(it)) }.getOrNull() }.toSet() } ?: emptySet()
                blocks += BlockEntity(id = id, listId = listId, startMin = minutes(b.getString("start")), endMin = minutes(b.getString("end")), daysMask = daysToMask(days))
                val dt = b.optJSONObject("dayTimes")
                dt?.keys()?.forEach { name ->
                    val day = runCatching { DayOfWeek.valueOf(name) }.getOrNull() ?: return@forEach
                    val o = dt.getJSONObject(name)
                    weekday += WeekdayTimeEntity(id, day.value, minutes(o.getString("start")), minutes(o.getString("end")))
                }
            }
        }

        val overrides = mutableListOf<OverrideEntity>()
        read(FILE_CHANGES)?.let { text ->
            val doc = parse(text, FILE_CHANGES)
            checkSchema(doc, FILE_CHANGES)
            val have = blocks.map { it.id }.toSet()
            val arr = doc.optJSONArray("changes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val blockId = c.getLong("blockId")
                if (blockId !in have) continue
                overrides += OverrideEntity(blockId, LocalDate.parse(c.getString("date")).toEpochDay(), minutes(c.getString("start")), minutes(c.getString("end")), c.optBoolean("skipped", false))
            }
        }

        // The manifest counts what was written; a smaller copy means the writing was cut short.
        val wantLists = manifest.optInt("lists", lists.size)
        val wantTasks = manifest.optInt("tasks", tasks.size)
        if (lists.size != wantLists || tasks.size != wantTasks) {
            throw DataFormatException("This copy looks unfinished: it should hold $wantLists lists and $wantTasks tasks but holds ${lists.size} and ${tasks.size}.")
        }
        return DataSnapshot(lists, tasks, blocks, weekday, overrides, settings)
    }

    private fun taskFrom(o: JSONObject, listId: Long, parentId: Long?, index: Int) = TaskEntity(
        id = o.getLong("id"), listId = listId, title = o.optString("title", "Task"),
        deadline = o.optStringOrNull("deadline")?.let { LocalDateTime.parse(it).toStored() },
        position = o.optInt("position", index), done = o.optBoolean("done", false), parentId = parentId,
        completedAt = o.optStringOrNull("completedAt")?.let { LocalDateTime.parse(it).toStored() },
        repeatEvery = o.optJSONObject("repeat")?.let { r -> r.optInt("every", 0).takeIf { it >= 1 && parentId == null } },
        repeatUnit = o.optJSONObject("repeat")?.let { r ->
            r.optString("unit", "").takeIf { u -> parentId == null && r.optInt("every", 0) >= 1 && RepeatUnit.entries.any { it.name == u } }
        },
    )

    private fun settingsFrom(o: JSONObject): Settings {
        checkSchema(o, FILE_SETTINGS)
        val d = CycleSettings()
        val defaults = defaultSounds()
        val cy = o.optJSONObject("cycle")
        val al = o.optJSONObject("alerts")
        val sd = o.optJSONObject("sounds")
        val ub = o.optJSONObject("userBreak")
        return Settings(
            cycle = CycleSettings(
                workMin = (cy?.optInt("workMin", d.workMin) ?: d.workMin).coerceIn(1, 180),
                shortBreakMin = (cy?.optInt("shortBreakMin", d.shortBreakMin) ?: d.shortBreakMin).coerceIn(0, 60),
                longBreakMin = (cy?.optInt("longBreakMin", d.longBreakMin) ?: d.longBreakMin).coerceIn(0, 120),
                countdownMin = (cy?.optInt("countdownMin", d.countdownMin) ?: d.countdownMin).coerceIn(0, 60),
                longBreakAfterMin = (cy?.optInt("longBreakAfterMin", d.longBreakAfterMin) ?: d.longBreakAfterMin).coerceIn(15, 1440),
            ),
            alertMode = al?.optString("mode")?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() } ?: AlertMode.SOUND,
            sounds = SoundSlot.values().associateWith { slot ->
                val def = defaults.getValue(slot)
                val c = sd?.optJSONObject(slot.name)
                SoundConfig(
                    source = c?.optString("source")?.takeIf { it.isNotEmpty() } ?: def.source,
                    label = c?.optString("label")?.takeIf { it.isNotEmpty() } ?: def.label,
                    enabled = c?.optBoolean("enabled", true) ?: true,
                )
            },
            maxSoundSec = (al?.optInt("maxSoundSec", 10) ?: 10).coerceIn(1, 60),
            fade = al?.optBoolean("fade", true) ?: true,
            userBreak = ub?.let { runCatching { UserBreak(LocalDateTime.parse(it.getString("from")), LocalDateTime.parse(it.getString("until"))) }.getOrNull() },
            lockWarningDismissed = o.optBoolean("lockWarningDismissed", false),
            keepScreenOn = o.optBoolean("keepScreenOn", false),
            wallpaperColors = o.optBoolean("wallpaperColors", false),
            dimWhenIdle = o.optBoolean("dimWhenIdle", true),
            dimPercent = o.optInt("dimPercent", 10).coerceIn(5, 80),
            dimAfterSec = o.optInt("dimAfterSec", 30).coerceIn(5, 1800),
            timerViewWhenIdle = o.optBoolean("timerViewWhenIdle", true),
            timerViewAfterSec = o.optInt("timerViewAfterSec", 60).coerceIn(10, 1800),
            seeded = true,
        )
    }

    private fun minutes(clock: String): Int {
        val (h, m) = clock.split(":").map { it.trim().toInt() }
        return (h * 60 + m).coerceIn(0, 24 * 60)
    }

    private fun parse(text: String, name: String): JSONObject =
        try { JSONObject(text) } catch (e: Exception) { throw DataFormatException("$name is damaged, so this copy cannot be used.") }

    private fun checkSchema(o: JSONObject, name: String) {
        val v = o.optInt("schema", 1)
        if (v > DATA_SCHEMA) throw DataFormatException("$name was made by a newer version of FocusTask. Update the app to open it.")
        // Older layouts are read here as they appear: if (v < 2) { ...upgrade o... }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
