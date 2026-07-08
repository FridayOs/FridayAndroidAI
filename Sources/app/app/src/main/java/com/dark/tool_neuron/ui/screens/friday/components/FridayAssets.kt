package com.dark.tool_neuron.ui.screens.friday.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

const val FRIDAY_LOGO_ASSET = "friday_logo.png"
const val FRIDAY_AVATAR_ASSET = "friday_avatar.jpg"

@Composable
fun rememberAssetBitmap(assetName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(assetName) {
        runCatching {
            context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }.getOrNull()
    }
}
