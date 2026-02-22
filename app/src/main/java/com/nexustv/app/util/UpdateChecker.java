package com.nexustv.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String GITHUB_API =
        "https://api.github.com/repos/ctsw05/nexustv-android/releases/latest";

    private final Activity activity;
    private final int currentVersionCode;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UpdateChecker(Activity activity, int currentVersionCode) {
        this.activity = activity;
        this.currentVersionCode = currentVersionCode;
    }

    /** Called on launch — silent, no "up to date" message */
    public void checkSilently() {
        check(false, null);
    }

    /** Called from Settings button — shows "up to date" if no update found */
    public void checkManually(java.util.function.Consumer<Boolean> onResult) {
        check(true, onResult);
    }

    private void check(boolean showIfUpToDate, java.util.function.Consumer<Boolean> onResult) {
        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    new URL(GITHUB_API).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    if (onResult != null) mainHandler.post(() -> onResult.accept(false));
                    return;
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.getString("tag_name");
                String apkUrl  = null;

                org.json.JSONArray assets = json.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url");
                        break;
                    }
                }

                int remoteCode = parseVersionCode(tagName);
                final boolean updateAvailable = remoteCode > currentVersionCode && apkUrl != null;
                final String finalApkUrl  = apkUrl;
                final String finalTagName = tagName;

                mainHandler.post(() -> {
                    if (updateAvailable) {
                        if (onResult != null) onResult.accept(true);
                        showUpdateDialog(finalTagName, finalApkUrl);
                    } else {
                        if (onResult != null) onResult.accept(false);
                    }
                });

            } catch (Exception ignored) {
                if (onResult != null) mainHandler.post(() -> onResult.accept(false));
            }
        });
    }

    private void showUpdateDialog(String version, String apkUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("Version " + version + " is available.\nDownload and install now?")
            .setPositiveButton("Update", (d, w) -> downloadAndInstall(apkUrl))
            .setNegativeButton("Not Now", null)
            .show();
    }

    private void downloadAndInstall(String apkUrl) {
        try {
            String fileName = "nexustv-update.apk";
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Nexus TV Update")
                .setDescription("Downloading update…")
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            long downloadId = dm.enqueue(request);

            // Listen for download complete
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        activity.unregisterReceiver(this);
                        File apkFile = new File(
                            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                        installApk(apkFile);
                    }
                }
            };
            activity.registerReceiver(receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        } catch (Exception e) {
            mainHandler.post(() -> new AlertDialog.Builder(activity)
                .setTitle("Download Failed")
                .setMessage(e.getMessage())
                .setPositiveButton("OK", null).show());
        }
    }

    private void installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            mainHandler.post(() -> new AlertDialog.Builder(activity)
                .setTitle("Install Failed")
                .setMessage("Could not launch installer: " + e.getMessage())
                .setPositiveButton("OK", null).show());
        }
    }

    /** Convert tag like "v2.0.1" → 20001, "v2.1.0" → 21000 */
    private int parseVersionCode(String tag) {
        try {
            String clean = tag.replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + patch;
        } catch (Exception e) {
            return 0;
        }
    }

    public void shutdown() { executor.shutdown(); }
}
