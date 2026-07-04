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
import androidx.activity.viewModels
import com.avow.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val triggerIntrusionState = mutableStateOf(false)
    private val intrusionReasonState = mutableStateOf<String?>(null)
    private val preload15mVowState = mutableStateOf(false)
    private val isTemporaryLockoutState = mutableStateOf(false)
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            AVowTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = mainViewModel,
                    triggerIntrusion = triggerIntrusionState.value,
                    intrusionReason = intrusionReasonState.value,
                    onIntrusionHandled = { triggerIntrusionState.value = false },
                    preload15mVow = preload15mVowState.value,
                    onPreloadHandled = { preload15mVowState.value = false },
                    isTemporaryLockout = isTemporaryLockoutState.value,
                    onTemporaryLockoutHandled = { isTemporaryLockoutState.value = false }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.saveConfigToDataStore()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null) {
            if (intent.getBooleanExtra("TRIGGER_INTRUSION", false)) {
                intrusionReasonState.value = intent.getStringExtra("BLOCK_REASON")
                triggerIntrusionState.value = true
            }
            if (intent.getBooleanExtra("PRELOAD_15M_VOW", false)) {
                preload15mVowState.value = true
            }
            if (intent.getBooleanExtra("IS_TEMPORARY_LOCKOUT", false)) {
                isTemporaryLockoutState.value = true
            }
        }
    }
}