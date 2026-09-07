package com.example.resouretree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.resouretree.ui.navigation.ResourceTreeApp
import com.example.resouretree.ui.theme.ResoureTreeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResoureTreeTheme {
                ResourceTreeApp(application as ResourceTreeApplication)
            }
        }
    }
}
