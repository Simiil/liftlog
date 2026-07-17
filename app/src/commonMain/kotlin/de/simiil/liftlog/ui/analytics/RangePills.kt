package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.range_1y
import liftlog.app.generated.resources.range_30d
import liftlog.app.generated.resources.range_90d
import liftlog.app.generated.resources.range_all
import org.jetbrains.compose.resources.stringResource

/** Equal-width 30d/90d/1y/all pills, shared by the exercise-detail screen and the muscle-balance card. */
@Composable
fun RangePills(
    selected: Range,
    onChange: (Range) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Range.entries.forEach { r ->
            val isSelected = r == selected
            Surface(
                onClick = { onChange(r) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        rangeLabel(r),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun rangeLabel(r: Range) =
    stringResource(
        when (r) {
            Range.D30 -> Res.string.range_30d
            Range.D90 -> Res.string.range_90d
            Range.Y1 -> Res.string.range_1y
            Range.ALL -> Res.string.range_all
        },
    )
