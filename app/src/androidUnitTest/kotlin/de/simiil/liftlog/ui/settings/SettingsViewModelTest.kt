package de.simiil.liftlog.ui.settings

import app.cash.turbine.test
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import de.simiil.liftlog.testing.FakeBackupRepository
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.MainDispatcherRule
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = FixedClock(Instant.parse("2026-06-09T12:00:00Z"))

    private fun noOpSeeder() = SyntheticHistorySeeder(FakeSessionDao(), Clock.System)

    private fun vm(backup: FakeBackupRepository = FakeBackupRepository()) =
        SettingsViewModel(
            settingsRepository = FakeSettingsRepository(),
            syntheticHistorySeeder = noOpSeeder(),
            backupRepository = backup,
            documentIo =
                object : DocumentIo {
                    override suspend fun readText(handle: DocumentHandle) = error("unused")

                    override suspend fun writeText(
                        handle: DocumentHandle,
                        text: String,
                    ) = error("unused")
                },
            clock = clock,
        )

    @Test
    fun `selecting a theme persists it and updates ui state`() =
        runTest {
            val viewModel = vm()
            viewModel.uiState.test {
                assertEquals(ThemePreference.SYSTEM, awaitItem().theme)
                viewModel.onThemeSelected(ThemePreference.DARK)
                assertEquals(ThemePreference.DARK, awaitItem().theme)
            }
        }

    @Test
    fun `default export file name uses the clock date`() {
        assertEquals("liftlog-backup-2026-06-09.json", vm().defaultExportFileName())
    }

    @Test
    fun `Ready parse result shows the confirm summary`() {
        val viewModel = vm()
        val summary = ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 12, 5, 40)
        viewModel.handleParseResult(ParseResult.Ready(object : ParsedBackup {}, summary))
        assertEquals(summary, viewModel.uiState.value.pendingImport)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `blocked parse result shows the live-session dialog`() {
        val viewModel = vm()
        viewModel.handleParseResult(ParseResult.BlockedByLiveSession)
        assertEquals(SettingsDialog.LiveSession, viewModel.uiState.value.dialog)
    }

    @Test
    fun `newer and invalid parse results show their dialogs`() {
        val viewModel = vm()
        viewModel.handleParseResult(ParseResult.Newer(2))
        assertEquals(SettingsDialog.Newer(2), viewModel.uiState.value.dialog)
        viewModel.dismissDialog()
        viewModel.handleParseResult(ParseResult.Invalid(InvalidReason.FK_ORPHAN))
        assertEquals(SettingsDialog.Invalid(InvalidReason.FK_ORPHAN), viewModel.uiState.value.dialog)
    }

    @Test
    fun `confirming an import applies it and emits the imported message`() =
        runTest {
            val backup = FakeBackupRepository()
            val viewModel = vm(backup)
            val summary = ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 1, 1, 1)
            viewModel.handleParseResult(ParseResult.Ready(backup.dummyParsed, summary))
            viewModel.confirmImport()
            assertEquals(backup.dummyParsed, backup.appliedWith)
            assertEquals(SettingsMessage.IMPORTED, viewModel.uiState.value.message)
            assertNull(viewModel.uiState.value.pendingImport)
        }

    @Test
    fun `dismissing the confirm cancels without applying`() {
        val backup = FakeBackupRepository()
        val viewModel = vm(backup)
        viewModel.handleParseResult(
            ParseResult.Ready(
                backup.dummyParsed,
                ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 1, 1, 1),
            ),
        )
        viewModel.dismissImport()
        assertNull(viewModel.uiState.value.pendingImport)
        viewModel.confirmImport()
        assertNull(backup.appliedWith) // nothing applied
    }
}
