package de.simiil.liftlog.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R

/**
 * Shared "PR" marker (bold 12sp `tertiary`) — used on Analytics detail history rows, Home
 * recent-workout rows, and History session cards. Tertiary is the app-wide PR accent
 * (matches the PR dot in ProgressLineChart).
 */
@Composable
fun PrBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.analytics_pr),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}
