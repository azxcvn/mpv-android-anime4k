package com.fam4k007.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.ui.screens.AudioPlayerScreen
import org.koin.android.ext.android.inject

class AudioPlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AudioPlayerActivity"
    }

    internal val viewModel: PlayerViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(Modifier.fillMaxSize()) {
                AudioPlayerScreen(viewModel = viewModel, onClose = { finish() })
            }
        }
    }
}
