package com.riyadmondol2006.taskfern.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Why a backup file could not be used. The UI turns each case into a clear message. */
sealed interface BackupError {
    /** The file is empty or not JSON at all. */
    data object NotJson : BackupError

    /** Valid JSON, but not a Taskfern backup. */
    data object WrongFormat : BackupError

    /** Written by a newer Taskfern that this version cannot read safely. */
    data class NewerVersion(val version: Int) : BackupError

    /** A Taskfern backup with missing fields, bad values or broken references. */
    data class Invalid(val problems: List<String>) : BackupError

    /** The file could not be read from storage. */
    data class Unreadable(val detail: String) : BackupError
}

sealed interface BackupReadResult {
    data class Success(val file: BackupFile) : BackupReadResult
    data class Failure(val error: BackupError) : BackupReadResult
}

/** JSON encoding and safe decoding of [BackupFile]. Decoding always validates; nothing half-parsed gets through. */
object BackupCodec {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // A file from the same format version may carry extra fields from a later minor release; they are ignored.
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupReadResult {
        if (text.isBlank()) return BackupReadResult.Failure(BackupError.NotJson)
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return BackupReadResult.Failure(BackupError.NotJson)
        }
        if (root !is JsonObject) return BackupReadResult.Failure(BackupError.WrongFormat)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != BackupFile.FORMAT) return BackupReadResult.Failure(BackupError.WrongFormat)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: return BackupReadResult.Failure(BackupError.Invalid(listOf("The version number is missing.")))
        if (version > BackupFile.VERSION) return BackupReadResult.Failure(BackupError.NewerVersion(version))
        if (version < 1) return BackupReadResult.Failure(BackupError.Invalid(listOf("Unknown version $version.")))

        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: SerializationException) {
            return BackupReadResult.Failure(BackupError.Invalid(listOf(e.message?.lineSequence()?.firstOrNull() ?: "Malformed backup.")))
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.Failure(BackupError.Invalid(listOf(e.message ?: "Malformed backup.")))
        }
        val problems = BackupValidator.validate(file)
        return if (problems.isEmpty()) BackupReadResult.Success(file)
        else BackupReadResult.Failure(BackupError.Invalid(problems))
    }
}
