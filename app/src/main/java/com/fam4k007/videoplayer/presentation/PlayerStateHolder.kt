package com.fam4k007.videoplayer.presentation

import com.fam4k007.videoplayer.VideoFileParcelable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerStateHolder {
    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _videoList = MutableStateFlow<List<VideoFileParcelable>>(emptyList())
    val videoList: StateFlow<List<VideoFileParcelable>> = _videoList.asStateFlow()

    private val _commands = MutableSharedFlow<AudioCommand>(extraBufferCapacity = 10)
    val commands: SharedFlow<AudioCommand> = _commands.asSharedFlow()

    fun syncFromViewModel(vm: PlayerViewModel) {
        _videoTitle.value = vm.videoTitle.value
        _currentIndex.value = vm.currentIndex.value
        _videoList.value = vm.videoList.value
    }

    fun sendCommand(cmd: AudioCommand) { _commands.tryEmit(cmd) }

    sealed interface AudioCommand {
        data object TogglePlayPause : AudioCommand
        data object NextVideo : AudioCommand
        data object PreviousVideo : AudioCommand
        data class SeekTo(val seconds: Int) : AudioCommand
        data class SetSpeed(val speed: Double) : AudioCommand
        data class PlayAtIndex(val index: Int) : AudioCommand
    }
}
