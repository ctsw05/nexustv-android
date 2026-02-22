package com.nexustv.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.nexustv.app.R;
import com.nexustv.app.data.*;
import java.util.*;

public class GuideActivity extends AppCompatActivity {

    public static final String EXTRA_CHANNEL_ID = "selected_channel_id";
    public static final String EXTRA_IS_OVERLAY = "is_overlay";

    private static final int TOTAL_HOURS  = 8;
    private static final int MINS_PER_SLOT = 30;

    // Views
    private RecyclerView     guideChannelCol, epgRowList;
    private HorizontalScrollView timeHeaderScroll;
    private LinearLayout     epgTimeHeader;
    private TextView         guideTime, guideSelectedInfo;
    private Button           btnWatch, btnClose;
    private StyledPlayerView guidePipPlayer;

    // Data
    private AppStorage storage;
    private List<Channel> channels = new ArrayList<>();
    private Map<String, List<EpgProgram>> epgData = new HashMap<>();
    private Channel selectedChannel;
    private EpgProgram selectedProgram;
    private long windowStartMs;

    // Grid state
    private EpgRowAdapter rowAdapter;
    private ChannelColAdapter chanAdapter;
    private int focusedRow = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    private boolean isOverlay = false;

    // Shared horizontal scroll position across all rows
    private int sharedScrollX = 0;
    private boolean syncingScroll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        setImmersive();

        storage   = new AppStorage(this);
        epgData   = MainActivity.sharedEpgData;
        isOverlay = getIntent().getBooleanExtra(EXTRA_IS_OVERLAY, false);

        bindViews();
        setupWindowStart();
        loadChannels();
        buildTimeHeader();
        setupChannelCol();
        setupRowList();
        setupButtons();
        setupPip();
        startClock();

        // Scroll to now after layout
        handler.postDelayed(this::scrollToNow, 200);

        // Pre-select initial channel
        String initId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        if (initId != null) {
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i).id.equals(initId)) {
                    focusedRow = i;
                    selectedChannel = channels.get(i);
                    break;
                }
            }
        }
        if (selectedChannel == null && !channels.isEmpty()) selectedChannel = channels.get(0);
        handler.postDelayed(() -> {
            epgRowList.scrollToPosition(focusedRow);
            guideChannelCol.scrollToPosition(focusedRow);
            rowAdapter.setFocusedRow(focusedRow);
            epgRowList.requestFocus();
        }, 300);
    }

    private void bindViews() {
        guideChannelCol  = findViewById(R.id.guideChannelCol);
        epgRowList       = findViewById(R.id.epgRowList);
        timeHeaderScroll = findViewById(R.id.timeHeaderScroll);
        epgTimeHeader    = findViewById(R.id.epgTimeHeader);
        guideTime        = findViewById(R.id.guideTime);
        guideSelectedInfo= findViewById(R.id.guideSelectedInfo);
        btnWatch         = findViewById(R.id.btnGuideWatch);
        btnClose         = findViewById(R.id.btnGuideClose);
        guidePipPlayer   = findViewById(R.id.guidePipPlayer);
    }

    private void setupWindowStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        windowStartMs = cal.getTimeInMillis();
    }

    private void loadChannels() {
        Playlist active = storage.getActivePlaylist();
        Set<String> hidden = storage.getHiddenChannels();
        if (active != null && active.channels != null) {
            for (Channel ch : active.channels)
                if (!hidden.contains(ch.id)) channels.add(ch);
        }
    }

    private void buildTimeHeader() {
        epgTimeHeader.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int slotCount = TOTAL_HOURS * 60 / MINS_PER_SLOT;
        int slotWidthPx = (int)(MINS_PER_SLOT * EpgGridView.PX_PER_MIN * density);

        for (int i = 0; i < slotCount; i++) {
            long slotMs = windowStartMs + (long) i * MINS_PER_SLOT * 60 * 1000;

            View divider = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                (int)(1 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            divider.setLayoutParams(dlp);
            divider.setBackgroundColor(0xFF1A2530);

            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                slotWidthPx, ViewGroup.LayoutParams.MATCH_PARENT);
            tv.setLayoutParams(lp);
            tv.setText(EpgProgram.formatTime(slotMs));
            tv.setTextSize(11f);
            tv.setTextColor(0xFF888888);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding((int)(8 * density), 0, 0, 0);

            epgTimeHeader.addView(divider);
            epgTimeHeader.addView(tv);
        }
    }

    private void setupChannelCol() {
        chanAdapter = new ChannelColAdapter(channels);
        guideChannelCol.setLayoutManager(new LinearLayoutManager(this));
        guideChannelCol.setAdapter(chanAdapter);
        guideChannelCol.setFocusable(false);
        guideChannelCol.setItemAnimator(null);
    }

    private void setupRowList() {
        long windowEndMs = windowStartMs + TOTAL_HOURS * 3600000L;
        rowAdapter = new EpgRowAdapter(this, channels, epgData, windowStartMs, windowEndMs,
            new EpgRowAdapter.Listener() {
                @Override
                public void onRowFocused(int row, Channel ch, EpgProgram prog) {
                    focusedRow = row;
                    selectedChannel = ch;
                    selectedProgram = prog;
                    chanAdapter.setSelected(row);
                    // Sync channel column scroll
                    LinearLayoutManager llm = (LinearLayoutManager) guideChannelCol.getLayoutManager();
                    if (llm != null) llm.scrollToPositionWithOffset(row, 0);
                    // Update header
                    if (prog != null)
                        guideSelectedInfo.setText(ch.name + "  •  " + prog.title + "  " + prog.getTimeRange());
                    else
                        guideSelectedInfo.setText(ch.name);
                }
                @Override
                public void onProgramSelected(Channel ch, EpgProgram prog) {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_CHANNEL_ID, ch.id);
                    setResult(RESULT_OK, result);
                    finish();
                }
                @Override
                public void onScrollXChanged(int scrollX) {
                    if (!syncingScroll) {
                        syncingScroll = true;
                        sharedScrollX = scrollX;
                        rowAdapter.setSharedScrollX(scrollX);
                        timeHeaderScroll.scrollTo(scrollX, 0);
                        syncingScroll = false;
                    }
                }
            });

        LinearLayoutManager llm = new LinearLayoutManager(this);
        epgRowList.setLayoutManager(llm);
        epgRowList.setAdapter(rowAdapter);
        epgRowList.setItemAnimator(null);
        epgRowList.setFocusable(true);

        // Sync channel column when rows scroll vertically
        epgRowList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                guideChannelCol.scrollBy(0, dy);
            }
        });
    }

    private void scrollToNow() {
        float density = getResources().getDisplayMetrics().density;
        long nowMs = System.currentTimeMillis();
        float nowX = (nowMs - windowStartMs) / 60000f * EpgGridView.PX_PER_MIN * density;
        float slotW = MINS_PER_SLOT * EpgGridView.PX_PER_MIN * density;
        int scrollTo = Math.max(0, (int)(nowX - slotW));
        sharedScrollX = scrollTo;
        rowAdapter.setSharedScrollX(scrollTo);
        timeHeaderScroll.scrollTo(scrollTo, 0);
    }

    private void setupButtons() {
        btnClose.setOnClickListener(v -> finish());
        btnWatch.setOnClickListener(v -> {
            if (selectedChannel != null) {
                Intent result = new Intent();
                result.putExtra(EXTRA_CHANNEL_ID, selectedChannel.id);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }

    private void setupPip() {
        if (isOverlay && MainActivity.sharedPlayer != null) {
            guidePipPlayer.setVisibility(View.VISIBLE);
            guidePipPlayer.setPlayer(MainActivity.sharedPlayer);
            guidePipPlayer.setUseController(false);
        }
    }

    private void startClock() {
        clockRunnable = new Runnable() {
            @Override public void run() {
                guideTime.setText(EpgProgram.formatTime(System.currentTimeMillis()));
                handler.postDelayed(this, 30000);
            }
        };
        handler.post(clockRunnable);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockRunnable);
        if (guidePipPlayer != null) guidePipPlayer.setPlayer(null);
        super.onDestroy();
    }

    private void setImmersive() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ── Channel column adapter ─────────────────────────────────────────────
    static class ChannelColAdapter extends RecyclerView.Adapter<ChannelColAdapter.VH> {
        private final List<Channel> channels;
        private int selectedPos = 0;

        ChannelColAdapter(List<Channel> ch) { this.channels = ch; }

        void setSelected(int pos) {
            int old = selectedPos; selectedPos = pos;
            notifyItemChanged(old); notifyItemChanged(pos);
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_epg_channel, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Channel ch = channels.get(pos);
            h.name.setText(ch.name);
            h.itemView.setBackgroundColor(pos == selectedPos ? 0xFF0D2233 : 0xFF0A0A0A);
            if (ch.logo != null && !ch.logo.isEmpty()) {
                Glide.with(h.itemView.getContext()).load(ch.logo)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(h.logo);
            } else {
                h.logo.setImageResource(R.drawable.ic_channel_placeholder);
            }
        }

        @Override public int getItemCount() { return channels.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView logo; TextView name;
            VH(View v) {
                super(v);
                logo = v.findViewById(R.id.epgChLogo);
                name = v.findViewById(R.id.epgChName);
            }
        }
    }

    // ── EPG row adapter ────────────────────────────────────────────────────
    static class EpgRowAdapter extends RecyclerView.Adapter<EpgRowAdapter.VH> {

        interface Listener {
            void onRowFocused(int row, Channel ch, EpgProgram prog);
            void onProgramSelected(Channel ch, EpgProgram prog);
            void onScrollXChanged(int scrollX);
        }

        private final Context ctx;
        private final List<Channel> channels;
        private final Map<String, List<EpgProgram>> epgData;
        private final long windowStartMs, windowEndMs;
        private final Listener listener;
        private int focusedRow = 0;
        private int sharedScrollX = 0;

        // Keep track of all scroll views so we can sync them
        private final List<HorizontalScrollView> scrollViews = new ArrayList<>();

        EpgRowAdapter(Context ctx, List<Channel> channels,
                      Map<String, List<EpgProgram>> epgData,
                      long windowStartMs, long windowEndMs, Listener listener) {
            this.ctx = ctx;
            this.channels = channels;
            this.epgData = epgData;
            this.windowStartMs = windowStartMs;
            this.windowEndMs = windowEndMs;
            this.listener = listener;
        }

        void setFocusedRow(int row) {
            int old = focusedRow;
            focusedRow = row;
            notifyItemChanged(old);
            notifyItemChanged(row);
        }

        void setSharedScrollX(int x) {
            sharedScrollX = x;
            for (HorizontalScrollView sv : scrollViews) {
                if (sv.getScrollX() != x) sv.scrollTo(x, 0);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_epg_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Channel ch = channels.get(pos);
            String key = ch.tvgId != null && !ch.tvgId.isEmpty() ? ch.tvgId : ch.id;
            List<EpgProgram> progs = epgData.get(key);

            long now = System.currentTimeMillis();
            // Find current program index for initial focus
            int initFocusCol = 0;
            if (progs != null) {
                for (int i = 0; i < progs.size(); i++) {
                    if (progs.get(i).startMs <= now && progs.get(i).stopMs > now) {
                        initFocusCol = i; break;
                    }
                }
            }

            boolean hasFocus = pos == focusedRow;
            h.gridView.setData(ch, progs, windowStartMs, windowEndMs);
            h.gridView.setFocusCol(hasFocus ? initFocusCol : -1, hasFocus);
            h.gridView.setListener(new EpgGridView.CellListener() {
                @Override
                public void onCellFocused(Channel c, EpgProgram p, int idx) {
                    if (listener != null) listener.onRowFocused(h.getAdapterPosition(), c, p);
                    // Auto-scroll horizontal to keep focused cell visible
                    EpgProgram prog = p;
                    if (prog != null) {
                        float density = ctx.getResources().getDisplayMetrics().density;
                        float cellX = (prog.startMs - windowStartMs) / 60000f
                            * EpgGridView.PX_PER_MIN * density;
                        int newX = Math.max(0, (int) cellX - 40);
                        if (listener != null) listener.onScrollXChanged(newX);
                    }
                }
                @Override
                public void onCellSelected(Channel c, EpgProgram p) {
                    if (listener != null) listener.onProgramSelected(c, p);
                }
            });

            // Track focus changes
            h.gridView.setOnFocusChangeListener((v, gained) -> {
                if (gained) {
                    int row = h.getAdapterPosition();
                    if (row != focusedRow) {
                        int old = focusedRow;
                        focusedRow = row;
                        notifyItemChanged(old);
                        notifyItemChanged(row);
                    }
                    if (listener != null) {
                        List<EpgProgram> rowProgs = epgData.get(
                            ch.tvgId != null && !ch.tvgId.isEmpty() ? ch.tvgId : ch.id);
                        int col = h.gridView.getFocusCol();
                        EpgProgram prog = (rowProgs != null && col >= 0 && col < rowProgs.size())
                            ? rowProgs.get(col) : null;
                        listener.onRowFocused(row, ch, prog);
                    }
                }
            });

            // Sync scroll position
            h.scrollView.scrollTo(sharedScrollX, 0);

            // Register for sync updates
            if (!scrollViews.contains(h.scrollView)) scrollViews.add(h.scrollView);

            // Listen to manual scroll and broadcast to others
            h.scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) -> {
                if (listener != null) listener.onScrollXChanged(scrollX);
            });
        }

        @Override
        public void onViewRecycled(@NonNull VH h) {
            scrollViews.remove(h.scrollView);
            super.onViewRecycled(h);
        }

        @Override public int getItemCount() { return channels.size(); }

        static class VH extends RecyclerView.ViewHolder {
            HorizontalScrollView scrollView;
            EpgGridView gridView;
            VH(View v) {
                super(v);
                scrollView = v.findViewById(R.id.epgRowScroll);
                gridView   = v.findViewById(R.id.epgRowView);
            }
        }
    }
}
