package com.riyadmondol2006.taskfern.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** A published, stable Taskfern release as offered to the user. */
data class ReleaseInfo(
    /** "1.0.42", from the tag without its leading "v". */
    val versionName: String,
    /** The tag's last number, which is the APK's versionCode (docs/RELEASING.md). */
    val versionCode: Int,
    /** Short plain-text release notes, possibly empty. */
    val notes: String,
    /** What "Download" opens: the APK file when the release has one, otherwise the release page. */
    val downloadUrl: String,
    /** The release page on GitHub. */
    val pageUrl: String,
)

/** What the "latest release" response contains, before comparing it with the installed version. */
sealed interface ParsedRelease {
    data class Stable(val info: ReleaseInfo) : ParsedRelease

    /** A draft or pre-release: never offered. */
    data object NotStable : ParsedRelease

    /** The response or its tag is not something Taskfern understands. */
    data class Malformed(val reason: String) : ParsedRelease
}

/**
 * Reads GitHub's "get the latest release" JSON (https://docs.github.com/rest/releases/releases#get-the-latest-release).
 * Pure and lenient: unknown fields are ignored, wrong types count as missing, and nothing here throws.
 */
object ReleaseParser {
    /** Release notes are cut to about this many characters for the popup. */
    const val NOTES_MAX_CHARS = 600

    /** Android's practical versionCode ceiling, as in app/build.gradle.kts. */
    private const val MAX_VERSION_CODE = 2_100_000_000L

    /** Release tags: v + MAJOR.MINOR.versionCode, e.g. v1.0.42. */
    private val TAG = Regex("""v?(\d{1,9})\.(\d{1,9})\.(\d{1,10})""")

    private val json = Json { ignoreUnknownKeys = true }

    /** The versionCode encoded in [tag], or null when the tag does not look like v1.0.42. */
    fun versionCodeOf(tag: String): Int? {
        val match = TAG.matchEntire(tag.trim()) ?: return null
        return match.groupValues[3].toLongOrNull()?.takeIf { it in 1..MAX_VERSION_CODE }?.toInt()
    }

    fun parse(body: String): ParsedRelease {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return ParsedRelease.Malformed("response is not a JSON object")
        if (root.flag("draft") || root.flag("prerelease")) return ParsedRelease.NotStable
        val tag = root.text("tag_name")?.trim() ?: return ParsedRelease.Malformed("release has no tag")
        val code = versionCodeOf(tag) ?: return ParsedRelease.Malformed("unrecognised tag '${tag.take(40)}'")
        val page = root.text("html_url")?.takeIf(::isHttps)
        val apk = (root["assets"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { asset ->
                asset.text("name")?.endsWith(".apk", ignoreCase = true) == true &&
                    asset.text("browser_download_url")?.let(::isHttps) == true
            }
            ?.text("browser_download_url")
        val download = apk ?: page ?: return ParsedRelease.Malformed("release has no download link")
        return ParsedRelease.Stable(
            ReleaseInfo(
                versionName = tag.removePrefix("v"),
                versionCode = code,
                notes = plainNotes(root.text("body")),
                downloadUrl = download,
                pageUrl = page ?: download,
            ),
        )
    }

    /**
     * Turns Markdown release notes into short plain text: headings, emphasis, code marks, links and HTML tags are
     * reduced to their text, list items get a bullet, and the result is cut at a word boundary near [max] characters.
     */
    fun plainNotes(markdown: String?, max: Int = NOTES_MAX_CHARS): String {
        if (markdown.isNullOrBlank()) return ""
        val text = markdown.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { line ->
                line.trimEnd()
                    .replace(Regex("""^\s{0,3}#{1,6}\s*"""), "")
                    .replace(Regex("""^(\s*)[-*+]\s+"""), "$1• ")
                    .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
                    .replace(Regex("""<[^>]+>"""), "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
            }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        if (text.length <= max) return text
        val cut = text.take(max)
        val boundary = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
        return (if (boundary > max / 2) cut.take(boundary) else cut).trimEnd() + "…"
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true) && url.length > "https://".length

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
}
