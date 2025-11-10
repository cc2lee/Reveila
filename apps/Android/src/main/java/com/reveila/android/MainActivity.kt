package com.reveila.android

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    // Start the Reveila service when the main activity is created.
    // This ensures the app is in the foreground, satisfying modern Android restrictions
    // on starting foreground services.
    try {
      startService(Intent(this, ReveilaService::class.java))
    } catch (e: Exception) {
      e.printStackTrace()
    }

    // It's crucial to call super.onCreate last. This allows the ReveilaService to be
    // started before the React Native UI begins its asynchronous initialization,
    // preventing race conditions where the UI tries to access a service that isn't ready.
    super.onCreate(savedInstanceState)
  }

  override fun getMainComponentName(): String = "Reveila"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}