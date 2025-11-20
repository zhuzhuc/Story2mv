package com.nm.story2mv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nm.story2mv.ui.theme.Story2mvTheme
import com.nm.story2mv.ui.navigation.StoryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Story2mvTheme {
                val container = (application as Story2mvApp).container
                StoryApp(container = container)
            }
        }
    }
}