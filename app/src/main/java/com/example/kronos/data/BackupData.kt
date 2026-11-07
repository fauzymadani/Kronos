package com.example.kronos.data

import com.example.kronos.data.local.Category
import com.example.kronos.data.local.ClassSession
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.Holiday
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.Task
import com.example.kronos.lib.AssignmentType
import com.example.kronos.lib.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class BackupSettings(
    val rotationLength: Int,
    val semesterStart: LocalDate,
    val periods: List<ClassPeriod>,
    val themeMode: ThemeMode,
    val pureBlack: Boolean,
    val timeFormat: TimeFormat,
    val firstDayOfWeek: FirstDayOfWeek,
    val defaultReminderOffsetMinutes: Int,
    val taskRemindersEnabled: Boolean,
    val classRemindersEnabled: Boolean,
    val examRemindersEnabled: Boolean,
    val examDayBeforeEnabled: Boolean,
    val examHourBeforeEnabled: Boolean,
    val examReminderHour: LocalTime
)

data class BackupData(
    val subjects: List<Subject>,
    val classes: List<ClassSession>,
    val categories: List<Category>,
    val tasks: List<Task>,
    val exams: List<Exam>,
    val holidays: List<Holiday>,
    val settings: BackupSettings
)

object BackupJsonCodec {
    const val FORMAT_VERSION = 3
    const val DATABASE_SCHEMA_VERSION = 10

    fun encode(data: BackupData): String = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("databaseSchemaVersion", DATABASE_SCHEMA_VERSION)
        put("subjects", JSONArray(data.subjects.map { JSONObject().apply {
            put("id", it.id); put("name", it.name); putNullable("code", it.code); putNullable("color", it.color)
        } }))
        put("classes", JSONArray(data.classes.map { JSONObject().apply {
            put("id", it.id); put("subjectId", it.subjectId); put("dayOfWeek", it.dayOfWeek)
            put("startTime", it.startTime.toString()); put("endTime", it.endTime.toString())
            putNullable("room", it.room); putNullable("teacher", it.teacher); put("weekNumber", it.weekNumber)
            putNullable("notes", it.notes); put("reminderEnabled", it.reminderEnabled)
            putNullable("reminderOffsetMinutes", it.reminderOffsetMinutes)
        } }))
        put("categories", JSONArray(data.categories.map { JSONObject().apply {
            put("id", it.id); put("name", it.name); putNullable("color", it.color)
        } }))
        put("tasks", JSONArray(data.tasks.map { JSONObject().apply {
            put("id", it.id); put("title", it.title); putNullable("notes", it.notes)
            putNullable("dueDate", it.dueDate); putNullable("dueTime", it.dueTime)
            putNullable("subjectId", it.subjectId); putNullable("categoryId", it.categoryId)
            put("priority", it.priority); put("done", it.done); putNullable("reminderTime", it.reminderTime)
            put("status", it.status); put("assignmentType", it.assignmentType)
            putNullable("completedAt", it.completedAt)
        } }))
        put("exams", JSONArray(data.exams.map { JSONObject().apply {
            put("id", it.id); put("title", it.title); putNullable("subjectId", it.subjectId)
            put("date", it.date); putNullable("startTime", it.startTime); putNullable("endTime", it.endTime)
            putNullable("room", it.room)
            putNullable("seat", it.seat); putNullable("notes", it.notes)
        } }))
        put("holidays", JSONArray(data.holidays.map { JSONObject().apply {
            put("id", it.id); put("name", it.name); put("startDate", it.startDate)
            put("endDate", it.endDate); putNullable("notes", it.notes)
        } }))
        put("settings", JSONObject().apply {
            put("rotationLength", data.settings.rotationLength)
            put("semesterStart", data.settings.semesterStart)
            put("periods", JSONArray(data.settings.periods.map {
                JSONObject().put("name", it.name).put("start", it.start).put("end", it.end)
            }))
            put("themeMode", data.settings.themeMode.value)
            put("pureBlack", data.settings.pureBlack)
            put("timeFormat", data.settings.timeFormat.value)
            put("firstDayOfWeek", data.settings.firstDayOfWeek.value)
            put("defaultReminderOffsetMinutes", data.settings.defaultReminderOffsetMinutes)
            put("taskRemindersEnabled", data.settings.taskRemindersEnabled)
            put("classRemindersEnabled", data.settings.classRemindersEnabled)
            put("examRemindersEnabled", data.settings.examRemindersEnabled)
            put("examDayBeforeEnabled", data.settings.examDayBeforeEnabled)
            put("examHourBeforeEnabled", data.settings.examHourBeforeEnabled)
            put("examReminderHour", data.settings.examReminderHour)
        })
    }.toString()

    fun decode(json: String): BackupData {
        return try {
            val root = JSONObject(json)
            val format = root.requiredInt("formatVersion")
            if (format > FORMAT_VERSION) throw BackupValidationException("Backup format version $format is newer than supported version $FORMAT_VERSION.")
            if (format < 1) throw BackupValidationException("Unsupported backup format version $format.")
            val schema = root.requiredInt("databaseSchemaVersion")
            if (schema > DATABASE_SCHEMA_VERSION) throw BackupValidationException("Backup database schema version $schema is newer than supported version $DATABASE_SCHEMA_VERSION.")
            BackupData(
                subjects = root.array("subjects") { subject(it) },
                classes = root.array("classes") { classSession(it) },
                categories = root.array("categories") { category(it) },
                tasks = root.array("tasks") { task(it) },
                exams = root.array("exams") { exam(it) },
                holidays = root.array("holidays") { holiday(it) },
                settings = settings(root.getJSONObject("settings"))
            )
        } catch (e: BackupValidationException) {
            throw e
        } catch (e: Exception) {
            throw BackupValidationException("Invalid backup file: ${e.message ?: "missing or malformed data"}.", e)
        }
    }

    private fun subject(o: JSONObject) = Subject(o.requiredLong("id"), o.requiredString("name"), o.stringOrNull("code"), o.stringOrNull("color"))
    private fun category(o: JSONObject) = Category(o.requiredLong("id"), o.requiredString("name"), o.stringOrNull("color"))
    private fun classSession(o: JSONObject) = ClassSession(
        o.requiredLong("id"), o.requiredLong("subjectId"), o.requiredInt("dayOfWeek"),
        LocalTime.parse(o.requiredString("startTime")), LocalTime.parse(o.requiredString("endTime")),
        o.stringOrNull("room"), o.stringOrNull("teacher"), o.requiredInt("weekNumber"),
        o.stringOrNull("notes"), o.requiredBoolean("reminderEnabled"), o.intOrNull("reminderOffsetMinutes")
    )
    private fun task(o: JSONObject): Task {
        // Older backups have no status/assignmentType/completedAt: derive them from done.
        val status = o.intOrNull("status")?.let { TaskStatus.fromCode(it) }
            ?: if (o.requiredBoolean("done")) TaskStatus.DONE else TaskStatus.NOT_STARTED
        val assignmentType = o.intOrNull("assignmentType")?.let { AssignmentType.fromCode(it) }
            ?: AssignmentType.INDIVIDUAL
        return Task(
            o.requiredLong("id"), o.requiredString("title"), o.stringOrNull("notes"),
            o.dateOrNull("dueDate"), o.timeOrNull("dueTime"), o.longOrNull("subjectId"),
            o.longOrNull("categoryId"), o.requiredInt("priority"), status == TaskStatus.DONE,
            o.dateTimeOrNull("reminderTime"), status.code, assignmentType.code,
            o.longOrNull("completedAt")
        )
    }
    private fun exam(o: JSONObject) = Exam(
        o.requiredLong("id"), o.requiredString("title"), o.longOrNull("subjectId"),
        LocalDate.parse(o.requiredString("date")), o.timeOrNull("startTime"), o.timeOrNull("endTime"),
        o.stringOrNull("room"), o.stringOrNull("seat"), o.stringOrNull("notes")
    )
    private fun holiday(o: JSONObject) = Holiday(
        o.requiredLong("id"), o.requiredString("name"), LocalDate.parse(o.requiredString("startDate")),
        LocalDate.parse(o.requiredString("endDate")), o.stringOrNull("notes")
    )
    private fun settings(o: JSONObject) = BackupSettings(
        o.requiredInt("rotationLength"), LocalDate.parse(o.requiredString("semesterStart")),
        o.array("periods") { ClassPeriod(it.requiredString("name"), LocalTime.parse(it.requiredString("start")), LocalTime.parse(it.requiredString("end"))) },
        ThemeMode.fromStoredValue(o.requiredString("themeMode")), o.requiredBoolean("pureBlack"),
        TimeFormat.fromStoredValue(o.requiredString("timeFormat")), FirstDayOfWeek.fromStoredValue(o.requiredString("firstDayOfWeek")),
        o.requiredInt("defaultReminderOffsetMinutes"), o.requiredBoolean("taskRemindersEnabled"),
        o.requiredBoolean("classRemindersEnabled"), o.requiredBoolean("examRemindersEnabled"),
        o.requiredBoolean("examDayBeforeEnabled"), o.requiredBoolean("examHourBeforeEnabled"),
        LocalTime.parse(o.requiredString("examReminderHour"))
    )

    private fun JSONObject.putNullable(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)
    private fun JSONObject.requiredString(name: String) = optString(name, "").takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("missing $name")
    private fun JSONObject.requiredLong(name: String) = if (has(name)) getLong(name) else throw IllegalArgumentException("missing $name")
    private fun JSONObject.requiredInt(name: String) = if (has(name)) getInt(name) else throw IllegalArgumentException("missing $name")
    private fun JSONObject.requiredBoolean(name: String) = if (has(name)) getBoolean(name) else throw IllegalArgumentException("missing $name")
    private fun JSONObject.stringOrNull(name: String) = optString(name, null)
    private fun JSONObject.longOrNull(name: String) = if (isNull(name)) null else getLong(name)
    private fun JSONObject.intOrNull(name: String) = if (isNull(name)) null else getInt(name)
    private fun JSONObject.dateOrNull(name: String) = stringOrNull(name)?.let(LocalDate::parse)
    private fun JSONObject.timeOrNull(name: String) = stringOrNull(name)?.let(LocalTime::parse)
    private fun JSONObject.dateTimeOrNull(name: String) = stringOrNull(name)?.let(LocalDateTime::parse)
    private inline fun <T> JSONObject.array(name: String, transform: (JSONObject) -> T): List<T> {
        val array = getJSONArray(name)
        return (0 until array.length()).map { transform(array.getJSONObject(it)) }
    }
}

class BackupValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
