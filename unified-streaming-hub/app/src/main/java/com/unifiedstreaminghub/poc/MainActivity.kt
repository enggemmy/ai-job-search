package com.unifiedstreaminghub.poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unifiedstreaminghub.poc.ui.HubApp
import com.unifiedstreaminghub.poc.ui.theme.HubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubTheme {
                HubApp()
            }
        }
    }
}
