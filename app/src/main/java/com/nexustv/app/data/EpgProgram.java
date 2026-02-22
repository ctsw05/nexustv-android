package com.nexustv.app.data;

public class EpgProgram {
    public String channelId;
    public String title;
    public String subtitle;
    public String description;
    public long   startMs;
    public long   stopMs;

    public EpgProgram() {}

    public float getProgress() {
        long now = System.currentTimeMillis();
        if (now < startMs) return 0f;
        if (now > stopMs)  return 1f;
        return (float)(now - startMs) / (stopMs - startMs);
    }

    public String getTimeRange() {
        return formatTime(startMs) + " - " + formatTime(stopMs);
    }

    public static String formatTime(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        int h = c.get(java.util.Calendar.HOUR_OF_DAY);
        int m = c.get(java.util.Calendar.MINUTE);
        String ap = h >= 12 ? "PM" : "AM";
        if (h == 0) h = 12; else if (h > 12) h -= 12;
        return h + ":" + String.format("%02d", m) + " " + ap;
    }

    /** Full display title: "Title: Subtitle" if subtitle present */
    public String getDisplayTitle() {
        if (subtitle != null && !subtitle.isEmpty()) return title + ": " + subtitle;
        return title;
    }
}
