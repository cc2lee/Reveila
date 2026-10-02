package com.reveila.system

import java.net.URL
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap

open class PerformanceTracker {

    companion object {
        const val DEFAULT_PENALTY_MS: Long = 5000L

        @Volatile
        private var sharedInstance: PerformanceTracker? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): PerformanceTracker {
            if (sharedInstance == null) {
                sharedInstance = PerformanceTracker()
            }
            return sharedInstance!!
        }
    }

    private var capacity: Int = 1000
    private val tracker: NavigableMap<Number, URL> = Collections.synchronizedNavigableMap(TreeMap<Number, URL>())

    @Synchronized
    open fun track(timeUsed: Number?, url: URL?) {
        if (url == null || timeUsed == null) {
            return
        }

        tracker[timeUsed] = url
        if (tracker.size > capacity) {
            tracker.pollLastEntry()
        }
    }

    @Synchronized
    open fun size(): Int {
        return tracker.size
    }

    @Synchronized
    open fun getBestNodeUrl(): URL? {
        val entry = tracker.firstEntry()
        return entry?.value
    }

    @Synchronized
    open fun getCapacity(): Int {
        return capacity
    }

    @Synchronized
    open fun setCapacity(capacity: Int) {
        this.capacity = capacity
        while (tracker.size > this.capacity) {
            tracker.pollLastEntry()
        }
    }

    @Synchronized
    open fun clear() {
        tracker.clear()
    }
}
