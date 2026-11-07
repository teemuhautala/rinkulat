package fi.dreamr.rinkulat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Google's sample banner unit; replace with the real ad unit ID before release.
private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

/** Standard 320×50 AdMob banner, loaded once when it enters the composition. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    // Reserve the height up front so a late-loading ad can't push the controls below it out from under a tap.
    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(AdSize.BANNER.height.dp),
    )
}
