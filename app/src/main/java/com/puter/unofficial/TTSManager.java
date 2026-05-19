package com.puter.unofficial;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Native Android Text-To-Speech Manager.
 * Handles the speech synthesis and barge-in (interruption) logic.
 * UPDATED: Integrated UtteranceProgressListener for continuous conversation flow.
 */
public class TTSManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "PuterTTSManager";
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public TTSManager(Context context) {
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            } else {
                isInitialized = true;
                // REQUIREMENT: Setup listener to notify when AI is done speaking
                // so the microphone can automatically re-open for a continuous loop.
                setupProgressListener();
            }
        } else {
            Log.e(TAG, "TTS Initialization failed");
        }
    }

    /**
     * Internal listener to track speech lifecycle.
     */
    private void setupProgressListener() {
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "Speech Started: " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "Speech Finished: " + utteranceId);
                    // This is where we can trigger a callback to start listening again
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "Speech Error on: " + utteranceId);
                }
            });
        }
    }

    /**
     * Speaks the given text. 
     * If the AI is already speaking, this stops the current speech (Barge-in) 
     * and immediately begins the new text.
     */
    public void speak(String text) {
        if (isInitialized && tts != null) {
            // Barge-in Interruption: Stop whatever is currently playing
            tts.stop();
            
            // REQUIREMENT: Use the Utterance ID from constants to track lifecycle
            // Queue Flush ensures it starts immediately (Barge-in)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, AppConstants.TTS_UTTERANCE_ID);
        }
    }

    /**
     * Checks if the TTS hardware is currently outputting audio.
     */
    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}