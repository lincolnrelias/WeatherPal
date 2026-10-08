package com.example.weatherpal.ui.forecast

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.components.*
import com.example.weatherpal.ui.presentation.*

@Composable
internal fun ActivityCard(recommendation: Recommendation, rank: Int) {
    // The enclosing page's saveable state is keyed by city and date. A new card starts closed;
    // expansion survives ranking changes, refreshes, swiping away, and recreation.
    var expanded by rememberSaveable(recommendation.activity.name) { mutableStateOf(false) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "Activity details arrow")
    val action = if (expanded) "Hide weather details" else "Show weather details"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = action) { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
            ) {
                ActivityPhoto(recommendation.activity, Modifier.matchParentSize())
                Box(
                    Modifier.matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0B211B).copy(alpha = 0.68f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier.matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color(0xFF0B211B).copy(alpha = 0.86f),
                                )
                            )
                        )
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 166.dp).padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(color = Color.White.copy(alpha = 0.17f), shape = CircleShape) {
                            Text(
                                rank.toString().padStart(2, '0'),
                                Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        SuitabilityBadge(recommendation.label)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                recommendation.activity.title(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppIcon(
                                    recommendation.activity.symbol(),
                                    Modifier.size(14.dp),
                                    Color(0xFFD7E8D9),
                                )
                                Text(
                                    if (expanded) "Hide details" else "Weather details",
                                    color = Color(0xFFE3EDE3),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Box(
                            Modifier.size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(Symbol.CHEVRON, Modifier.size(19.dp).rotate(angle), Color.White)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Eyebrow("BEHIND THE RECOMMENDATION")
                    recommendation.reasons.forEach { reason ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val symbol =
                                when (reason.code) {
                                    ReasonCode.TEMPERATURE -> Symbol.SUN
                                    ReasonCode.PRECIPITATION -> Symbol.DROP
                                    ReasonCode.WIND -> Symbol.WIND
                                    ReasonCode.SNOW_DEPTH,
                                    ReasonCode.SNOWFALL,
                                    ReasonCode.ZERO_SNOW_CAP -> Symbol.SNOW
                                    ReasonCode.INDOOR_TRADEOFF -> Symbol.BUILDING
                                    ReasonCode.MISSING_INPUTS -> Symbol.INFO
                                }
                            AppIcon(
                                symbol,
                                Modifier.padding(top = 2.dp).size(18.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                reasonText(reason, recommendation.activity),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SmallNote(
                        when (recommendation.activity) {
                            Activity.SURFING ->
                                "Land-weather comfort only. Check waves, tides, water temperature, shore wind and local access before surfing."
                            Activity.SKIING ->
                                "Modeled snow does not verify slopes, resorts or actual snowpack. Check local conditions before skiing."
                            Activity.OUTDOOR_SIGHTSEEING ->
                                "Weather fit does not confirm attraction access, opening hours or local safety conditions."
                            Activity.INDOOR_SIGHTSEEING ->
                                "Relative to the outdoor weather. Check opening hours and availability with the attraction."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuitabilityBadge(label: Suitability) {
    val (background, foreground) =
        when (label) {
            Suitability.VERY_FAVORABLE,
            Suitability.FAVORABLE -> Color(0xFFE0F0DA) to Color(0xFF25492F)
            Suitability.MIXED -> Color(0xFFFFEEC9) to Color(0xFF624918)
            Suitability.UNFAVORABLE -> Color(0xFFF0DFD8) to Color(0xFF663D30)
            Suitability.INSUFFICIENT_DATA -> Color(0xFFE3E7E4) to Color(0xFF3D4E44)
        }
    Surface(color = background, contentColor = foreground, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (label == Suitability.VERY_FAVORABLE || label == Suitability.FAVORABLE)
                AppIcon(Symbol.CHECK, Modifier.size(13.dp))
            Text(label.title(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
