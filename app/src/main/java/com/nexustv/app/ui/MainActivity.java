package com.nexustv.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.*;
import com.google.android.exoplayer2.util.MimeTypes;
import com.nexustv.app.R;
import com.nexustv.app.data.*;
import com.nexustv.app.util.EpgParser;
import com.nexustv.app.util.M3UParser;
import com.nexustv.app.util.UpdateChecker;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity implements ChannelAdapter.Listener {

    private static final int TAB_ALL = 0, TAB_FAV = 1, TAB_RECENT = 2;
    private static final long BACK_EXIT_DELAY = 2000;

    public static Map<String, List<EpgProgram>> sharedEpgData = new HashMap<>();
    public static ExoPlayer sharedPlayer = null;

    // ── Focus zones ───────────────────────────────────────────────────────
    // CHANNEL_LIST = default, NAVBAR = top bar
    // D-pad rules:
    //   Up from top of list  → NAVBAR
    //   Down from NAVBAR      → CHANNEL_LIST
    //   Left from CHANNEL_LIST → NAVBAR
    //   Right from CHANNEL_LIST → nothing (swallowed)
    //   Back from NAVBAR     → CHANNEL_LIST
    private static final int FOCUS_CHANNEL_LIST = 0;
    private static final int FOCUS_NAVBAR       = 1;
    private int focusZone = FOCUS_CHANNEL_LIST;

    // Views
    private StyledPlayerView playerView;
    private RecyclerView     channelList;
    private TextView         channelCountLabel;
    private TextView         nowPlayingName, nowPlayingGroup;
    private TextView         epgCurrentTitle, epgCurrentSubtitle, epgCurrentTime;
    private TextView         epgNext1Title, epgNext1Time;
    private TextView         epgNext2Title, epgNext2Time;
    private ProgressBar      epgProgressBar;
    private Button           tabAll, tabFav, tabRecent, btnSettings, btnFullscreen;
    private View             channelPanel, panelDivider, infoBar, navbar;

    // OSD (fullscreen only)
    private View        osdCard;
    private ImageView   osdLogo;
    private TextView    osdChannelName, osdChannelGroup, osdProgramTitle,
                        osdProgramSubtitle, osdProgramTime, osdProgramDesc, osdToast;
    private ProgressBar osdProgressBar;

    // Help overlay
    private FrameLayout helpOverlay;
    private LinearLayout helpColMain, helpColFullscreen;

    // State
    private boolean isFullscreen    = false;
    private boolean helpVisible     = false;
    private boolean backPressedOnce = false;
    private final Runnable backResetRunnable = () -> backPressedOnce = false;

    // Player / data
    private ExoPlayer player;
    private AppStorage storage;
    private ChannelAdapter adapter;
    private HlsMediaSource.Factory hlsFactory;

    private List<Channel> allChannels       = new ArrayList<>();
    private List<Channel> displayedChannels = new ArrayList<>();
    private Channel currentChannel = null;
    private int currentTab = TAB_ALL;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Runnable osdHideRunnable;
    private Runnable osdCardHideRunnable;

    private boolean playlistLoaded = false;
    private UpdateChecker updateChecker;

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersive();
        setContentView(R.layout.activity_main);

        storage = new AppStorage(this);
        bindViews();
        setupPlayer();
        setupRecyclerView();
        setupNavbar();
        setupHelpOverlay();
        loadActivePlaylist();
        playlistLoaded = true;

        // Silent background update check on launch
        int versionCode = 20000;
        try { versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; }
        catch (Exception ignored) {}
        updateChecker = new UpdateChecker(this, versionCode);
        updateChecker.checkSilently();
    }

    @Override protected void onResume() {
        super.onResume();
        setImmersive();
        if (!playlistLoaded || allChannels.isEmpty()) {
            loadActivePlaylist();
            playlistLoaded = true;
        }
        // Always return focus to channel list on resume
        focusZone = FOCUS_CHANNEL_LIST;
        channelList.post(() -> channelList.requestFocus());
    }

    @Override protected void onPause()   { super.onPause(); if (player != null) player.pause(); }
    @Override protected void onDestroy() {
        if (player != null) player.release();
        executor.shutdown();
        if (updateChecker != null) updateChecker.shutdown();
        super.onDestroy();
    }

    // ── Bind views ─────────────────────────────────────────────────────────
    private void bindViews() {
        playerView         = findViewById(R.id.playerView);
        channelList        = findViewById(R.id.channelList);
        channelCountLabel  = findViewById(R.id.channelCountLabel);
        nowPlayingName     = findViewById(R.id.nowPlayingName);
        nowPlayingGroup    = findViewById(R.id.nowPlayingGroup);
        epgCurrentTitle    = findViewById(R.id.epgCurrentTitle);
        epgCurrentSubtitle = findViewById(R.id.epgCurrentSubtitle);
        epgCurrentTime     = findViewById(R.id.epgCurrentTime);
        epgProgressBar     = findViewById(R.id.epgProgressBar);
        epgNext1Title      = findViewById(R.id.epgNext1Title);
        epgNext1Time       = findViewById(R.id.epgNext1Time);
        epgNext2Title      = findViewById(R.id.epgNext2Title);
        epgNext2Time       = findViewById(R.id.epgNext2Time);
        tabAll             = findViewById(R.id.tabAll);
        tabFav             = findViewById(R.id.tabFavorites);
        tabRecent          = findViewById(R.id.tabRecent);
        btnSettings        = findViewById(R.id.btnSettings);
        btnFullscreen      = findViewById(R.id.btnFullscreen);
        osdCard            = findViewById(R.id.osdCard);
        osdLogo            = findViewById(R.id.osdLogo);
        osdChannelName     = findViewById(R.id.osdChannelName);
        osdChannelGroup    = findViewById(R.id.osdChannelGroup);
        osdProgramTitle    = findViewById(R.id.osdProgramTitle);
        osdProgramSubtitle = findViewById(R.id.osdProgramSubtitle);
        osdProgramTime     = findViewById(R.id.osdProgramTime);
        osdProgramDesc     = findViewById(R.id.osdProgramDesc);
        osdProgressBar     = findViewById(R.id.osdProgressBar);
        osdToast           = findViewById(R.id.osdToast);
        channelPanel       = findViewById(R.id.channelPanel);
        panelDivider       = findViewById(R.id.panelDivider);
        infoBar            = findViewById(R.id.infoBar);
        navbar             = findViewById(R.id.navbar);
        helpOverlay        = findViewById(R.id.helpOverlay);
        helpColMain        = findViewById(R.id.helpColMain);
        helpColFullscreen  = findViewById(R.id.helpColFullscreen);

        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        playerView.setUseController(false);
    }

    // ── Player ─────────────────────────────────────────────────────────────
    private void setupPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000);

        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, http);
        hlsFactory = new HlsMediaSource.Factory(data);

        player = new ExoPlayer.Builder(this)
            .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 1500, 5000).build())
            .setMediaSourceFactory(
                new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(data))
            .build();

        playerView.setPlayer(player);
        sharedPlayer = player;

        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                showToast("Playback error — trying next format");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) showToast("Buffering…");
            }
        });
    }

    private void playChannel(Channel ch) {
        currentChannel = ch;
        storage.addToHistory(ch.id);
        nowPlayingName.setText(ch.name);
        nowPlayingGroup.setText(ch.group != null ? ch.group : "");
        adapter.setPlayingId(ch.id);

        MediaItem item = new MediaItem.Builder()
            .setUri(Uri.parse(ch.url))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build();
        try { player.setMediaSource(hlsFactory.createMediaSource(item)); }
        catch (Exception e) { player.setMediaItem(item); }
        player.prepare();
        player.setPlayWhenReady(true);

        updateInfoBar(ch);
        showOsdCard(ch);
    }

    private void playChannelFullscreen(Channel ch) {
        playChannel(ch);
        if (!isFullscreen) enterFullscreen();
    }

    // ── Fullscreen ─────────────────────────────────────────────────────────
    private void enterFullscreen() {
        isFullscreen = true;
        channelPanel.setVisibility(View.GONE);
        panelDivider.setVisibility(View.GONE);
        infoBar.setVisibility(View.GONE);
        navbar.setVisibility(View.GONE);
        btnFullscreen.setText("[ X ]");
        playerView.setFocusable(true);
        playerView.setFocusableInTouchMode(true);
        playerView.requestFocus();
    }

    private void exitFullscreen() {
        isFullscreen = false;
        channelPanel.setVisibility(View.VISIBLE);
        panelDivider.setVisibility(View.VISIBLE);
        infoBar.setVisibility(View.VISIBLE);
        navbar.setVisibility(View.VISIBLE);
        btnFullscreen.setText("[ ]");
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        focusZone = FOCUS_CHANNEL_LIST;
        channelList.post(() -> channelList.requestFocus());
    }

    // ── Info bar ───────────────────────────────────────────────────────────
    private void updateInfoBar(Channel ch) {
        String key = (ch.tvgId != null && !ch.tvgId.isEmpty()) ? ch.tvgId : ch.id;
        List<EpgProgram> progs = sharedEpgData.get(key);

        EpgProgram cur = null, nxt1 = null, nxt2 = null;
        if (progs != null) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < progs.size(); i++) {
                if (progs.get(i).startMs <= now && progs.get(i).stopMs > now) {
                    cur  = progs.get(i);
                    if (i + 1 < progs.size()) nxt1 = progs.get(i + 1);
                    if (i + 2 < progs.size()) nxt2 = progs.get(i + 2);
                    break;
                }
            }
        }

        if (cur != null) {
            epgCurrentTitle.setText(cur.title);
            if (cur.subtitle != null && !cur.subtitle.isEmpty()) {
                epgCurrentSubtitle.setText(cur.subtitle);
                epgCurrentSubtitle.setVisibility(View.VISIBLE);
            } else { epgCurrentSubtitle.setVisibility(View.GONE); }
            epgCurrentTime.setText(cur.getTimeRange());
            epgProgressBar.setProgress((int)(cur.getProgress() * 100));
        } else {
            epgCurrentTitle.setText("No guide data");
            epgCurrentSubtitle.setVisibility(View.GONE);
            epgCurrentTime.setText("");
            epgProgressBar.setProgress(0);
        }

        epgNext1Title.setText(nxt1 != null ? nxt1.getDisplayTitle() : "");
        epgNext1Time.setText(nxt1 != null ? nxt1.getTimeRange() : "");
        epgNext2Title.setText(nxt2 != null ? nxt2.getDisplayTitle() : "");
        epgNext2Time.setText(nxt2 != null ? nxt2.getTimeRange() : "");
    }

    // ── OSD card (fullscreen only) ─────────────────────────────────────────
    private void showOsdCard(Channel ch) {
        if (!isFullscreen) return;

        osdChannelName.setText(ch.name);
        osdChannelGroup.setText(ch.group != null ? ch.group : "");

        if (ch.logo != null && !ch.logo.isEmpty()) {
            Glide.with(this).load(ch.logo)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_channel_placeholder)
                .into(osdLogo);
        } else { osdLogo.setImageResource(R.drawable.ic_channel_placeholder); }

        String key = (ch.tvgId != null && !ch.tvgId.isEmpty()) ? ch.tvgId : ch.id;
        EpgProgram cur = getCurrentProgram(key);

        if (cur != null) {
            osdProgramTitle.setText(cur.title);
            if (cur.subtitle != null && !cur.subtitle.isEmpty()) {
                osdProgramSubtitle.setText(cur.subtitle);
                osdProgramSubtitle.setVisibility(View.VISIBLE);
            } else { osdProgramSubtitle.setVisibility(View.GONE); }
            osdProgramTime.setText(cur.getTimeRange());
            osdProgressBar.setProgress((int)(cur.getProgress() * 100));
            if (cur.description != null && !cur.description.isEmpty()) {
                osdProgramDesc.setText(cur.description);
                osdProgramDesc.setVisibility(View.VISIBLE);
            } else { osdProgramDesc.setVisibility(View.GONE); }
        } else {
            osdProgramTitle.setText("Live TV");
            osdProgramSubtitle.setVisibility(View.GONE);
            osdProgramTime.setText("");
            osdProgressBar.setProgress(0);
            osdProgramDesc.setVisibility(View.GONE);
        }

        osdCard.setVisibility(View.VISIBLE);
        if (osdCardHideRunnable != null) mainHandler.removeCallbacks(osdCardHideRunnable);
        osdCardHideRunnable = () -> osdCard.setVisibility(View.GONE);
        mainHandler.postDelayed(osdCardHideRunnable, 5000);
    }

    private EpgProgram getCurrentProgram(String key) {
        List<EpgProgram> progs = sharedEpgData.get(key);
        if (progs == null) return null;
        long now = System.currentTimeMillis();
        for (EpgProgram p : progs) if (p.startMs <= now && p.stopMs > now) return p;
        return null;
    }

    // ── RecyclerView ───────────────────────────────────────────────────────
    private void setupRecyclerView() {
        adapter = new ChannelAdapter(this, this);
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(adapter);
        channelList.setItemAnimator(null);
        channelList.setFocusable(true);
        channelList.setFocusableInTouchMode(true);
        channelList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        channelList.post(() -> channelList.requestFocus());
    }

    // ── Navbar ─────────────────────────────────────────────────────────────
    private void setupNavbar() {
        tabAll.setOnClickListener(v    -> setTab(TAB_ALL));
        tabFav.setOnClickListener(v    -> setTab(TAB_FAV));
        tabRecent.setOnClickListener(v -> setTab(TAB_RECENT));
        btnSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        btnFullscreen.setOnClickListener(v -> {
            if (isFullscreen) exitFullscreen(); else enterFullscreen();
        });

        // Keep focusZone in sync whenever a navbar button gains focus
        View.OnFocusChangeListener navFocus = (v, gained) -> {
            if (gained) focusZone = FOCUS_NAVBAR;
        };
        tabAll.setOnFocusChangeListener(navFocus);
        tabFav.setOnFocusChangeListener(navFocus);
        tabRecent.setOnFocusChangeListener(navFocus);
        btnSettings.setOnFocusChangeListener(navFocus);
        btnFullscreen.setOnFocusChangeListener(navFocus);

        // Keep focusZone in sync when channel list gains focus
        channelList.setOnFocusChangeListener((v, gained) -> {
            if (gained) focusZone = FOCUS_CHANNEL_LIST;
        });

        updateTabHighlight();
    }

    private void setTab(int tab) {
        currentTab = tab;
        updateTabHighlight();
        applyFilters();
        // Return focus to list after tab change
        focusZone = FOCUS_CHANNEL_LIST;
        channelList.post(() -> channelList.requestFocus());
    }

    private void updateTabHighlight() {
        tabAll.setSelected(currentTab == TAB_ALL);
        tabFav.setSelected(currentTab == TAB_FAV);
        tabRecent.setSelected(currentTab == TAB_RECENT);
        int accent  = getColor(R.color.accent);
        int normal  = getColor(R.color.text_secondary);
        tabAll.setTextColor(currentTab == TAB_ALL    ? accent : normal);
        tabFav.setTextColor(currentTab == TAB_FAV    ? accent : normal);
        tabRecent.setTextColor(currentTab == TAB_RECENT ? accent : normal);
    }

    // ── Filters ────────────────────────────────────────────────────────────
    private void applyFilters() {
        Set<String> hidden = storage.getHiddenChannels();
        Set<String> favs   = storage.getFavorites();

        List<Channel> source = new ArrayList<>();
        for (Channel ch : allChannels) if (!hidden.contains(ch.id)) source.add(ch);

        if (currentTab == TAB_FAV) {
            List<Channel> f = new ArrayList<>();
            for (Channel ch : source) if (favs.contains(ch.id)) f.add(ch);
            source = f;
        } else if (currentTab == TAB_RECENT) {
            List<String> history = storage.getHistory();
            List<Channel> r = new ArrayList<>();
            for (String id : history)
                for (Channel ch : source) if (ch.id.equals(id)) { r.add(ch); break; }
            source = r;
        }

        displayedChannels = source;
        adapter.setChannels(displayedChannels, favs, sharedEpgData);
        channelCountLabel.setText(displayedChannels.size() + " channels");
    }

    // ── Load playlist ──────────────────────────────────────────────────────
    private void loadActivePlaylist() {
        Playlist active = storage.getActivePlaylist();
        if (active == null) { channelCountLabel.setText("No playlist — go to Settings"); return; }
        if (active.channels != null && !active.channels.isEmpty()) {
            allChannels = active.channels;
            applyFilters();
            if (active.epgUrl != null && !active.epgUrl.isEmpty()) loadEpg(active.epgUrl);
        } else {
            fetchPlaylist(active);
        }
    }

    private void fetchPlaylist(Playlist p) {
        channelCountLabel.setText("Loading " + p.name + "…");
        executor.execute(() -> {
            try {
                String url = p.url;
                String body;
                if (url.startsWith("content://") || url.startsWith("file://")) {
                    java.io.InputStream is =
                        getContentResolver().openInputStream(android.net.Uri.parse(url));
                    java.io.BufferedReader br =
                        new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    br.close();
                    body = sb.toString();
                } else {
                    okhttp3.Response resp = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS).build()
                        .newCall(new okhttp3.Request.Builder().url(url)
                            .header("User-Agent", "NexusTV/2.0").build()).execute();
                    body = resp.body() != null ? resp.body().string() : "";
                }
                List<Channel> channels = M3UParser.parse(body);
                storage.updatePlaylistChannels(p.id, channels);
                mainHandler.post(() -> {
                    allChannels = channels;
                    applyFilters();
                    showToast("Loaded " + channels.size() + " channels");
                    if (p.epgUrl != null && !p.epgUrl.isEmpty()) loadEpg(p.epgUrl);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    channelCountLabel.setText("Load failed");
                    showToast("Error: " + e.getMessage());
                });
            }
        });
    }

    private void loadEpg(String url) {
        showToast("Loading TV guide…");
        executor.execute(() -> {
            try {
                okhttp3.Response resp = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).build()
                    .newCall(new okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "NexusTV/2.0").build()).execute();
                String body = resp.body() != null ? resp.body().string() : "";
                Map<String, List<EpgProgram>> data = EpgParser.parse(body);
                mainHandler.post(() -> {
                    sharedEpgData = data;
                    adapter.updateEpg(data);
                    if (currentChannel != null) updateInfoBar(currentChannel);
                    showToast("Guide ready: " + data.size() + " channels");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showToast("Guide failed: " + e.getMessage()));
            }
        });
    }

    // ── Channel surf (fullscreen only) ─────────────────────────────────────
    private void skipChannel(int delta) {
        if (displayedChannels.isEmpty()) return;
        int cur = 0;
        if (currentChannel != null) {
            for (int i = 0; i < displayedChannels.size(); i++) {
                if (displayedChannels.get(i).id.equals(currentChannel.id)) { cur = i; break; }
            }
        }
        int next = (cur + delta + displayedChannels.size()) % displayedChannels.size();
        playChannel(displayedChannels.get(next));
        channelList.scrollToPosition(next);
    }

    // ── ChannelAdapter callbacks ───────────────────────────────────────────
    @Override public void onChannelClicked(Channel ch, int pos) {
        playChannelFullscreen(ch);
    }

    @Override public void onChannelHighlighted(Channel ch, int pos) { /* not used */ }

    @Override public void onFavoriteToggled(Channel ch, int pos) {
        storage.toggleFavorite(ch.id);
        showToast(storage.isFavorite(ch.id) ? "Added to Favorites" : "Removed from Favorites");
        applyFilters();
    }

    @Override public void onChannelLongPress(Channel ch, int pos) {
        boolean isFav = storage.isFavorite(ch.id);
        new AlertDialog.Builder(this)
            .setTitle(ch.name)
            .setItems(new CharSequence[]{
                isFav ? "Remove from Favorites" : "Add to Favorites",
                "Hide Channel",
                "Cancel"
            }, (d, w) -> {
                if (w == 0) {
                    storage.toggleFavorite(ch.id);
                    showToast(storage.isFavorite(ch.id)
                        ? "Added to Favorites" : "Removed from Favorites");
                    applyFilters();
                } else if (w == 1) {
                    storage.hideChannel(ch.id);
                    showToast("Channel hidden");
                    applyFilters();
                }
            }).show();
    }

    // ── Help overlay ───────────────────────────────────────────────────────
    private void setupHelpOverlay() {
        buildHelpRows(helpColMain, new String[][]{
            {"Up / Down",      "Scroll channel list"},
            {"Left",           "Jump to top navbar"},
            {"OK / Select",    "Play channel → fullscreen"},
            {"Long-press OK",  "Favorites / Hide channel"},
            {"Back (×2)",      "Press twice to exit"},
            {"Info key",       "Show this help screen"},
        });
        buildHelpRows(helpColFullscreen, new String[][]{
            {"CH ▲",           "Previous channel"},
            {"CH ▼",           "Next channel"},
            {"Back",           "Exit fullscreen"},
            {"Info key",       "Show this help screen"},
        });
    }

    private void buildHelpRows(LinearLayout container, String[][] rows) {
        container.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (String[] row : rows) {
            View v = inf.inflate(R.layout.item_help_row, container, false);
            ((TextView) v.findViewById(R.id.helpKey)).setText(row[0]);
            ((TextView) v.findViewById(R.id.helpDesc)).setText(row[1]);
            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(0x22FFFFFF);
            container.addView(v);
            container.addView(div);
        }
    }

    private void showHelp() {
        helpVisible = true;
        helpOverlay.setVisibility(View.VISIBLE);
        helpOverlay.requestFocus();
    }

    private void hideHelp() {
        helpVisible = false;
        helpOverlay.setVisibility(View.GONE);
        if (isFullscreen) playerView.requestFocus();
        else channelList.post(() -> channelList.requestFocus());
    }

    // ── Toast ──────────────────────────────────────────────────────────────
    public void showToast(String msg) {
        osdToast.setText(msg);
        osdToast.setVisibility(View.VISIBLE);
        if (osdHideRunnable != null) mainHandler.removeCallbacks(osdHideRunnable);
        osdHideRunnable = () -> osdToast.setVisibility(View.GONE);
        mainHandler.postDelayed(osdHideRunnable, 3000);
    }

    // ── Key handling ───────────────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // ── Help overlay absorbs everything except close keys ──
        if (helpVisible) {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER) {
                hideHelp();
            }
            return true; // swallow all keys while help is showing
        }

        // ── Global keys (work everywhere) ──
        switch (keyCode) {
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_CAPTIONS:
                showHelp();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
                return true;
        }

        // ── FULLSCREEN ────────────────────────────────────────────────────
        if (isFullscreen) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    exitFullscreen();
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    skipChannel(-1); // CH▲ = previous
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    skipChannel(1);  // CH▼ = next
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
            }
            // Pass everything else to super (so D-pad center still fires onClick on player)
            return super.onKeyDown(keyCode, event);
        }

        // ── MAIN SCREEN ───────────────────────────────────────────────────
        switch (keyCode) {

            case KeyEvent.KEYCODE_DPAD_UP:
                if (focusZone == FOCUS_CHANNEL_LIST) {
                    LinearLayoutManager llm =
                        (LinearLayoutManager) channelList.getLayoutManager();
                    // Only jump to navbar if we are already at the very top item
                    if (llm != null && llm.findFirstCompletelyVisibleItemPosition() == 0) {
                        focusZone = FOCUS_NAVBAR;
                        tabAll.requestFocus();
                        return true;
                    }
                    return false; // let RecyclerView scroll normally
                }
                return false;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focusZone == FOCUS_NAVBAR) {
                    // nextFocusDown in XML already moves focus to channelList,
                    // just sync our zone tracker and let it happen
                    focusZone = FOCUS_CHANNEL_LIST;
                    channelList.post(() -> channelList.requestFocus());
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focusZone == FOCUS_CHANNEL_LIST) {
                    // Left from list → go to navbar
                    focusZone = FOCUS_NAVBAR;
                    tabAll.requestFocus();
                    return true;
                }
                // In navbar, let Android handle left/right between buttons naturally
                return false;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focusZone == FOCUS_CHANNEL_LIST) {
                    // Nothing to the right — swallow so focus doesn't wander
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_BACK:
                if (focusZone == FOCUS_NAVBAR) {
                    // Back from navbar → return to channel list
                    focusZone = FOCUS_CHANNEL_LIST;
                    channelList.post(() -> channelList.requestFocus());
                    return true;
                }
                // Double-back to exit
                if (backPressedOnce) {
                    mainHandler.removeCallbacks(backResetRunnable);
                    finish();
                    return true;
                }
                backPressedOnce = true;
                showToast("Press Back again to exit");
                mainHandler.postDelayed(backResetRunnable, BACK_EXIT_DELAY);
                return true;

            case KeyEvent.KEYCODE_MENU:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            // Channel buttons disabled on main screen
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return true; // swallow
        }

        return super.onKeyDown(keyCode, event);
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
