package com.caddie.study.runtime.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test
    fun `open app`() {
        val r = ActionParser.parse("open com.example.app")
        assertEquals(ActionParser.ActionType.OPEN_APP, r.type)
        assertEquals("com.example.app", r.descriptor)
        assertFalse(r.submit)
    }

    @Test
    fun `open app with component`() {
        val r = ActionParser.parse("open com.example.app/.MainActivity")
        assertEquals(ActionParser.ActionType.OPEN_APP, r.type)
        assertEquals("com.example.app/.MainActivity", r.descriptor)
    }

    @Test
    fun `open url`() {
        val r = ActionParser.parse("open_url https://maps.google.com/dir/A/B")
        assertEquals(ActionParser.ActionType.OPEN_URL, r.type)
        assertEquals("https://maps.google.com/dir/A/B", r.descriptor)
    }

    @Test
    fun `click single quoted label`() {
        val r = ActionParser.parse("click 'Senden'")
        assertEquals(ActionParser.ActionType.TAP, r.type)
        assertEquals("Senden", r.descriptor)
    }

    @Test
    fun `click double quoted label`() {
        val r = ActionParser.parse("click \"Senden\"")
        assertEquals(ActionParser.ActionType.TAP, r.type)
        assertEquals("Senden", r.descriptor)
    }

    @Test
    fun `click first quoted label`() {
        val r = ActionParser.parse("click first 'OK'")
        assertEquals(ActionParser.ActionType.TAP_FIRST, r.type)
        assertEquals("OK", r.descriptor)
    }

    @Test
    fun `click unquoted label`() {
        val r = ActionParser.parse("click Senden")
        assertEquals(ActionParser.ActionType.TAP, r.type)
        assertEquals("Senden", r.descriptor)
    }

    @Test
    fun `tap backward compat alias`() {
        val r = ActionParser.parse("tap 'Submit'")
        assertEquals(ActionParser.ActionType.TAP, r.type)
        assertEquals("Submit", r.descriptor)
    }

    @Test
    fun `input text quoted`() {
        val r = ActionParser.parse("input text 'Hello World'")
        assertEquals(ActionParser.ActionType.TYPE, r.type)
        assertEquals("Hello World", r.descriptor)
        assertFalse(r.submit)
    }

    @Test
    fun `input text quoted with submit`() {
        val r = ActionParser.parse("input text 'Hello' (submit)")
        assertEquals(ActionParser.ActionType.TYPE, r.type)
        assertEquals("Hello", r.descriptor)
        assertTrue(r.submit)
    }

    @Test
    fun `input text unquoted with submit`() {
        val r = ActionParser.parse("input text Max (submit)")
        assertEquals(ActionParser.ActionType.TYPE, r.type)
        assertEquals("Max", r.descriptor)
        assertTrue(r.submit)
    }

    @Test
    fun `replace text quoted`() {
        val r = ActionParser.parse("replace text 'new value'")
        assertEquals(ActionParser.ActionType.REPLACE, r.type)
        assertEquals("new value", r.descriptor)
    }

    @Test
    fun `replace text quoted with submit`() {
        val r = ActionParser.parse("replace text 'x' (submit)")
        assertTrue(r.submit)
    }

    @Test
    fun `capture transit arrival`() {
        val r = ActionParser.parse("capture transit arrival as 'arrival_time'")
        assertEquals(ActionParser.ActionType.CAPTURE_TRANSIT_ARRIVAL, r.type)
        assertEquals("arrival_time", r.descriptor)
    }

    @Test
    fun `capture transit duration`() {
        val r = ActionParser.parse("capture transit duration as 'dur'")
        assertEquals(ActionParser.ActionType.CAPTURE_TRANSIT_DURATION, r.type)
        assertEquals("dur", r.descriptor)
    }

    @Test
    fun `capture song recommendation`() {
        val r = ActionParser.parse("capture song recommendation as 'song'")
        assertEquals(ActionParser.ActionType.CAPTURE_SONG_RECOMMENDATION, r.type)
        assertEquals("song", r.descriptor)
    }

    @Test
    fun `scroll directions`() {
        assertEquals(ActionParser.ActionType.SCROLL, ActionParser.parse("scroll down").type)
        assertEquals("down", ActionParser.parse("scroll down").descriptor)
        assertEquals("up", ActionParser.parse("scroll up").descriptor)
        assertEquals("left", ActionParser.parse("scroll left").descriptor)
        assertEquals("right", ActionParser.parse("scroll right").descriptor)
    }

    @Test
    fun `press buttons`() {
        assertEquals(ActionParser.ActionType.PRESS, ActionParser.parse("press BACK").type)
        assertEquals("BACK", ActionParser.parse("press BACK").descriptor)
        assertEquals("HOME", ActionParser.parse("press HOME").descriptor)
        assertEquals("RECENT", ActionParser.parse("press RECENT").descriptor)
    }

    @Test
    fun `invalid action throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("do something weird") }
    }

    @Test
    fun `empty open throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("open ") }
    }

    @Test
    fun `invalid scroll direction throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("scroll sideways") }
    }

    @Test
    fun `invalid press button throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("press VOLUME") }
    }

    @Test
    fun `empty quoted label throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("click ''") }
    }

    @Test
    fun `unterminated quote throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("click 'unterminated") }
    }

    @Test
    fun `trailing junk after quoted label throws`() {
        assertThrows(IllegalArgumentException::class.java) { ActionParser.parse("click 'OK' extra") }
    }
}
