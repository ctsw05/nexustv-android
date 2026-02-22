package com.nexustv.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.*;

public class AppStorage {
    private static final String PREFS_NAME    = "nexustv_prefs";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_ACTIVE    = "active_playlist_id";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY   = "watch_history";
    private static final String KEY_HIDDEN    = "hidden_channels";

    private final SharedPreferences prefs;
    private final Gson gson;

    public AppStorage(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson  = new Gson();
    }

    // ── Playlists ──────────────────────────────────────────────────────────
    public List<Playlist> getPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Playlist>>(){}.getType();
        List<Playlist> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void savePlaylists(List<Playlist> playlists) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    public void addPlaylist(Playlist p) {
        List<Playlist> list = getPlaylists();
        list.add(p);
        savePlaylists(list);
        if (list.size() == 1) setActivePlaylistId(p.id);
    }

    public void deletePlaylist(String id) {
        List<Playlist> list = getPlaylists();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) { list.remove(i); break; }
        }
        savePlaylists(list);
        if (id.equals(getActivePlaylistId())) {
            setActivePlaylistId(list.isEmpty() ? null : list.get(0).id);
        }
    }

    public Playlist getActivePlaylist() {
        String activeId = getActivePlaylistId();
        if (activeId == null) return null;
        for (Playlist p : getPlaylists()) {
            if (p.id.equals(activeId)) return p;
        }
        return null;
    }

    public String getActivePlaylistId() {
        return prefs.getString(KEY_ACTIVE, null);
    }

    public void setActivePlaylistId(String id) {
        if (id == null) prefs.edit().remove(KEY_ACTIVE).apply();
        else prefs.edit().putString(KEY_ACTIVE, id).apply();
    }

    public void updatePlaylistChannels(String playlistId, List<Channel> channels) {
        List<Playlist> list = getPlaylists();
        for (Playlist p : list) {
            if (p.id.equals(playlistId)) { p.channels = channels; break; }
        }
        savePlaylists(list);
    }

    // ── Favorites ──────────────────────────────────────────────────────────
    public Set<String> getFavorites() {
        String json = prefs.getString(KEY_FAVORITES, null);
        if (json == null) return new HashSet<>();
        Type type = new TypeToken<Set<String>>(){}.getType();
        Set<String> set = gson.fromJson(json, type);
        return set != null ? set : new HashSet<>();
    }

    public void toggleFavorite(String channelId) {
        Set<String> favs = getFavorites();
        if (favs.contains(channelId)) favs.remove(channelId);
        else favs.add(channelId);
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(favs)).apply();
    }

    public boolean isFavorite(String channelId) {
        return getFavorites().contains(channelId);
    }

    // ── Hidden Channels ────────────────────────────────────────────────────
    public Set<String> getHiddenChannels() {
        String json = prefs.getString(KEY_HIDDEN, null);
        if (json == null) return new HashSet<>();
        Type type = new TypeToken<Set<String>>(){}.getType();
        Set<String> set = gson.fromJson(json, type);
        return set != null ? set : new HashSet<>();
    }

    public void hideChannel(String channelId) {
        Set<String> hidden = getHiddenChannels();
        hidden.add(channelId);
        prefs.edit().putString(KEY_HIDDEN, gson.toJson(hidden)).apply();
    }

    public void unhideChannel(String channelId) {
        Set<String> hidden = getHiddenChannels();
        hidden.remove(channelId);
        prefs.edit().putString(KEY_HIDDEN, gson.toJson(hidden)).apply();
    }

    public void unhideAllChannels() {
        prefs.edit().remove(KEY_HIDDEN).apply();
    }

    public boolean isHidden(String channelId) {
        return getHiddenChannels().contains(channelId);
    }

    // ── Watch history ──────────────────────────────────────────────────────
    public List<String> getHistory() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>(){}.getType();
        List<String> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void addToHistory(String channelId) {
        List<String> history = getHistory();
        history.remove(channelId);
        history.add(0, channelId);
        if (history.size() > 50) history = history.subList(0, 50);
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
    }
}
