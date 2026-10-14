package com.gbcore.remote;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Details of the phone sent to support with the connection request (same keys as Windows). */
final class DeviceInfo {
    private DeviceInfo() {
    }

    static String name() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        return model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT)) ? model : maker + " " + model;
    }

    static JSONObject collect(Context context) {
        JSONObject info = new JSONObject();
        try {
            info.put("app_version", BuildConfig.VERSION_NAME);
            info.put("device_type", "phone");

            JSONObject os = new JSONObject();
            os.put("name", "Android " + Build.VERSION.RELEASE);
            os.put("version", String.valueOf(Build.VERSION.SDK_INT));
            os.put("build", Build.DISPLAY);
            os.put("architecture", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            info.put("os", os);

            JSONObject device = new JSONObject();
            device.put("manufacturer", Build.MANUFACTURER);
            device.put("model", Build.MODEL);
            device.put("cores", Runtime.getRuntime().availableProcessors());
            info.put("computer", device);

            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            info.put("memory_mb", memory.totalMem / (1024 * 1024));

            StatFs storage = new StatFs(Environment.getDataDirectory().getPath());
            long gb = 1024L * 1024 * 1024;
            info.put("storage_gb", storage.getBlockCountLong() * storage.getBlockSizeLong() / gb);
            info.put("storage_free_gb", storage.getAvailableBlocksLong() * storage.getBlockSizeLong() / gb);

            info.put("browsers", browsers(context));
            info.put("printers", new JSONArray());

            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            info.put("screen", metrics.widthPixels + "x" + metrics.heightPixels + " @" + metrics.densityDpi + "dpi");
            info.put("language", Locale.getDefault().toString());
            info.put("timezone", TimeZone.getDefault().getID());
        } catch (Exception ignored) {
            // the details are a help for support: never block the connection for them
        }
        return info;
    }

    private static JSONArray browsers(Context context) {
        JSONArray list = new JSONArray();
        PackageManager packages = context.getPackageManager();
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"));
        int flags = Build.VERSION.SDK_INT >= 23 ? PackageManager.MATCH_ALL : 0;
        Set<String> seen = new HashSet<>();
        for (ResolveInfo resolved : packages.queryIntentActivities(web, flags)) {
            String name = resolved.activityInfo.packageName;
            if (!seen.add(name)) {
                continue;
            }
            try {
                JSONObject browser = new JSONObject();
                browser.put("name", resolved.loadLabel(packages).toString());
                browser.put("version", packages.getPackageInfo(name, 0).versionName);
                list.put(browser);
            } catch (Exception ignored) {
                // a browser that disappears while listing
            }
        }
        return list;
    }
}
