package com.dcp.punch.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.dcp.punch.BuildConfig;
import com.dcp.punch.overlay.IslandService;

/**
 * The live demonstration: a WebView sandbox over the bundled web/ folder.
 *
 * Runs in its own :demo process, and shuts itself down the instant the theme
 * takes over. A WebView costs roughly 80 MB; there is no reason to hold that
 * while the real overlay is the thing on screen, and a separate process means
 * closing it returns every byte to the system rather than leaving a fattened
 * heap behind in the process that hosts the island.
 *
 * The whole product is the web app in assets/web — this class only has to do
 * three things well:
 *
 *   1. Draw edge to edge, including *into* the display cutout, so the demo's
 *      own island sits in the same part of the screen as the real punch-hole.
 *   2. Configure the WebView for a pixel-tuned, offline, non-zooming UI.
 *   3. Keep navigation inside the bundled assets and hand anything else to the
 *      browser.
 */
public class DemoActivity extends Activity {

    private static final String START_URL = "file:///android_asset/web/dynamic-camera-punch-v2.5.0.html";
    private static final String ASSET_PREFIX = "file:///android_asset/";

    private WebView web;

    /** The service broadcasts when the theme takes over; free the WebView. */
    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    // UnspecifiedRegisterReceiverFlag: the detector wants a literal flag argument
    // and cannot see through the SDK_INT branch below. The receiver is guarded by
    // a signature-level permission on every API level *and* by
    // RECEIVER_NOT_EXPORTED from 33 up, which is strictly stronger than what the
    // check asks for.
    @SuppressLint({"SetJavaScriptEnabled", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Belt and braces: the launcher disables the button, but an intent can
        // arrive from anywhere, and two engines running at once is the exact
        // waste this separation exists to avoid.
        if (IslandService.isRunning(this)) {
            finish();
            return;
        }
        // Two guards, because they cover different things. The signature-level
        // permission works on every API level and means only a build signed with
        // our key can send this. RECEIVER_NOT_EXPORTED is the API 33+ way of
        // saying the same thing to the platform, so pass it where it exists.
        IntentFilter closeFilter = new IntentFilter(IslandService.ACTION_CLOSE_DEMO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, closeFilter,
                    IslandService.PERMISSION_INTERNAL, null, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(closeReceiver, closeFilter,
                    IslandService.PERMISSION_INTERNAL, null);
        }

        drawEdgeToEdge();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#FF08080A"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new AssetOnlyClient());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);          // the island is the app
        s.setDomStorageEnabled(true);          // theme preference persistence
        s.setSupportZoom(false);               // fixed-pixel layout
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);                    // ignore system font scaling
        s.setMediaPlaybackRequiresUserGesture(false);
        // Assets under file:///android_asset stay reachable with these off,
        // while the rest of the filesystem does not.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);   // nothing here leaves the device
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        setContentView(web);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    /**
     * Full-bleed layout. `shortEdges` is the important one: without it Android
     * letterboxes the window below the camera cutout, which would leave the
     * demo's island floating in a black bar instead of straddling the punch-hole.
     */
    private void drawEdgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** Keeps in-app navigation on the bundled assets; everything else leaves. */
    private final class AssetOnlyClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri.toString().startsWith(ASSET_PREFIX)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
                // No browser installed — swallow rather than crash the demo.
            }
            return true;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        web.pauseTimers();     // stop the island's 200 ms heartbeat in the background
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.resumeTimers();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(closeReceiver); } catch (Exception ignored) { }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
