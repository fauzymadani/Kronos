package com.example.kronos.lib

fun normalizeName(value: String): String = value.trim().lowercase()

class NormalizedNameStore<T>(
    private val key: (T) -> Long,
    private val name: (T) -> String,
    private val replace: (T, String) -> T
) {
    private val values = linkedMapOf<Long, T>()

    fun upsert(value: T): T {
        val normalized = normalizeName(name(value))
        require(normalized.isNotEmpty())
        val existing = values.values.firstOrNull { normalizeName(name(it)) == normalized }
        return if (existing == null) {
            val stored = replace(value, name(value).trim())
            values[key(stored)] = stored
            stored
        } else {
            replace(existing, name(value).trim()).also { values[key(existing)] = it }
        }
    }

    fun values(): List<T> = values.values.toList()
}
