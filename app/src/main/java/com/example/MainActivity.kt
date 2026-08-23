package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.navigation.JarvisApp
import com.example.ui.theme.JarvisTheme
import com.example.ui.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp(viewModel = viewModel)
            }
        }
    }
}

