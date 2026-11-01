package com.example.kronos.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {
    @Test
    fun escapesCsvSpecialCharacters() {
        assertEquals("\"Math, Advanced\"", csvField("Math, Advanced"))
        assertEquals("\"Teacher \"\"A\"\"\"", csvField("Teacher \"A\""))
        assertEquals("\"Room\n2\"", csvField("Room\n2"))
    }
}
