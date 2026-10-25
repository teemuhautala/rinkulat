package fi.dreamr.rinkulat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.gms.ads.MobileAds
import fi.dreamr.rinkulat.ui.RinkulatScreen
import fi.dreamr.rinkulat.ui.theme.RinkulatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Initializing on a background thread keeps it off the startup path.
        Thread { MobileAds.initialize(this) }.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            RinkulatTheme {
                RinkulatScreen()
            }
        }
    }
}
