package com.gbcore.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

/** What the app remembers: the server, the company, the device code and its secret. */
final class Store {
    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getSharedPreferences("gbremote", Context.MODE_PRIVATE);
        if (secret().isEmpty()) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String value = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            prefs.edit().putString("secret", value).apply();
        }
    }

    String server() {
        return prefs.getString("server", BuildConfig.DEFAULT_SERVER);
    }

    String company() {
        return prefs.getString("company", "");
    }

    String deviceCode() {
        return prefs.getString("device_code", "");
    }

    String secret() {
        return prefs.getString("secret", "");
    }

    void setServer(String value) {
        prefs.edit().putString("server", value).apply();
    }

    void setDevice(String company, String deviceCode) {
        prefs.edit().putString("company", company).putString("device_code", deviceCode).apply();
    }

    void forgetDevice() {
        prefs.edit().putString("device_code", "").apply();
    }
}
