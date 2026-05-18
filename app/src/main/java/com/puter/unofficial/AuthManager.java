package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the authentication state for Puter Unofficial.
 * Uses SharedPreferences to persist login status across app restarts,
 * ensuring the user is not forced to sign in every time the app opens.
 */
public class AuthManager {

    private static final String PREF_NAME = "PuterPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static AuthManager instance;
    private final SharedPreferences prefs;

    /**
     * Private constructor for Singleton pattern.
     */
    private AuthManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the global instance of the AuthManager.
     */
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    /**
     * Saves the current authentication status.
     * Called when the browser redirect indicates a successful Puter login.
     * 
     * @param status true if the user is authenticated, false otherwise.
     */
    public void setLoggedIn(boolean status) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, status).apply();
    }

    /**
     * Checks if the user is currently considered logged in.
     * This status is used by the WebView bridge to update the HTML UI.
     * 
     * @return true if status is saved as logged in.
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Clears the authentication state.
     * Triggered when the user selects "Sign Out" from the app menu.
     */
    public void logout() {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();
    }

    /**
     * Helper to verify if a specific URL is a Puter authentication success callback.
     * In a production app, Puter would redirect to a specific domain or scheme.
     * 
     * @param url The URL being loaded by the WebView.
     * @return true if the URL matches the auth success pattern.
     */
    public boolean isAuthCallback(String url) {
        // This is a placeholder for the Puter redirect URI pattern
        // Example: https://puter.com/auth/callback or a custom app scheme
        return url != null && url.contains("puter.com") && url.contains("auth_success");
    }
}