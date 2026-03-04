package expo.modules.reveila

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL

import com.reveila.android.ReveilaAdapterFacade
import android.content.Intent
import com.reveila.android.ReveilaService

class ReveilaModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("Reveila")

    AsyncFunction("startService") { systemHome: String? ->
        val context = appContext.reactContext ?: return@AsyncFunction null
        val intent = Intent(context, ReveilaService::class.java)
        if (systemHome != null) {
            intent.putExtra("systemHome", systemHome)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        null
    }

    AsyncFunction("invoke") { payload: String ->
        try {
            ReveilaAdapterFacade.invoke(payload)
        } catch (e: Exception) {
            throw Exception("Reveila Invocation Failed: ${e.message}")
        }
    }

    Function("isRunning") {
        ReveilaService.isRunning()
    }

    View(ReveilaView::class) {
      Prop("url") { view: ReveilaView, url: URL ->
        view.webView.loadUrl(url.toString())
      }
      Events("onLoad")
    }
  }
}
