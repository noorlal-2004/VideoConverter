package com.example.videoconverter.converter

import com.example.videoconverter.model.ConversionStatus
import com.example.videoconverter.model.QueueItem
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide conversion queue, shared by the service and the UI. */
object QueueManager {
    const val CANCELED = "Canceled"

    private val counter = AtomicLong(0)
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    fun newId(): Long = counter.incrementAndGet()

    fun add(newItems: List<QueueItem>) {
        _items.update { it + newItems }
    }

    fun nextPending(): QueueItem? =
        _items.value.firstOrNull { it.status is ConversionStatus.Idle }

    fun pendingCount(): Int =
        _items.value.count { it.status is ConversionStatus.Idle }

    fun setStatus(id: Long, status: ConversionStatus) {
        _items.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    /** Removes an item unless it is currently running. */
    fun remove(id: Long) {
        _items.update { list ->
            list.filterNot { it.id == id && it.status !is ConversionStatus.Running }
        }
    }

    fun clearFinished() {
        _items.update { list ->
            list.filter { it.status is ConversionStatus.Idle || it.status is ConversionStatus.Running }
        }
    }

    /** Marks every waiting item as canceled. */
    fun cancelPending() {
        _items.update { list ->
            list.map {
                if (it.status is ConversionStatus.Idle) {
                    it.copy(status = ConversionStatus.Failed(CANCELED))
                } else it
            }
        }
    }
}