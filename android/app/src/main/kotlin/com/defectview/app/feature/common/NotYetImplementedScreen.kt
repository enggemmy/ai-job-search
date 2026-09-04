package com.defectview.app.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Honest placeholder for a nav destination whose feature is scheduled for a later
 * implementation phase (see the phase plan in the product spec). This is intentionally NOT a
 * dead button - it is a real screen the app navigates to, and it says plainly what's missing
 * instead of presenting fake functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotYetImplementedScreen(title: String, phaseNote: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(phaseNote)
        }
    }
}
