package com.dark.tool_neuron.ui.screens.friday.components

import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

enum class VoiceAnimation(val assetName: String) {
    ORB("orb.mp4"),
    GIF_1("voice_anim_1.gif"),
    GIF_3("voice_anim_3.gif"),
    GIF_4("voice_anim_4.gif");

    companion object {
        fun fromKey(key: String): VoiceAnimation = when (key) {
            "gif_1" -> GIF_1
            "gif_3" -> GIF_3
            "gif_4" -> GIF_4
            else -> ORB
        }
    }
}

private val VOICE_ANIM_SIZE = 176.dp
private const val VOICE_ANIM_VIDEO_SCALE = 1.32f

@Composable
fun FridayVoiceAnimation(animation: VoiceAnimation, active: Boolean, modifier: Modifier = Modifier) {
    val clipped = modifier.size(VOICE_ANIM_SIZE).clip(CircleShape)
    if (animation == VoiceAnimation.ORB) {
        FridayVoiceOrbVideo(animation.assetName, active, clipped)
    } else {
        FridayVoiceGifAnimation(animation.assetName, active, clipped)
    }
}

@Composable
private fun FridayVoiceOrbVideo(assetName: String, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    var surfaceReady by remember { mutableStateOf(false) }

    DisposableEffect(assetName) {
        onDispose { mediaPlayer.release() }
    }

    LaunchedEffect(active, surfaceReady) {
        if (!surfaceReady) return@LaunchedEffect
        runCatching { if (active) mediaPlayer.start() else mediaPlayer.pause() }
    }

    AndroidView(
        modifier = modifier.graphicsLayer(scaleX = VOICE_ANIM_VIDEO_SCALE, scaleY = VOICE_ANIM_VIDEO_SCALE),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            context.assets.openFd(assetName).use { fd ->
                                mediaPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                            }
                            mediaPlayer.setSurface(Surface(surface))
                            mediaPlayer.isLooping = true
                            mediaPlayer.setVolume(0f, 0f)
                            mediaPlayer.prepare()
                            if (active) mediaPlayer.start()
                            surfaceReady = true
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        surfaceReady = false
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        }
    )
}

@Composable
private fun FridayVoiceGifAnimation(assetName: String, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val drawable = remember(assetName) {
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, assetName))
        }.getOrNull()
    }

    LaunchedEffect(active, drawable) {
        val animated = drawable as? AnimatedImageDrawable ?: return@LaunchedEffect
        if (active) animated.start() else animated.stop()
    }
    DisposableEffect(drawable) {
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(drawable)
            }
        }
    )
}
