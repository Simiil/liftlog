package de.sleisering.liftlog.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.sleisering.liftlog.R
import de.sleisering.liftlog.ui.components.PlaceholderScreen

@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_analytics), modifier = modifier)
}
