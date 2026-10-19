package com.cyberghost.core

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class RingBuffer<T>(private val capacity: Int) {
    private val backing = ArrayDeque<T>(capacity)
    private val _snapshot: SnapshotStateList<T> = mutableStateListOf()
    val items: List<T> get() = _snapshot
    fun add(item: T) { if (backing.size >= capacity) backing.removeFirst(); backing.addLast(item); if (backing.size % 50 == 0 || backing.size < 50) syncSnapshot() }
    fun clear() { backing.clear(); _snapshot.clear() }
    fun syncSnapshot() { _snapshot.clear(); _snapshot.addAll(backing) }
}
