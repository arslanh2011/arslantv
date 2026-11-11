package com.arslan.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.arslan.tv.R
import com.arslan.tv.util.LogoCache

/** Channel logo fetched from the network, with a neutral placeholder until it lands. */
@Composable
fun RemoteLogo(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /**
     * [ContentScale.Crop] for artwork meant to fill its tile - a YouTube still
     * is 4:3 with the 16:9 frame letterboxed inside it, and cropping is what
     * trims those black bars off.
     */
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val logo by produceState<ImageBitmap?>(initialValue = null, url) {
        value = url?.let { LogoCache.load(context, it)?.asImageBitmap() }
    }

    // The modifier belongs on the Crossfade, not on the children. Left on the
    // children, the Crossfade wraps its content, so a fillMaxSize inside it
    // resolves against unbounded constraints and the image falls back to the
    // bitmap's intrinsic size - which is why logos drew small on a phone.
    Crossfade(
        targetState = logo,
        animationSpec = tween(220),
        label = "logo",
        modifier = modifier,
    ) { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.logo_placeholder),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
