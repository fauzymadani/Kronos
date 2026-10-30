package com.example.kronos.lib

import android.graphics.Color
import androidx.core.graphics.toColorInt
import kotlin.math.pow

fun parseUserColor(value: String?, fallback: Int): Int =
    runCatching { value?.toColorInt() ?: fallback }.getOrDefault(fallback)

fun contrastingTextColor(background: Int): Int {
    val red = Color.red(background) / 255.0
    val green = Color.green(background) / 255.0
    val blue = Color.blue(background) / 255.0
    val luminance = listOf(red, green, blue).map {
        if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
        }.let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }
        return if (luminance > 0.179) Color.BLACK else Color.WHITE
    }
