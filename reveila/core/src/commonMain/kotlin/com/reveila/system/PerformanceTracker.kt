package com.reveila.system

/**
 * Pure Kotlin Multiplatform performance tracker for distributed nodes.
 */
open class PerformanceTracker {

    companion object {
        const val DEFAULT_PENALTY_MS: Long = 5000L
        private val instance = PerformanceTracker()

        fun getInstance(): PerformanceTracker = instance
    }

    private var capacity: Int = 1000
    private val records: MutableList<Pair<Long, String>> = mutableListOf()

    open fun track(timeUsed: Long?, url: String?) {
        if (url == null || timeUsed == null) {
            return
        }
        records.add(timeUsed to url)
        records.sortBy { it.first }
        while (records.size > capacity) {
            records.removeAt(records.lastIndex)
        }
    }

    open fun size(): Int = records.size

    open fun getBestNodeUrl(): String? {
        return records.firstOrNull()?.second
    }

    open fun getCapacity(): Int = capacity

    open fun setCapacity(capacity: Int) {
        this.capacity = capacity
        while (records.size > this.capacity) {
            records.removeAt(records.lastIndex)
        }
    }

    open fun clear() {
        records.clear()
    }
}
