package com.example.kronos.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizationTest {
    private data class Entry(val id: Long, val name: String)

    @Test
    fun sameNameDifferentCaseCountsAsOne() {
        val store = store()
        store.upsert(Entry(1, "Math"))
        store.upsert(Entry(2, " math "))
        assertEquals(1, store.values().size)
        assertEquals(1L, store.values().single().id)
    }

    @Test
    fun whitespaceIsTrimmed() {
        val store = store()
        store.upsert(Entry(1, "  Physics  "))
        assertEquals("Physics", store.values().single().name)
    }

    @Test
    fun deletingCategoryInUseDoesNotDeleteTask() {
        data class Task(val id: Long, val categoryId: Long?)
        val tasks = mutableListOf(Task(7, 1))
        val categories = mutableListOf(1L)
        categories.remove(1L)
        tasks.replaceAll { it.copy(categoryId = null) }
        assertEquals(1, tasks.size)
        assertEquals(null, tasks.single().categoryId)
    }

    private fun store() = NormalizedNameStore(
        key = Entry::id,
        name = Entry::name,
        replace = { entry, name -> entry.copy(name = name) }
    )
}
