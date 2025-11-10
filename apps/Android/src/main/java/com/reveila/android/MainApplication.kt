package com.reveila.android

import android.app.Application
import android.content.Intent
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping // For React Native 0.76+
import com.facebook.soloader.SoLoader
import java.io.IOException
import java.lang.Exception
import com.reveila.android.ReveilaPackage
import com.reveila.android.ReveilaSetup
import com.reveila.android.ReveilaService
import com.reveila.android.BuildConfig



class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> =
            PackageList(this).packages.apply {
              // Packages that cannot be autolinked yet can be added manually here, for example:
              add(ReveilaPackage())
            }

        override fun getJSMainModuleName(): String = "index"

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()
    try {
            // For React Native 0.76 and above, use OpenSourceMergedSoMapping
            SoLoader.init(this, OpenSourceMergedSoMapping)
        } catch (e: Exception) {
            // Handle potential IOException during SoLoader initialization
            throw RuntimeException(e)
        }
    loadReactNative(this)
  }
}
