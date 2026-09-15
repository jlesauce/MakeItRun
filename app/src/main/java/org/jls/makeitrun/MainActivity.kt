package org.jls.makeitrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.jls.makeitrun.treadmill.TreadmillScreen
import org.jls.makeitrun.ui.theme.MakeItRunTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MakeItRunTheme {
                TreadmillScreen()
            }
        }
    }
}
