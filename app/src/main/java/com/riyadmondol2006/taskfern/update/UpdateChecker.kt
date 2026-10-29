package com.riyadmondol2006.taskfern.update

import com.riyadmondol2006.taskfern.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the latest release of [repo]. This is the only network request Taskfern makes: a single HTTPS GET
 * with no cookies, no identifiers and nothing about the user's data. Every failure becomes [UpdateResult.Failed].
 */
class UpdateChecker(
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val installedCode: Int = BuildConfig.VERSION_CODE,
    private val userAgent: String = "Taskfern/${BuildConfig.VERSION_NAME}",
) {
    val endpoint: String get() = "https://api.github.com/repos/$repo/releases/latest"

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", userAgent)
            }
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateResult.NoReleaseYet
                in 200..299 -> {
                    val body = connection.inputStream.use { readCapped(it, MAX_RESPONSE_BYTES) }
                    if (body == null) {
                        UpdateResult.Failed("response larger than $MAX_RESPONSE_BYTES bytes", reachedServer = true)
                    } else {
                        UpdatePolicy.evaluate(ReleaseParser.parse(body), installedCode)
                    }
                }
                else -> UpdateResult.Failed("HTTP $status", reachedServer = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            UpdateResult.Failed(e.javaClass.simpleName, reachedServer = false)
        } catch (e: Exception) {
            // SecurityException, malformed URLs from a bad fork setting, anything else: stay quiet.
            UpdateResult.Failed(e.javaClass.simpleName, reachedServer = false)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val TIMEOUT_MILLIS = 10_000

        /** A release response is a few kilobytes; anything far larger is not what we asked for. */
        const val MAX_RESPONSE_BYTES = 256 * 1024

        /** Reads [input] as UTF-8, or returns null when it is longer than [limit] bytes. */
        fun readCapped(input: InputStream, limit: Int): String? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > limit) return null
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
