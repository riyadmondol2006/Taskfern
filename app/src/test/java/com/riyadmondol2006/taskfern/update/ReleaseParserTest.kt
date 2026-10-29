package com.riyadmondol2006.taskfern.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private fun release(
        tag: String = "v1.0.42",
        draft: Boolean = false,
        prerelease: Boolean = false,
        body: String? = "## Changes since v1.0.41\n\n- Streaks for routines\n- **Faster** widgets",
        assets: String = """
            [
              {"name": "Taskfern-v1.0.42-release.apk.sha256", "browser_download_url": "https://github.com/o/r/releases/download/v1.0.42/Taskfern-v1.0.42-release.apk.sha256"},
              {"name": "Taskfern-v1.0.42-release.apk", "browser_download_url": "https://github.com/o/r/releases/download/v1.0.42/Taskfern-v1.0.42-release.apk"}
            ]
        """,
    ) = """
        {
          "url": "https://api.github.com/repos/o/r/releases/1",
          "html_url": "https://github.com/o/r/releases/tag/$tag",
          "tag_name": "$tag",
          "name": "Taskfern $tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "body": ${body?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"},
          "assets": $assets,
          "author": {"login": "o", "id": 1}
        }
    """.trimIndent()

    private fun stable(json: String): ReleaseInfo {
        val parsed = ReleaseParser.parse(json)
        assertTrue("expected a stable release, got $parsed", parsed is ParsedRelease.Stable)
        return (parsed as ParsedRelease.Stable).info
    }

    @Test
    fun readsVersionNotesAndPrefersTheApkAsset() {
        val info = stable(release())
        assertEquals("1.0.42", info.versionName)
        assertEquals(42, info.versionCode)
        assertEquals("https://github.com/o/r/releases/download/v1.0.42/Taskfern-v1.0.42-release.apk", info.downloadUrl)
        assertEquals("https://github.com/o/r/releases/tag/v1.0.42", info.pageUrl)
        assertEquals("Changes since v1.0.41\n\n• Streaks for routines\n• Faster widgets", info.notes)
    }

    @Test
    fun withoutAnApkAssetDownloadOpensTheReleasePage() {
        val noApk = release(assets = """[{"name": "notes.txt", "browser_download_url": "https://github.com/o/r/notes.txt"}]""")
        assertEquals("https://github.com/o/r/releases/tag/v1.0.42", stable(noApk).downloadUrl)
        assertEquals("https://github.com/o/r/releases/tag/v1.0.42", stable(release(assets = "[]")).downloadUrl)
        assertEquals("https://github.com/o/r/releases/tag/v1.0.42", stable(release(assets = "null")).downloadUrl)
    }

    @Test
    fun anApkLinkThatIsNotHttpsIsIgnored() {
        val plain = release(assets = """[{"name": "a.apk", "browser_download_url": "http://example.com/a.apk"}]""")
        assertEquals("https://github.com/o/r/releases/tag/v1.0.42", stable(plain).downloadUrl)
    }

    @Test
    fun draftsAndPrereleasesAreNeverOffered() {
        assertEquals(ParsedRelease.NotStable, ReleaseParser.parse(release(draft = true)))
        assertEquals(ParsedRelease.NotStable, ReleaseParser.parse(release(prerelease = true)))
    }

    @Test
    fun badTagsAreMalformed() {
        listOf("nightly", "v1.0", "v1.0.x", "v1.0.42-beta", "1.0.0.7", "v1.0.0", "v1.0.99999999999", "").forEach { tag ->
            val parsed = ReleaseParser.parse(release(tag = tag))
            assertTrue("tag '$tag' gave $parsed", parsed is ParsedRelease.Malformed)
        }
    }

    @Test
    fun tagsWithAndWithoutTheVPrefixAreRead() {
        assertEquals(42, ReleaseParser.versionCodeOf("v1.0.42"))
        assertEquals(7, ReleaseParser.versionCodeOf("2.3.7"))
        assertEquals(2_100_000_000, ReleaseParser.versionCodeOf("v1.0.2100000000"))
        assertNull(ReleaseParser.versionCodeOf("v1.0.2100000001"))
        assertNull(ReleaseParser.versionCodeOf("V1.0.42"))
    }

    @Test
    fun brokenResponsesAreMalformedNotCrashes() {
        listOf("", "not json", "[]", "null", "{}", """{"tag_name": 42}""", """{"tag_name": "v1.0.5"}""").forEach { body ->
            val parsed = ReleaseParser.parse(body)
            assertTrue("'$body' gave $parsed", parsed is ParsedRelease.Malformed)
        }
    }

    @Test
    fun missingBodyGivesEmptyNotes() {
        assertEquals("", stable(release(body = null)).notes)
    }

    @Test
    fun longNotesAreCutAtAWordNearTheLimit() {
        val notes = ReleaseParser.plainNotes("word ".repeat(400))
        assertTrue(notes.length <= ReleaseParser.NOTES_MAX_CHARS + 1)
        assertTrue(notes.endsWith("word…"))
    }

    @Test
    fun markdownBecomesPlainText() {
        val md = "# Title\r\n\r\n\r\n\r\n* one [link](https://x.y)\n+ `two` <b>bold</b>\n![img](https://x.y/i.png)\n__three__"
        assertEquals("Title\n\n• one link\n• two bold\nimg\nthree", ReleaseParser.plainNotes(md))
    }
}
