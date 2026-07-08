package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import com.dark.tool_neuron.ui.screens.experiment.IslandPrototypeScreen
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import dagger.hilt.android.AndroidEntryPoint

// Scratchpad activity. Currently hosting the dynamic-island prototype so we
// can iterate the morph animation against a real device without dragging
// the service / overlay-permission machinery along for the ride. Swap the
// screen here when iterating on a different experiment.
@AndroidEntryPoint
class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolNeuronTheme {
                Scaffold { padding ->
                    IslandPrototypeScreen(innerPadding = padding)
                }
            }
        }
    }
}
