package com.nexustv.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.nexustv.app.R;
import com.nexustv.app.data.*;
import com.nexustv.app.util.M3UParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.*;

public class SettingsActivity extends AppCompatActivity implements PlaylistAdapter.Listener {

    private static final int REQUEST_PICK_FILE = 200;

    private RecyclerView playlistList;
    private EditText inputName, inputUrl, inputEpgUrl;
    private Button btnSave, btnClear, btnBack, btnUnhideAll, btnBrowseFile, btnCheckUpdate;
    private TextView statusMsg, hiddenCount, selectedFileName;

    private AppStorage storage;
    private PlaylistAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Holds the URI of a locally-picked file (null if URL mode)
    private Uri pickedFileUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setImmersive();
        setContentView(R.layout.activity_settings);

        storage = new AppStorage(this);

        playlistList     = findViewById(R.id.playlistList);
        inputName        = findViewById(R.id.inputName);
        inputUrl         = findViewById(R.id.inputUrl);
        inputEpgUrl      = findViewById(R.id.inputEpgUrl);
        btnSave          = findViewById(R.id.btnSave);
        btnClear         = findViewById(R.id.btnClear);
        btnBack          = findViewById(R.id.btnBack);
        btnUnhideAll     = findViewById(R.id.btnUnhideAll);
        btnBrowseFile    = findViewById(R.id.btnBrowseFile);
        statusMsg        = findViewById(R.id.statusMsg);
        hiddenCount      = findViewById(R.id.hiddenCount);
        selectedFileName = findViewById(R.id.selectedFileName);

        btnCheckUpdate   = findViewById(R.id.btnCheckUpdate);

        adapter = new PlaylistAdapter(this, this);
        playlistList.setLayoutManager(new LinearLayoutManager(this));
        playlistList.setAdapter(adapter);
        playlistList.setItemAnimator(null);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> savePlaylist());
        btnClear.setOnClickListener(v -> clearForm());
        btnBrowseFile.setOnClickListener(v -> openFilePicker());
        btnUnhideAll.setOnClickListener(v -> {
            storage.unhideAllChannels();
            refreshHiddenCount();
            setStatus("All channels are now visible.");
        });
        btnCheckUpdate.setOnClickListener(v -> {
            setStatus("Checking for updates…");
            int versionCode = 20000;
            try {
                versionCode = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception ignored) {}
            com.nexustv.app.util.UpdateChecker checker =
                new com.nexustv.app.util.UpdateChecker(this, versionCode);
            checker.checkManually(found -> {
                if (!found) setStatus("You're up to date!");
            });
        });

        // When user types in URL field, clear any picked file
        inputUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s.length() > 0 && pickedFileUri != null) {
                    pickedFileUri = null;
                    selectedFileName.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        refreshList();
        refreshHiddenCount();
    }

    // ── File Picker ────────────────────────────────────────────────────────

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select M3U File"), REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pickedFileUri = uri;
                String fileName = getFileNameFromUri(uri);
                selectedFileName.setText("✓ Selected: " + fileName);
                selectedFileName.setVisibility(View.VISIBLE);
                // Clear the URL field since we're using a file
                inputUrl.setText("");
                setStatus("File selected — enter a name and press Save.");
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        // Try content resolver for display name first
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    cursor.close();
                    if (name != null && !name.isEmpty()) return name;
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        // Fallback to path parsing
        String path = uri.getPath();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) return path.substring(slash + 1);
        }
        return uri.toString();
    }

    // ── Save Playlist ──────────────────────────────────────────────────────

    private void refreshList() {
        adapter.setData(storage.getPlaylists(), storage.getActivePlaylistId());
    }

    private void refreshHiddenCount() {
        int count = storage.getHiddenChannels().size();
        if (hiddenCount != null) {
            hiddenCount.setText(count + " channel" + (count == 1 ? "" : "s") + " hidden");
        }
    }

    private void savePlaylist() {
        String name   = inputName.getText().toString().trim();
        String epgUrl = inputEpgUrl.getText().toString().trim();

        if (name.isEmpty()) { setStatus("Please enter a playlist name."); return; }

        if (pickedFileUri != null) {
            saveFromFile(name, epgUrl, pickedFileUri);
        } else {
            String url = inputUrl.getText().toString().trim();
            if (url.isEmpty())  { setStatus("Please enter an M3U URL or pick a file."); return; }
            if (!url.startsWith("http")) { setStatus("URL must start with http:// or https://"); return; }
            saveFromUrl(name, url, epgUrl);
        }
    }

    private void saveFromUrl(String name, String url, String epgUrl) {
        setStatus("Fetching playlist…");
        btnSave.setEnabled(false);

        executor.execute(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
                okhttp3.Response resp = client.newCall(
                    new okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "NexusTV/2.0").build()
                ).execute();
                String body = resp.body() != null ? resp.body().string() : "";

                String resolvedEpg = epgUrl.isEmpty() ? M3UParser.extractEpgUrl(body) : epgUrl;
                List<Channel> channels = M3UParser.parse(body);
                Playlist p = new Playlist(UUID.randomUUID().toString(), name, url, resolvedEpg);
                p.channels = channels;
                storage.addPlaylist(p);

                final String finalEpg = resolvedEpg;
                mainHandler.post(() -> {
                    String status = "✓ Added \"" + name + "\" - " + channels.size() + " channels";
                    if (!finalEpg.isEmpty()) status += " (EPG linked)";
                    setStatus(status);
                    btnSave.setEnabled(true);
                    clearForm();
                    refreshList();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setStatus("✗ Failed: " + e.getMessage());
                    btnSave.setEnabled(true);
                });
            }
        });
    }

    private void saveFromFile(String name, String epgUrl, Uri fileUri) {
        setStatus("Reading file…");
        btnSave.setEnabled(false);

        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(fileUri);
                if (is == null) throw new Exception("Cannot read file");

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                is.close();

                String body = sb.toString();
                if (body.trim().isEmpty()) throw new Exception("File is empty");
                if (!body.contains("#EXTM3U") && !body.contains("#EXTINF")) {
                    throw new Exception("File does not appear to be a valid M3U");
                }

                String resolvedEpg = epgUrl.isEmpty() ? M3UParser.extractEpgUrl(body) : epgUrl;
                List<Channel> channels = M3UParser.parse(body);

                // Store the content URI string so we can re-read if needed
                String storedUrl = fileUri.toString();
                Playlist p = new Playlist(UUID.randomUUID().toString(), name, storedUrl, resolvedEpg);
                p.channels = channels;
                storage.addPlaylist(p);

                final String finalEpg = resolvedEpg;
                mainHandler.post(() -> {
                    String status = "✓ Added \"" + name + "\" from file — " + channels.size() + " channels";
                    if (!finalEpg.isEmpty()) status += " (EPG linked)";
                    setStatus(status);
                    btnSave.setEnabled(true);
                    clearForm();
                    refreshList();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setStatus("✗ Failed: " + e.getMessage());
                    btnSave.setEnabled(true);
                });
            }
        });
    }

    private void clearForm() {
        inputName.setText("");
        inputUrl.setText("");
        inputEpgUrl.setText("");
        pickedFileUri = null;
        selectedFileName.setVisibility(View.GONE);
    }

    private void setStatus(String msg) { statusMsg.setText(msg); }

    @Override
    public void onPlaylistClicked(Playlist p) {
        storage.setActivePlaylistId(p.id);
        refreshList();
        setStatus("✓ Active: " + p.name);
    }

    @Override
    public void onDeleteClicked(Playlist p) {
        storage.deletePlaylist(p.id);
        refreshList();
        setStatus("Deleted \"" + p.name + "\"");
    }

    @Override
    protected void onDestroy() { executor.shutdown(); super.onDestroy(); }

    private void setImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
