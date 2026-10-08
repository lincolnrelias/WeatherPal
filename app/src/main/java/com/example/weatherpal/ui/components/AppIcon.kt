package com.example.weatherpal.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.Activity

/** A small, consistent line icon set, bundled without an extra runtime dependency. */
enum class Symbol(private val path: String) {
    SUN(
        "M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M5 19 L6.5 17.5 M17.5 6.5 L19 5"
    ),
    CLOUD("M6 18 A4 4 0 0 1 5.8 10 A6 6 0 0 1 17.4 8.5 A4.8 4.8 0 0 1 18 18 Z"),
    RAIN(
        "M5 15 A3.5 3.5 0 0 1 5.8 8 A5.5 5.5 0 0 1 16.3 6.5 A4.5 4.5 0 0 1 18 15 M8 18 L7 21 M13 18 L12 21 M18 18 L17 21"
    ),
    DROP("M12 3 C10 6 5 11 5 15 A7 7 0 0 0 19 15 C19 11 14 6 12 3 Z"),
    WIND("M3 8 L15 8 A3 3 0 1 0 12 5 M3 12 L19 12 A3 3 0 1 1 16 15 M3 16 L10 16 A3 3 0 1 1 7 19"),
    SNOW(
        "M12 2 L12 22 M3.3 7 L20.7 17 M3.3 17 L20.7 7 M9 4 L12 7 L15 4 M9 20 L12 17 L15 20 M4 11 L8 10 L7 6 M17 18 L16 14 L20 13 M4 13 L8 14 L7 18 M17 6 L16 10 L20 11"
    ),
    SEARCH("M10.5 3 A7.5 7.5 0 1 0 10.5 18 A7.5 7.5 0 1 0 10.5 3 M16 16 L21 21"),
    LOCATION(
        "M12 22 C9 18 4 13 4 9 A8 8 0 0 1 20 9 C20 13 15 18 12 22 Z M12 6 A3 3 0 1 0 12 12 A3 3 0 1 0 12 6"
    ),
    BACK("M14 5 L7 12 L14 19 M7 12 L21 12"),
    ARROW("M4 12 L20 12 M13 5 L20 12 L13 19"),
    CHEVRON("M6 9 L12 15 L18 9"),
    REFRESH("M20 7 A9 9 0 1 0 21 15 M20 2 L20 8 L14 8"),
    INFO("M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M12 11 L12 17 M12 7 L12 7.1"),
    CHECK("M5 12 L10 17 L20 6"),
    MOUNTAIN("M2 21 L9 5 L14 14 L17 9 L23 21 Z M6 12 L9 14 L11 10"),
    WAVES(
        "M2 7 Q5 3 8 7 Q11 11 14 7 Q17 3 22 7 M2 13 Q5 9 8 13 Q11 17 14 13 Q17 9 22 13 M2 19 Q5 15 8 19 Q11 23 14 19 Q17 15 22 19"
    ),
    COMPASS("M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M16 8 L14 14 L8 16 L10 10 Z"),
    BUILDING(
        "M3 9 L12 3 L21 9 Z M3 21 L21 21 M5 12 L5 18 M10 12 L10 18 M15 12 L15 18 M20 12 L20 18"
    ),
    CLOCK("M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M12 6 L12 12 L16 14"),
    CLOSE("M6 6 L18 18 M6 18 L18 6"),
    CALENDAR(
        "M5 5 L19 5 Q21 5 21 7 L21 20 Q21 22 19 22 L5 22 Q3 22 3 20 L3 7 Q3 5 5 5 Z M3 10 L21 10 M8 2 L8 7 M16 2 L16 7"
    );

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
}

@Composable
fun AppIcon(
    symbol: Symbol,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(symbol.vector, contentDescription, modifier.size(22.dp), tint)
}

fun Activity.symbol() =
    when (this) {
        Activity.SKIING -> Symbol.MOUNTAIN
        Activity.SURFING -> Symbol.WAVES
        Activity.OUTDOOR_SIGHTSEEING -> Symbol.COMPASS
        Activity.INDOOR_SIGHTSEEING -> Symbol.BUILDING
    }
