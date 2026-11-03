// DayTasks.kt: Eigene Tagesaufgaben mit Motivation durch das Wesen (reines Kotlin). Gegenstück: DayTasks.swift.
//
// Aufgaben sind einmalig oder wiederkehrend (täglich, Mo–Fr, Wochenende). Einmalige Aufgaben, die nicht erledigt
// wurden, wandern auf den nächsten Tag („von gestern“). Erledigt wird pro Tag gespeichert (doneDays), damit
// wiederkehrende Aufgaben jeden Tag neu starten. Tage = epochDay (lokales Datum, 1970-01-01 = 0, Donnerstag).

package com.example.osmon.engine

enum class TaskRepeat { ONCE, DAILY, WEEKDAYS, WEEKEND }

data class DayTask(
    val id: String,
    val title: String,
    val emoji: String = "⭐",
    val repeat: TaskRepeat = TaskRepeat.ONCE,
    val createdDay: Long,
    val doneDays: Set<Long> = emptySet(),
    val fromParent: Boolean = false          // von den Eltern gestellt (Eltern-Bereich): Kind kann sie nicht löschen
)

/** Wie weit der Tag ist – bestimmt, womit das Wesen motiviert. */
enum class TaskStage { EMPTY, START, GOING, HALF, ALL }

data class TaskProgress(val done: Int, val total: Int) {
    val stage: TaskStage get() = when {
        total == 0 -> TaskStage.EMPTY
        done == 0 -> TaskStage.START
        done >= total -> TaskStage.ALL
        done * 2 >= total -> TaskStage.HALF
        else -> TaskStage.GOING
    }
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

object DayTasks {
    const val MAX_TASKS = 12
    const val KEEP_DAYS = 60L          // so lange bleibt „erledigt“ gespeichert (für die Serie)

    /** 0 = Montag … 6 = Sonntag. */
    fun weekday(day: Long): Int = Math.floorMod(day + 3, 7L).toInt()

    /** Steht die Aufgabe an diesem Tag auf der Liste? */
    fun isDue(t: DayTask, day: Long): Boolean = when (t.repeat) {
        TaskRepeat.ONCE -> {
            val doneOn = t.doneDays.minOrNull()
            day >= t.createdDay && (doneOn == null || day <= doneOn)
        }
        TaskRepeat.DAILY -> day >= t.createdDay
        TaskRepeat.WEEKDAYS -> day >= t.createdDay && weekday(day) < 5
        TaskRepeat.WEEKEND -> day >= t.createdDay && weekday(day) >= 5
    }

    fun isDone(t: DayTask, day: Long) = day in t.doneDays

    /** Einmalige Aufgabe von einem früheren Tag, noch offen. */
    fun isCarried(t: DayTask, day: Long) = t.repeat == TaskRepeat.ONCE && t.createdDay < day && !isDone(t, day)

    /** Liste für den Tag: offene zuerst (übernommene ganz oben), dann erledigte; sonst Reihenfolge wie angelegt. */
    fun forDay(all: List<DayTask>, day: Long): List<DayTask> {
        val due = all.filter { isDue(it, day) }
        return due.sortedWith(compareBy<DayTask>({ isDone(it, day) }, { !isCarried(it, day) }))
    }

    fun progress(all: List<DayTask>, day: Long): TaskProgress {
        val due = all.filter { isDue(it, day) }
        return TaskProgress(due.count { isDone(it, day) }, due.size)
    }

    /** Abhaken / zurücknehmen. Alte Einträge (älter als KEEP_DAYS) fallen weg. */
    fun toggle(t: DayTask, day: Long): DayTask {
        val set = if (day in t.doneDays) t.doneDays - day else t.doneDays + day
        return t.copy(doneDays = set.filter { it > day - KEEP_DAYS }.toSet())
    }

    /**
     * Serie: aufeinanderfolgende Tage bis heute, an denen alles erledigt war (Tage ohne Aufgaben zählen nicht
     * und unterbrechen nicht). Ist heute noch nicht alles erledigt, zählt die Serie bis gestern.
     */
    fun streak(all: List<DayTask>, today: Long): Int {
        var n = 0
        var d = today
        val first = all.minOfOrNull { it.createdDay } ?: return 0
        if (progress(all, today).stage != TaskStage.ALL) d--
        while (d >= first && d > today - KEEP_DAYS) {
            val p = progress(all, d)
            when (p.stage) {
                TaskStage.ALL -> n++
                TaskStage.EMPTY -> {}
                else -> return n
            }
            d--
        }
        return n
    }

    /** Neue Aufgabe ist erlaubt (Titel nicht leer, höchstens MAX_TASKS an diesem Tag). */
    fun canAdd(all: List<DayTask>, day: Long, title: String) =
        title.isNotBlank() && all.count { isDue(it, day) } < MAX_TASKS

    /** Beim Laden: einmalige Aufgaben, die vor mehr als KEEP_DAYS erledigt wurden, aufräumen. */
    fun cleanup(all: List<DayTask>, today: Long): List<DayTask> = all.filterNot {
        it.repeat == TaskRepeat.ONCE && (it.doneDays.maxOrNull() ?: Long.MAX_VALUE) <= today - KEEP_DAYS
    }

    /** Text auswählen: wechselt mit Tag und Anzahl erledigter Aufgaben, bleibt aber beim Neuzeichnen gleich. */
    fun <T> pick(list: List<T>, day: Long, salt: Int): T? =
        if (list.isEmpty()) null else list[Math.floorMod(day * 31 + salt, list.size.toLong()).toInt()]

    /** Platzhalter {name} (Wesen) und {task} (Aufgabe) ersetzen. */
    fun fill(line: String, pet: String, task: String = "") = line.replace("{name}", pet).replace("{task}", task)
}
