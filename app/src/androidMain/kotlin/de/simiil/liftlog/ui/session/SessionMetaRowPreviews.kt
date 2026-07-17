package de.simiil.liftlog.ui.session

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.ui.theme.LiftLogTheme

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "SessionMetaRow – empty collapsed", showBackground = true)
@Composable
private fun PreviewSessionMetaRowEmpty() {
    LiftLogTheme {
        SessionMetaRow(rpe = null, note = null, onRpeChange = {}, onNoteChange = {}, onNoteFlush = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "SessionMetaRow – with values collapsed", showBackground = true)
@Composable
private fun PreviewSessionMetaRowValues() {
    LiftLogTheme {
        SessionMetaRow(rpe = 8.0, note = "Felt strong today", onRpeChange = {
        }, onNoteChange = {}, onNoteFlush = {}, modifier = Modifier.padding(16.dp))
    }
}
