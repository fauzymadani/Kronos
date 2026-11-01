package com.example.kronos.lib

import com.example.kronos.data.local.ClassSessionWithSubject

fun csvField(value: String?): String {
    val text = value.orEmpty()
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${text.replace("\"", "\"\"")}\""
    } else text
}

fun classesCsv(classes: List<ClassSessionWithSubject>): String = buildString {
    appendLine("subject,day,start,end,room,teacher,week number,notes")
    classes.forEach { item ->
        appendLine(
            listOf(
                item.subjectName, item.dayOfWeek.toString(), item.startTime, item.endTime,
                item.room, item.teacher, item.weekNumber.toString(), item.notes
            ).joinToString(",") { csvField(it) }
        )
    }
}
