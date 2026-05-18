package com.puter.unofficial;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.Locale;

/**
 * The bridge class between the HTML JavaScript and Native Android code.
 */
public class WebAppInterface {

    private final Context context;
    private final WebView webView;
    private final TextToSpeech tts;
    private final SharedPreferences prefs;

    public WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.prefs = context.getSharedPreferences("PuterPrefs", Context.MODE_PRIVATE);

        // Initialize Native TTS Engine
        this.tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    // 1. Text-To-Speech: Speak and Barge-in Interruption
    @JavascriptInterface
    public void speak(String text) {
        if (tts != null) {
            // Barge-in: Stop current speech immediately
            tts.stop();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PuterTTS");
        }
    }

    // 2. Sign In: Open Browser for Auth
    @JavascriptInterface
    public void signIn() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://puter.com/login"));
        context.startActivity(intent);
    }

    // 3. Sign Out: Clear Persistence
    @JavascriptInterface
    public void signOut() {
        prefs.edit().putBoolean("is_logged_in", false).apply();
        Toast.makeText(context, "Signed out of Puter", Toast.LENGTH_SHORT).show();
    }

    // 4. Persistence Check
    @JavascriptInterface
    public boolean isLoggedIn() {
        return prefs.getBoolean("is_logged_in", false);
    }

    // 5. Update Status (Called by App when Auth callback returns)
    public void setLoggedIn(boolean status) {
        prefs.edit().putBoolean("is_logged_in", status).apply();
    }

    // 6. Voice Recognition Trigger
    @JavascriptInterface
    public void startListening() {
        // This triggers the native VoiceManager which will later call 
        // webView.evaluateJavascript("window.onSpeechResult('...')", null);
        Intent intent = new Intent(context, VoiceManager.class);
        context.startService(intent); 
    }

    // 7. Stop TTS (Force stop triggered by HTML UI if needed)
    @JavascriptInterface
    public void stopSpeaking() {
        if (tts != null) {
            tts.stop();
        }
    }

    // Cleanup when WebView is destroyed
    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}