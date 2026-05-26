package com.cj.tapblok

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRuleManagerTest {
    @Test
    fun overnightSleepBlockWindowIncludesLateNightAndEarlyMorning() {
        val start = 22 * 60
        val end = 5 * 60

        assertTrue(LocationRuleManager.isMinuteInSleepBlockWindow(23 * 60, start, end))
        assertTrue(LocationRuleManager.isMinuteInSleepBlockWindow(4 * 60 + 59, start, end))
        assertFalse(LocationRuleManager.isMinuteInSleepBlockWindow(5 * 60, start, end))
        assertFalse(LocationRuleManager.isMinuteInSleepBlockWindow(12 * 60, start, end))
    }

    @Test
    fun sameStartAndEndDoesNotCreateAllDayBlock() {
        assertFalse(LocationRuleManager.isMinuteInSleepBlockWindow(12 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun daytimeSleepBlockWindowOnlyIncludesMiddleOfWindow() {
        val start = 9 * 60
        val end = 17 * 60

        assertFalse(LocationRuleManager.isMinuteInSleepBlockWindow(8 * 60 + 59, start, end))
        assertTrue(LocationRuleManager.isMinuteInSleepBlockWindow(12 * 60, start, end))
        assertFalse(LocationRuleManager.isMinuteInSleepBlockWindow(17 * 60, start, end))
    }
}
