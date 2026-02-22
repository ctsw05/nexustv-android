package com.nexustv.app.data;

public class Channel {
    public String id;
    public String name;
    public String group;
    public String logo;
    public String url;
    public String tvgId;

    public Channel() {}

    public Channel(String id, String name, String group, String logo, String url, String tvgId) {
        this.id    = id;
        this.name  = name;
        this.group = group;
        this.logo  = logo;
        this.url   = url;
        this.tvgId = tvgId;
    }
}
