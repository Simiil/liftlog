package de.simiil.liftlog.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.components.PlaceholderScreen

@Composable
fun PlansScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_plans), modifier = modifier)
}
