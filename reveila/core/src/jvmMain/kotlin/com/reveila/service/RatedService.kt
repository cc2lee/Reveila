package com.reveila.service

import com.reveila.error.ConfigurationException
import com.reveila.system.Proxy
import com.reveila.system.SystemComponent
import com.reveila.util.ScoreTracker

open class RatedService(name: String) : SystemComponent() {

    private val pointsTracker: ScoreTracker

    init {
        require(name.isNotBlank()) { "Argument 'name' must not be null or empty" }
        this.pointsTracker = ScoreTracker(name)
    }

    @Throws(ConfigurationException::class)
    open fun setInitialPoints(providerNameAndPoints: List<String>?) {
        if (providerNameAndPoints.isNullOrEmpty()) {
            return
        }
        try {
            for (string in providerNameAndPoints) {
                val array = string.split(",")
                val providerName = array[0].trim()
                val points = array[1].trim()
                pointsTracker.applyPoints(points.toLong(), providerName)
            }
        } catch (e: Exception) {
            throw ConfigurationException(
                "Invalid points initialization format. Expected format: [<provider-name>, <initial-points>]\n" +
                "Error details: $e", e
            )
        }
    }

    @Synchronized
    @Throws(Exception::class)
    open fun invoke(methodName: String, args: Array<Any?>?): Any? {
        val providerName = getBestProvider() ?: throw ConfigurationException("No provider available in RatedService.")
        return try {
            val proxy: Proxy? = context?.getProxy(providerName)
            proxy?.invoke(methodName, args)
        } catch (e: IllegalArgumentException) {
            throw ConfigurationException("Component '$providerName' not found.", e)
        }
    }

    open fun rateProvider(provider: String?, points: Long?) {
        if (provider == null || points == null) return
        pointsTracker.applyPoints(points, provider)
    }

    open fun getBestProvider(): String? = pointsTracker.getBest()

    open fun getWorstProvider(): String? = pointsTracker.getWorst()

    @Throws(Exception::class)
    override fun onStop() {
    }

    @Throws(Exception::class)
    override fun onStart() {
    }
}
