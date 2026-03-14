package so.lai.recalo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProteinColor = Color(0xFFF09133)
private val FatColor = Color(0xFFF4BF21)
private val CarbsColor = Color(0xFF8CC63F)

@Composable
fun NutrientBadgeIcon(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = (size.value * 0.55).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (size.value * 0.6).sp
        )
    }
}

@Composable
fun NutrientBadge(
    color: Color,
    label: String,
    value: String,
    size: Dp = 20.dp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NutrientBadgeIcon(color = color, label = label, size = size)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun PFCBadges(
    protein: Int,
    fat: Int,
    carbs: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NutrientBadge(color = ProteinColor, label = "P", value = "${protein}g")
        NutrientBadge(color = FatColor, label = "F", value = "${fat}g")
        NutrientBadge(color = CarbsColor, label = "C", value = "${carbs}g")
    }
}
