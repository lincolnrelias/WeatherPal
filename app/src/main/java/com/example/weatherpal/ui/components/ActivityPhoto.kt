package com.example.weatherpal.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.weatherpal.R
import com.example.weatherpal.domain.model.Activity

fun Activity.photo() =
    when (this) {
        Activity.SKIING -> R.drawable.activity_skiing
        Activity.SURFING -> R.drawable.activity_surfing
        Activity.OUTDOOR_SIGHTSEEING -> R.drawable.activity_outdoor
        Activity.INDOOR_SIGHTSEEING -> R.drawable.activity_indoor
    }

@Composable
fun ActivityPhoto(activity: Activity, modifier: Modifier = Modifier) {
    Image(painterResource(activity.photo()), null, modifier, contentScale = ContentScale.Crop)
}
