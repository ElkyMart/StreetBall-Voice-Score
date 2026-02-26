package com.streetball.voicescore.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberWordParserTest {

    private val parser = NumberWordParser()

    @Test
    fun `extractTwoScores parses plain number words`() {
        val result = parser.extractTwoScores("six seven")

        assertEquals(6 to 7, result)
    }

    @Test
    fun `extractTwoScores ignores filler words`() {
        val result = parser.extractTwoScores("score is ten nine game point")

        assertEquals(10 to 9, result)
    }

    @Test
    fun `extractTwoScores rejects phrases without exactly two numbers`() {
        assertNull(parser.extractTwoScores("check ball"))
        assertNull(parser.extractTwoScores("one two three"))
    }

    @Test
    fun `extractTwoScores parses numeric speech`() {
        assertEquals(10 to 8, parser.extractTwoScores("10 8"))
    }
}
