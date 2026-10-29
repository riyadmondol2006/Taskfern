package com.riyadmondol2006.taskfern.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdatePolicyTest {
    private val day = UpdatePolicy.CHECK_INTERVAL_MILLIS
    private val now = 1_800_000_000_000L

    private fun info(code: Int) = ReleaseInfo("1.0.$code", code, "", "https://x/a.apk", "https://x/tag")

    @Test
    fun onlyAHigherVersionCodeIsNewer() {
        assertTrue(UpdatePolicy.isNewer(43, 42))
        assertFalse(UpdatePolicy.isNewer(42, 42))
        assertFalse(UpdatePolicy.isNewer(41, 42))
    }

    @Test
    fun evaluateComparesWithTheInstalledVersion() {
        val release = ParsedRelease.Stable(info(42))
        assertEquals(UpdateResult.Available(info(42)), UpdatePolicy.evaluate(release, installedCode = 41))
        assertEquals(UpdateResult.UpToDate, UpdatePolicy.evaluate(release, installedCode = 42))
        assertEquals(UpdateResult.UpToDate, UpdatePolicy.evaluate(release, installedCode = 50))
        assertEquals(UpdateResult.UpToDate, UpdatePolicy.evaluate(ParsedRelease.NotStable, installedCode = 1))
        assertEquals(
            UpdateResult.Failed("bad", reachedServer = true),
            UpdatePolicy.evaluate(ParsedRelease.Malformed("bad"), installedCode = 1),
        )
    }

    @Test
    fun parsedJsonEndToEnd() {
        val json = """{"tag_name": "v1.0.9", "html_url": "https://github.com/o/r/releases/tag/v1.0.9", "draft": false, "prerelease": false}"""
        assertTrue(UpdatePolicy.evaluate(ReleaseParser.parse(json), installedCode = 8) is UpdateResult.Available)
        assertEquals(UpdateResult.UpToDate, UpdatePolicy.evaluate(ReleaseParser.parse(json), installedCode = 9))
        assertEquals(UpdateResult.UpToDate, UpdatePolicy.evaluate(ReleaseParser.parse(json), installedCode = 10))
    }

    @Test
    fun neverChecksUnlessTheUserTurnedItOn() {
        assertFalse(UpdatePolicy.isCheckDue(now, lastCheckAt = 0, enabled = null))
        assertFalse(UpdatePolicy.isCheckDue(now, lastCheckAt = 0, enabled = false))
        assertTrue(UpdatePolicy.isCheckDue(now, lastCheckAt = 0, enabled = true))
    }

    @Test
    fun checksAtMostOnceADay() {
        assertFalse(UpdatePolicy.isCheckDue(now, lastCheckAt = now, enabled = true))
        assertFalse(UpdatePolicy.isCheckDue(now, lastCheckAt = now - day + 1, enabled = true))
        assertTrue(UpdatePolicy.isCheckDue(now, lastCheckAt = now - day, enabled = true))
        assertTrue(UpdatePolicy.isCheckDue(now, lastCheckAt = now - 10 * day, enabled = true))
    }

    @Test
    fun aClockSetBackDoesNotBlockChecksForever() {
        assertTrue(UpdatePolicy.isCheckDue(now, lastCheckAt = now + 365 * day, enabled = true))
    }

    @Test
    fun skippedVersionsAreNotPromptedButNewerOnesAre() {
        val available = UpdateResult.Available(info(42))
        assertEquals(info(42), UpdatePolicy.promptFor(available, skippedCode = 0))
        assertEquals(info(42), UpdatePolicy.promptFor(available, skippedCode = 41))
        assertNull(UpdatePolicy.promptFor(available, skippedCode = 42))
        assertNull(UpdatePolicy.promptFor(available, skippedCode = 50))
        assertNull(UpdatePolicy.promptFor(UpdateResult.UpToDate, skippedCode = 0))
        assertNull(UpdatePolicy.promptFor(UpdateResult.NoReleaseYet, skippedCode = 0))
        assertNull(UpdatePolicy.promptFor(UpdateResult.Failed("x", reachedServer = false), skippedCode = 0))
    }

    @Test
    fun responsesAboveTheSizeCapAreRejected() {
        val small = ByteArray(1_000) { 'a'.code.toByte() }
        assertEquals(1_000, UpdateChecker.readCapped(ByteArrayInputStream(small), limit = 1_000)?.length)
        assertNull(UpdateChecker.readCapped(ByteArrayInputStream(ByteArray(1_001)), limit = 1_000))
        assertEquals("ü", UpdateChecker.readCapped(ByteArrayInputStream("ü".toByteArray()), limit = 10))
    }
}
