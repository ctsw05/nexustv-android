package com.nexustv.app.util;

import com.nexustv.app.data.EpgProgram;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class EpgParser {

    public static Map<String, List<EpgProgram>> parse(String xml) {
        Map<String, List<EpgProgram>> result = new HashMap<>();
        if (xml == null || xml.isEmpty()) return result;
        try {
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            f.setNamespaceAware(false);
            XmlPullParser p = f.newPullParser();
            p.setInput(new StringReader(xml));
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "programme".equals(p.getName())) {
                    EpgProgram prog = parseProg(p);
                    if (prog != null && prog.channelId != null && !prog.channelId.isEmpty()) {
                        result.computeIfAbsent(prog.channelId, k -> new ArrayList<>()).add(prog);
                    }
                }
                event = p.next();
            }
        } catch (Exception ignored) {}
        for (List<EpgProgram> l : result.values())
            l.sort(Comparator.comparingLong(pr -> pr.startMs));
        return result;
    }

    private static EpgProgram parseProg(XmlPullParser p) throws Exception {
        EpgProgram prog = new EpgProgram();
        prog.channelId = p.getAttributeValue(null, "channel");
        prog.startMs   = parseDate(p.getAttributeValue(null, "start"));
        prog.stopMs    = parseDate(p.getAttributeValue(null, "stop"));
        if (prog.startMs == 0 || prog.stopMs == 0) return null;
        prog.title = ""; prog.subtitle = ""; prog.description = "";

        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) {
                depth++;
                String tag = p.getName();
                if ("title".equals(tag))     { prog.title       = readText(p); depth--; }
                else if ("sub-title".equals(tag)) { prog.subtitle = readText(p); depth--; }
                else if ("desc".equals(tag)) { prog.description  = readText(p); depth--; }
            } else if (ev == XmlPullParser.END_TAG) {
                depth--;
            } else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
        return prog;
    }

    private static String readText(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ev = p.next();
        while (ev == XmlPullParser.TEXT) { sb.append(p.getText()); ev = p.next(); }
        return sb.toString().trim();
    }

    private static long parseDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        for (String fmt : new String[]{"yyyyMMddHHmmss Z","yyyyMMddHHmmss"}) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
