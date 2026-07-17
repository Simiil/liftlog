package de.simiil.liftlog.ui.settings

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class SettingsMessage { EXPORTED, EXPORT_FAILED, IMPORTED }

sealed interface SettingsDialog {
    data object LiveSession : SettingsDialog

    data class Newer(
        val version: Int,
    ) : SettingsDialog

    data class Invalid(
        val reason: InvalidReason,
    ) : SettingsDialog
}

data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val pendingImport: ImportSummary? = null,
    val dialog: SettingsDialog? = null,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val syntheticHistorySeeder: SyntheticHistorySeeder,
    private val backupRepository: BackupRepository,
    private val documentIo: DocumentIo,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** The validated backup awaiting confirmation. Opaque (not displayed) -> kept off UiState. */
    private var pendingParsed: ParsedBackup? = null

    init {
        viewModelScope.launch {
            settingsRepository.themePreference.collect { theme ->
                _uiState.update { it.copy(theme = theme) }
            }
        }
    }

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch { settingsRepository.setThemePreference(preference) }
    }

    fun seedDemoData() {
        viewModelScope.launch { syntheticHistorySeeder.seed() }
    }

    fun defaultExportFileName(): String = "liftlog-backup-${clock.now().toLocalDateTime(TimeZone.UTC).date}.json"

    fun export(uri: Uri) {
        viewModelScope.launch {
            val message =
                try {
                    documentIo.writeText(uri, backupRepository.exportToJson())
                    SettingsMessage.EXPORTED
                } catch (e: Exception) {
                    SettingsMessage.EXPORT_FAILED
                }
            _uiState.update { it.copy(message = message) }
        }
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            val json =
                try {
                    documentIo.readText(uri)
                } catch (e: Exception) {
                    // A read failure (unreadable file / expired SAF Uri) is surfaced as a corrupt-file error.
                    handleParseResult(ParseResult.Invalid(InvalidReason.MALFORMED))
                    return@launch
                }
            val result =
                try {
                    backupRepository.parseImport(json)
                } catch (e: Exception) {
                    ParseResult.Invalid(InvalidReason.MALFORMED)
                }
            handleParseResult(result)
        }
    }

    /** Maps a parse result to dialog/confirm state. Public for unit testing (no Uri needed). */
    @VisibleForTesting
    fun handleParseResult(result: ParseResult) {
        when (result) {
            is ParseResult.Ready -> {
                pendingParsed = result.parsed
                _uiState.update { it.copy(pendingImport = result.summary, dialog = null) }
            }
            ParseResult.BlockedByLiveSession -> _uiState.update { it.copy(dialog = SettingsDialog.LiveSession) }
            is ParseResult.Newer -> _uiState.update { it.copy(dialog = SettingsDialog.Newer(result.fileVersion)) }
            is ParseResult.Invalid -> _uiState.update { it.copy(dialog = SettingsDialog.Invalid(result.reason)) }
        }
    }

    fun confirmImport() {
        val parsed = pendingParsed ?: return
        pendingParsed = null
        _uiState.update { it.copy(pendingImport = null) }
        viewModelScope.launch {
            try {
                backupRepository.applyImport(parsed)
                _uiState.update { it.copy(message = SettingsMessage.IMPORTED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(dialog = SettingsDialog.Invalid(InvalidReason.MALFORMED)) }
            }
        }
    }

    fun dismissImport() {
        pendingParsed = null
        _uiState.update { it.copy(pendingImport = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
