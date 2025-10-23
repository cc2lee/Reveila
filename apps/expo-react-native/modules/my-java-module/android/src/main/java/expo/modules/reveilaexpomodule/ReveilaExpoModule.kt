package expo.modules.reveilaexpomodule

import com.reveila.android.ReveilaAdapterFacade

import com.reveila.android.ReveilaService
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL

class ReveilaExpoModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ReveilaExpoModule')` in JavaScript.
    Name("ReveilaExpoModule")

    // Defines constant property on the module.
    Constant("PI") {
      Math.PI
    }

    // Defines event names that the module can send to JavaScript.
    Events("onChange")

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("hello") {
      "Hello world! 👋"
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    AsyncFunction("invokeAsync") { payload: String ->
      if (!ReveilaService.isRunning()) {
        throw Exception("Reveila service is not running.")
      }
      val reveila = ReveilaService.getReveilaInstance()
      if (reveila == null) {
        throw Exception("Reveila instance is not available.")
      }

      // Parse the JSON payload
      val gson = com.google.gson.Gson()
      val request = gson.fromJson(payload, Map::class.java)
      val componentName = request["componentName"] as? String
      val methodName = request["methodName"] as? String
      val methodArguments = request["methodArguments"] as? Array<Any>

      if (componentName == null || methodName == null) {
        throw Exception("Invalid payload: componentName and methodName are required.")
      }

      val result = reveila.invoke(componentName, methodName, methodArguments)
      return@AsyncFunction gson.toJson(result)
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(ReveilaExpoModuleView::class) {
      // Defines a setter for the `url` prop.
      Prop("url") { view: ReveilaExpoModuleView, url: URL ->
        view.webView.loadUrl(url.toString())
      }
      // Defines an event that the view can send to JavaScript.
      Events("onLoad")
    }
  }
}
