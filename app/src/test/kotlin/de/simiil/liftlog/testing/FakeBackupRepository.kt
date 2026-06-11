package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import java.time.Instant

class FakeBackupRepository : BackupRepository {
    var exportJson: String = "{}"
    var parseResult: ParseResult = ParseResult.Invalid(InvalidReason.MALFORMED)
    var appliedWith: ParsedBackup? = null
    val dummyParsed = object : ParsedBackup {}

    override suspend fun exportToJson(): String = exportJson

    override suspend fun parseImport(json: String): ParseResult = parseResult

    override suspend fun applyImport(parsed: ParsedBackup): ImportSummary {
        appliedWith = parsed
        return ImportSummary(Instant.parse("2026-06-09T12:00:00Z"), sessions = 1, exercises = 1, sets = 1)
    }
}
