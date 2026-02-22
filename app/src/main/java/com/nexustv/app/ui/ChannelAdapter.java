package com.nexustv.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.nexustv.app.R;
import com.nexustv.app.data.Channel;
import com.nexustv.app.data.EpgProgram;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface Listener {
        void onChannelClicked(Channel ch, int position);
        void onChannelHighlighted(Channel ch, int position); // focus moved to this channel
        void onFavoriteToggled(Channel ch, int position);
        void onChannelLongPress(Channel ch, int position);
    }

    private final Context ctx;
    private List<Channel> channels = new ArrayList<>();
    private Set<String> favorites;
    private Map<String, List<EpgProgram>> epgData;
    private String playingId = null;
    private Listener listener;

    private static final RequestOptions LOGO_OPTIONS = new RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.ic_channel_placeholder)
        .error(R.drawable.ic_channel_placeholder)
        .override(80, 80);

    public ChannelAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setChannels(List<Channel> list, Set<String> favs, Map<String, List<EpgProgram>> epg) {
        channels = list != null ? list : new ArrayList<>();
        favorites = favs;
        epgData = epg;
        notifyDataSetChanged();
    }

    public void setPlayingId(String id) {
        playingId = id;
        notifyDataSetChanged();
    }

    public void updateEpg(Map<String, List<EpgProgram>> epg) {
        epgData = epg;
        notifyDataSetChanged();
    }

    @Override public long getItemId(int pos) { return channels.get(pos).id.hashCode(); }
    @Override public int  getItemCount()     { return channels.size(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_channel, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Channel ch = channels.get(pos);

        h.name.setText(ch.name);
        h.group.setText(ch.group != null && !ch.group.isEmpty() ? ch.group : "");

        if (ch.logo != null && !ch.logo.isEmpty()) {
            Glide.with(ctx).load(ch.logo).apply(LOGO_OPTIONS).into(h.logo);
        } else {
            h.logo.setImageResource(R.drawable.ic_channel_placeholder);
        }

        EpgProgram current = getCurrentProgram(ch.tvgId != null && !ch.tvgId.isEmpty() ? ch.tvgId : ch.id);
        if (current != null) {
            h.nowPlaying.setText(current.getDisplayTitle());
            h.nowPlaying.setVisibility(View.VISIBLE);
        } else {
            h.nowPlaying.setVisibility(View.GONE);
        }

        boolean playing = ch.id.equals(playingId);
        h.playingDot.setVisibility(playing ? View.VISIBLE : View.GONE);
        h.itemView.setSelected(playing);

        boolean fav = favorites != null && favorites.contains(ch.id);
        h.favStar.setText(fav ? "♥" : "♡");
        h.favStar.setTextColor(ctx.getColor(fav ? R.color.accent : R.color.text_muted));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChannelClicked(ch, h.getAdapterPosition());
        });

        // When focus lands on this item, notify so EPG panel can update
        h.itemView.setOnFocusChangeListener((v, gained) -> {
            if (gained && listener != null) {
                listener.onChannelHighlighted(ch, h.getAdapterPosition());
            }
        });

        h.itemView.setLongClickable(true);
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onChannelLongPress(ch, h.getAdapterPosition());
            return true;
        });

        h.favStar.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteToggled(ch, h.getAdapterPosition());
        });
    }

    private EpgProgram getCurrentProgram(String tvgId) {
        if (epgData == null || tvgId == null || tvgId.isEmpty()) return null;
        List<EpgProgram> progs = epgData.get(tvgId);
        if (progs == null) return null;
        long now = System.currentTimeMillis();
        for (EpgProgram p : progs) {
            if (p.startMs <= now && p.stopMs > now) return p;
        }
        return null;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView logo;
        TextView name, group, nowPlaying, favStar;
        View playingDot;
        VH(View v) {
            super(v);
            logo       = v.findViewById(R.id.channelLogo);
            name       = v.findViewById(R.id.channelName);
            group      = v.findViewById(R.id.channelGroup);
            nowPlaying = v.findViewById(R.id.channelNowPlaying);
            favStar    = v.findViewById(R.id.favStar);
            playingDot = v.findViewById(R.id.playingDot);
        }
    }
}
