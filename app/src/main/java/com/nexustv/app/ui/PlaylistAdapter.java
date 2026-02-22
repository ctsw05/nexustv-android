package com.nexustv.app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nexustv.app.R;
import com.nexustv.app.data.Playlist;
import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

    public interface Listener {
        void onPlaylistClicked(Playlist p);
        void onDeleteClicked(Playlist p);
    }

    private List<Playlist> playlists = new ArrayList<>();
    private String activeId;
    private final Listener listener;
    private final Context ctx;

    public PlaylistAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<Playlist> list, String activeId) {
        this.playlists = list != null ? list : new ArrayList<>();
        this.activeId = activeId;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_playlist, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Playlist p = playlists.get(position);
        h.name.setText(p.name);
        int count = p.channels != null ? p.channels.size() : 0;

        // Show file vs URL indicator
        String sourceLabel = p.url.startsWith("content://") || p.url.startsWith("file://")
            ? "📂 Local file" : p.url;
        h.meta.setText(count + " channels  •  " + sourceLabel);

        boolean isActive = p.id.equals(activeId);
        h.activeLabel.setVisibility(isActive ? View.VISIBLE : View.GONE);

        // Short press → set active
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPlaylistClicked(p);
        });

        // Long press → confirm delete
        h.itemView.setLongClickable(true);
        h.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(ctx)
                .setTitle("Delete Playlist")
                .setMessage("Remove \"" + p.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (listener != null) listener.onDeleteClicked(p);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });
    }

    @Override public int getItemCount() { return playlists.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, meta, activeLabel;
        VH(View v) {
            super(v);
            name        = v.findViewById(R.id.playlistName);
            meta        = v.findViewById(R.id.playlistMeta);
            activeLabel = v.findViewById(R.id.activeLabel);
        }
    }
}
