package de.simiil.liftlog.domain.repository

import kotlin.time.Instant

/** Opaque, validated backup produced by [BackupRepository.parseImport] and consumed by
 *  [BackupRepository.applyImport]. The data layer's BackupSnapshot is the only implementor. */
interface ParsedBackup

/** Counts shown in the import confirmation (live rows only — what the user recognizes). */
data class ImportSummary(
    val exportedAt: Instant,
    val sessions: Int,
    val exercises: Int,
    val sets: Int,
)

/** Why a backup file was rejected. Each maps to a user-facing string at the UI. */
enum class InvalidReason { MALFORMED, MISSING_FIELDS, BAD_TIMESTAMP, FK_ORPHAN, UNKNOWN_ENUM }

/** Outcome of parsing+validating a candidate file. No writes have happened. */
sealed interface ParseResult {
    data class Ready(
        val parsed: ParsedBackup,
        val summary: ImportSummary,
    ) : ParseResult

    data object BlockedByLiveSession : ParseResult

    data class Newer(
        val fileVersion: Int,
    ) : ParseResult

    data class Invalid(
        val reason: InvalidReason,
    ) : ParseResult
}

/** Versioned JSON backup (02-data-spec §6). */
interface BackupRepository {
    /** Whole DB (incl. tombstones) + settings → JSON text. */
    suspend fun exportToJson(): String

    /** Parse + validate + live-session check. Never writes. */
    suspend fun parseImport(json: String): ParseResult

    /** Full-replace: wipe + insert everything in [parsed], then restore settings. */
    suspend fun applyImport(parsed: ParsedBackup): ImportSummary
}
