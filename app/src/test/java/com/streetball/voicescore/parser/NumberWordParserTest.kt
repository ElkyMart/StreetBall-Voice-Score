package com.streetball.voicescore.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberWordParserTest {

    private val parser = NumberWordParser()

    @Test
    fun parsesSimpleTwoTwo() {
        assertEquals(2 to 2, parser.extractTwoScores("two two"))
    }

    @Test
    fun parsesHomophoneToTo() {
        assertEquals(2 to 2, parser.extractTwoScores("to to"))
    }

    @Test
    fun parsesMergedTokenTwotwo() {
        assertEquals(2 to 2, parser.extractTwoScores("twotwo"))
    }

    @Test
    fun parsesCompactDigits() {
        assertEquals(2 to 2, parser.extractTwoScores("22"))
    }

    @Test
    fun parsesFourSingleDigitsAsTwoScores() {
        assertEquals(20 to 11, parser.extractTwoScores("two zero one one"))
    }

    @Test
    fun rejectsSingleDetectedScore() {
        assertNull(parser.extractTwoScores("twenty one"))
    }
}
