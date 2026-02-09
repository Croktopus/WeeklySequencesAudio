package com.chris.wsa.playback

import com.chris.wsa.data.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistManager {
    private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    fun addItem(item: PlaylistItem) {
        _playlist.value = _playlist.value + item
    }

    fun removeItem(index: Int) {
        _playlist.value = _playlist.value.filterIndexed { i, _ -> i != index }
        if (_currentIndex.value >= _playlist.value.size && _playlist.value.isNotEmpty()) {
            _currentIndex.value = _playlist.value.size - 1
        }
    }

    fun moveItem(from: Int, to: Int) {
        val list = _playlist.value.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        _playlist.value = list
    }

    fun getCurrentItem(): PlaylistItem? {
        return _playlist.value.getOrNull(_currentIndex.value)
    }

    fun next() {
        if (_currentIndex.value < _playlist.value.size - 1) {
            _currentIndex.value++
        }
    }

    fun previous() {
        if (_currentIndex.value > 0) {
            _currentIndex.value--
        }
    }

    fun selectItem(index: Int) {
        if (index in _playlist.value.indices) {
            _currentIndex.value = index
        }
    }

    fun clear() {
        _playlist.value = emptyList()
        _currentIndex.value = 0
    }
}
