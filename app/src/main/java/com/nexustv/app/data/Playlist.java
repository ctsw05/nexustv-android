package com.nexustv.app.data;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    public String id;
    public String name;
    public String url;
    public String epgUrl;   // XMLTV EPG URL (optional)
    public long addedAt;
    public List<Channel> channels;

    public Playlist() {
        channels = new ArrayList<>();
    }

    public Playlist(String id, String name, String url, String epgUrl) {
        this.id      = id;
        this.name    = name;
        this.url     = url;
        this.epgUrl  = epgUrl != null ? epgUrl : "";
        this.addedAt = System.currentTimeMillis();
        this.channels = new ArrayList<>();
    }
}
