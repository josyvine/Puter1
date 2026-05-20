package com.puter.unofficial;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity: The primary host for the Puter Unofficial WebView.
 * Handles permissions, hardware-level WebView configuration, 
 * and communication with the native Voice Agent Activity.
 * 
 * UPDATED: Fixed File Picker logic to ensure Bridge-initiated uploads 
 * are converted to Base64 and sent to the stagedFiles UI.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private VoiceManager voiceManager;
    private WebAppInterface webAppInterface;
    private MyWebChromeClient myWebChromeClient; // Custom client for popups/uploads

    // Receiver to catch results from the Full-Screen Voice Agent Activity
    private BroadcastReceiver voiceReceiver;

    private final static int FILE_CHOOSER_RESULT_CODE = 1;
    private final static int PERMISSION_REQUEST_CODE = 100;

    // Logic Guard to prevent the UI Blinking Loop during Auth/Reloads
    private boolean isRefreshing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // REQUIREMENT: Force White/Light Theme at the Activity Level
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        // DIAGNOSTICS: Enable Remote Debugging via Chrome DevTools (pc)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // --- WebView Configuration: Deep Hardware Settings ---
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        // CRITICAL FOR AUTH: Allows Puter SDK to open the Sign-In Popup Window
        webSettings.setSupportMultipleWindows(true); 

        // DIAGNOSTICS: Allow scripts to access local content for debug console
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        // FIX: Remove the WebView identifier ("; wv") from the User Agent.
        // This tricks the Puter.js SDK into initializing correctly in an app context.
        String userAgent = webSettings.getUserAgentString();
        userAgent = userAgent.replace("; wv", "");
        webSettings.setUserAgentString(userAgent);

        // FIX FOR PUTER.JS AUTH: Enable and force Third-Party Cookie acceptance.
        // This is necessary to bridge the session between the popup and main window.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // Standard Mobile Viewport scaling
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // --- Client Assignments ---
        // PuterWebViewClient handles virtual HTTPS routing for session persistence
        webView.setWebViewClient(new PuterWebViewClient(this));

        // MyWebChromeClient handles native file pickers and the Auth Popup Dialog
        myWebChromeClient = new MyWebChromeClient(this);
        webView.setWebChromeClient(myWebChromeClient);

        // --- Native Manager Initialization ---
        voiceManager = new VoiceManager(this, webView);
        webAppInterface = new WebAppInterface(this, webView);

        // Linking Voice and Bridge for Barge-in (Interruption) support
        webAppInterface.setVoiceManager(voiceManager);
        voiceManager.setBridge(webAppInterface);

        // --- JavaScript Bridge Registration ---
        // Exposes 'window.AndroidInterface' to the HTML/JS logic
        webView.addJavascriptInterface(webAppInterface, AppConstants.JS_BRIDGE_NAME);

        // Load the frontend via the Secure Origin Asset Loader
        webView.loadUrl(AppConstants.LOCAL_INDEX_URL);

        // --- Native Voice Loop Setup ---
        setupVoiceReceiver();

        // Check and Request System Permissions
        checkAndRequestPermissions();
    }

    /**
     * Requirement #3: Voice Agent Communication.
     * Listens for the "PUTER_VOICE_INPUT" broadcast from VoiceAgentActivity.
     * When the full-screen mode finishes a turn, it injects the text here.
     */
    private void setupVoiceReceiver() {
        voiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("PUTER_VOICE_INPUT".equals(intent.getAction())) {
                    String query = intent.getStringExtra("QUERY");
                    if (query != null) {
                        Log.d("MainActivity", "Voice Input Received: " + query);
                        injectSpeechToWebView(query);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("PUTER_VOICE_INPUT");

        // Android 13+ compatibility for exported receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(voiceReceiver, filter);
        }
    }

    /**
     * Requirement #1: Broadcast AI response text back to VoiceAgentActivity.
     */
    public void broadcastAiResponse(String text) {
        Intent intent = new Intent("PUTER_AI_RESPONSE");
        intent.putExtra("RESPONSE_TEXT", text);
        sendBroadcast(intent);
        Log.d("MainActivity", "AI Response Broadcasted to Native Agent");
    }

    /**
     * Requirement #3: Injects spoken text into the index.html logic.
     */
    private void injectSpeechToWebView(String text) {
        String safeText = text.replace("'", "\\'");
        webView.post(() -> webView.evaluateJavascript(
                "if(window.onSpeechResult) { window.onSpeechResult('" + safeText + "'); }",
                null)
        );
    }

    /**
     * Validates and requests all necessary hardware permissions.
     */
    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        listPermissionsNeeded.add(Manifest.permission.INTERNET);
        listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        listPermissionsNeeded.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listPermissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        List<String> remainingPermissions = new ArrayList<>();
        for (String permission : listPermissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(permission);
            }
        }

        if (!remainingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, remainingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Voice and Image features require permissions.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * UPDATED: Handles both standard WebChromeClient uploads and Bridge-initiated uploads.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 1. Logic for Bridge-initiated File Upload (Feature Enhancement #1)
        if (requestCode == FILE_CHOOSER_RESULT_CODE && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                Log.d("MainActivity", "File selected via Bridge: " + fileUri.toString());
                
                // Convert to Base64 Data URI
                String base64Data = FileUtils.fileToDataUri(this, fileUri);
                
                if (base64Data != null) {
                    // Inject the Base64 string directly into the JS stagedFiles array
                    webView.post(() -> {
                        webView.evaluateJavascript(
                            "if(window.onImageResult){ window.onImageResult('" + base64Data + "'); }", 
                            null
                        );
                    });
                }
            }
        }

        // 2. Original Requirement #3: Delegate standard file picker result back to ChromeClient
        if (myWebChromeClient != null) {
            myWebChromeClient.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Requirement #5: Force-reloads the WebView UI.
     * Used after sign-out to clear session state.
     */
    public void reloadWebView() {
        runOnUiThread(() -> {
            if (webView != null && !isRefreshing) {
                isRefreshing = true;

                // Requirement #4: Persistence Fix. Ensure cookies are saved before reload.
                CookieManager.getInstance().flush();

                webView.reload();

                // Release reload guard after 3 seconds to prevent UI flicker
                webView.postDelayed(() -> isRefreshing = false, 3000);
            }
        });
    }

    /**
     * Overridden to intercept back button navigation dynamically.
     * Prevents the app from closing when browsing an external page or when
     * inside the sub-panels (browser.html, scraper.html).
     */
    @Override
    public void onBackPressed() {
        if (webView != null) {
            String currentUrl = webView.getUrl();
            if (currentUrl != null) {
                // 1. If viewing an external webpage inside the viewport, go back in browser history
                if (!currentUrl.startsWith("https://appassets.androidplatform.net/")) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                        return;
                    }
                } 
                // 2. If inside local sub-panels, smoothly route back to the central Puter Chat index.html
                else if (currentUrl.contains("browser.html") || currentUrl.contains("scraper.html")) {
                    webView.loadUrl(AppConstants.LOCAL_INDEX_URL);
                    return;
                }
            }
        }
        // 3. Default fallback if we are on index.html
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        // Cleanup Native Resources
        if (voiceReceiver != null) {
            unregisterReceiver(voiceReceiver);
        }
        if (webView != null) {
            webView.destroy();
        }
        if (voiceManager != null) {
            voiceManager.destroy();
        }
        if (webAppInterface != null) {
            webAppInterface.destroy();
        }
        super.onDestroy();
    }
}