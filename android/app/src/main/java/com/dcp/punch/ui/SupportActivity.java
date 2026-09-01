package com.dcp.punch.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The support page, bundled as an asset and shown offline.
 *
 * Runs in the :demo process for the same reason the demonstration does — a
 * WebView is the single most expensive thing this app can create, and putting
 * it in a process that can be reaped whole is the difference between a page you
 * opened once and a permanently fatter app. The two are never open at the same
 * time, so they share the process rather than adding a third.
 *
 * Every request is pinned to file:///android_asset. The app holds no INTERNET
 * permission, so a link to the open web could not load in any case — but the
 * WebView should not try, and an unhandled navigation silently failing is worse
 * than one that never starts.
 */
public class SupportActivity extends Activity {

    private static final String START_URL = "file:///android_asset/support/index.html";
    private static final String ASSET_PREFIX = "file:///android_asset/";

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        getWindow().setStatusBarColor(Color.parseColor("#FF090D16"));
        getWindow().setNavigationBarColor(Color.parseColor("#FF090D16"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#FF090D16"));
        web.setWebViewClient(new AssetOnly());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);      // the amount picker and the accordion
        s.setDomStorageEnabled(false);     // nothing here needs to persist
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }

        setContentView(web);
        if (saved != null) web.restoreState(saved); else web.loadUrl(START_URL);
    }

    /** Anything that is not a bundled asset is refused rather than attempted. */
    private static class AssetOnly extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            String url = r.getUrl() == null ? "" : r.getUrl().toString();
            return !url.startsWith(ASSET_PREFIX);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            // Detach before destroying, or the WebView's own view tree keeps a
            // reference to this activity long enough to matter.
            setContentView(new android.view.View(this));
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
