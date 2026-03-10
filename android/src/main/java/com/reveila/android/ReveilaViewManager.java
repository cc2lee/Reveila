package com.reveila.android;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.common.MapBuilder;
import java.util.Map;
import androidx.annotation.Nullable;

public class ReveilaViewManager extends SimpleViewManager<WebView> {

    public static final String REACT_CLASS = "ReveilaView";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected WebView createViewInstance(ThemedReactContext reactContext) {
        WebView webView = new WebView(reactContext);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Emit onLoad event if needed (standard RN way)
            }
        });
        return webView;
    }

    @ReactProp(name = "url")
    public void setUrl(WebView view, @Nullable String url) {
        if (url != null) {
            view.loadUrl(url);
        }
    }

    @Override
    public @Nullable Map getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.builder()
                .put("onLoad", MapBuilder.of("registrationName", "onLoad"))
                .build();
    }
}
