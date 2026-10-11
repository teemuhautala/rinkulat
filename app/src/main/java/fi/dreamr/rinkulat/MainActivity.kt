package fi.dreamr.rinkulat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fi.dreamr.rinkulat.ui.RinkulatScreen
import fi.dreamr.rinkulat.ui.theme.RinkulatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            RinkulatTheme {
                RinkulatScreen()
            }
        }
    }
}
