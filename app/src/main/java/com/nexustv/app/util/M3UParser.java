package com.nexustv.app.util;

import com.nexustv.app.data.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    private static final Pattern LOGO_PATTERN  = Pattern.compile("tvg-logo=\"([^\"]+)\"",    Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PATTERN    = Pattern.compile("tvg-id=\"([^\"]+)\"",      Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPG_PATTERN   = Pattern.compile("(?:url-tvg|x-tvg-url)=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public static String extractEpgUrl(String content) {
        if (content == null || content.isEmpty()) return "";
        for (String line : content.split("\n")) {
            if (line.trim().startsWith("#EXTM3U")) {
                Matcher m = EPG_PATTERN.matcher(line);
                if (m.find()) return m.group(1).trim();
            }
        }
        return "";
    }

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;
        String[] lines = content.split("\n");
        Channel pending = null;
        int index = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) {
                pending = parseExtinf(line, index++);
            } else if (!line.isEmpty() && !line.startsWith("#") && pending != null) {
                if (isValidUrl(line)) {
                    pending.url = line;
                    channels.add(pending);
                }
                pending = null;
            }
        }
        return channels;
    }

    private static Channel parseExtinf(String line, int index) {
        Channel ch = new Channel();
        ch.group = ""; ch.logo = ""; ch.tvgId = "";

        Matcher m;
        m = LOGO_PATTERN.matcher(line);  if (m.find()) ch.logo  = m.group(1).trim();
        m = ID_PATTERN.matcher(line);    if (m.find()) ch.tvgId = m.group(1).trim();
        m = GROUP_PATTERN.matcher(line); if (m.find()) ch.group = m.group(1).trim();

        // Channel name is everything after the LAST comma on the line
        int lastComma = line.lastIndexOf(',');
        ch.name = (lastComma >= 0 && lastComma < line.length() - 1)
            ? line.substring(lastComma + 1).trim()
            : "Unknown";

        // Use tvg-id as stable ID; fall back to index
        ch.id = !ch.tvgId.isEmpty() ? ch.tvgId : "ch-" + index;

        return ch;
    }

    private static boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ||
               url.startsWith("rtmp://") || url.startsWith("rtsp://");
    }
}
