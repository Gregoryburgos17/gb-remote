package com.gbcore.remote;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Calls to /gb_remote/api/v1 of the GB platform (same API as the Windows app). */
final class Api {
    private Api() {
    }

    static JSONObject post(String server, String path, JSONObject body) throws Exception {
        URL url = new URL(server.replaceAll("/+$", "") + "/gb_remote/api/v1/" + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "GBRemote/" + BuildConfig.VERSION_NAME + " (Android)");
            byte[] data = body.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(data.length);
            OutputStream out = connection.getOutputStream();
            out.write(data);
            out.close();
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) {
                throw new Exception("HTTP " + status);
            }
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                text.write(buffer, 0, read);
            }
            in.close();
            return new JSONObject(text.toString("UTF-8"));
        } finally {
            connection.disconnect();
        }
    }
}
