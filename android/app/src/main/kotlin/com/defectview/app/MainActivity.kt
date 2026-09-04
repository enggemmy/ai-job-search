package com.defectview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.defectview.app.ui.navigation.DefectViewNavHost
import com.defectview.app.ui.theme.DefectViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DefectViewApplication).container

        setContent {
            DefectViewTheme {
                DefectViewNavHost(container)
            }
        }
    }
}
