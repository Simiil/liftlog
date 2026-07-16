package de.simiil.liftlog.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.analytics_pr
import org.jetbrains.compose.resources.stringResource

/**
 * Shared "PR" marker (bold 12sp `tertiary`) — used on Analytics detail history rows, Home
 * recent-workout rows, and History session cards. Tertiary is the app-wide PR accent
 * (matches the PR dot in ProgressLineChart).
 */
@Composable
fun PrBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.analytics_pr),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}
