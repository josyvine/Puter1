package com.puter.unofficial;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Native Android Text-To-Speech Manager.
 * Handles the speech synthesis and barge-in (interruption) logic.
 * UPDATED: Integrated UtteranceProgressListener for continuous conversation flow.
 * REFINED: Added AudioFocus management to support simultaneous microphone listening (Barge-in).
 */
public class TTSManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "PuterTTSManager";
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private final Context context;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    public TTSManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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
                    // Relinquish audio focus so microphone can take priority if needed
                    abandonFocus();
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "Speech Error on: " + utteranceId);
                    abandonFocus();
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
            // REQUIREMENT #2: Request Audio Focus to allow simultaneous Mic use
            requestAudioFocus();
            
            // Barge-in Interruption: Stop whatever is currently playing
            tts.stop();
            
            // REQUIREMENT: Use the Utterance ID from constants to track lifecycle
            // Queue Flush ensures it starts immediately (Barge-in)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, AppConstants.TTS_UTTERANCE_ID);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
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
            abandonFocus();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            abandonFocus();
        }
    }
}