package com.avow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.avow.app.ui.MainScreen
import com.avow.app.ui.theme.AVowTheme

class MainActivity : ComponentActivity() {
    private val triggerIntrusionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            AVowTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    triggerIntrusion = triggerIntrusionState.value,
                    onIntrusionHandled = { triggerIntrusionState.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("TRIGGER_INTRUSION", false)) {
            triggerIntrusionState.value = true
        }
    }
}