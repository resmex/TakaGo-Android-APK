package com.takago.app.db;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores who is currently logged in (id, name, role) using SharedPreferences. */
public class SessionManager {

    private static final String PREFS_NAME = "takago_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_ROLE = "role";
    private static final String KEY_API_TOKEN = "api_token";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(int userId, String name, String role) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_ROLE, role);
        editor.apply();
    }

    public void saveApiSession(int userId, String name, String role, String token) {
        saveSession(userId, name, role);
        prefs.edit().putString(KEY_API_TOKEN, token).apply();
    }

    public String getApiToken() { return prefs.getString(KEY_API_TOKEN, ""); }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "");
    }

    public boolean isLoggedIn() {
        return getUserId() != -1;
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
