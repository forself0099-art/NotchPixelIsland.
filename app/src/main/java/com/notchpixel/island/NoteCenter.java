package com.notchpixel.island;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Island se hat chuki notifications ka ghar (widget): app ke hisaab se group. */
final class NoteCenter {
    interface Listener { void onChanged(); }

    /** Sirf bade thumbnails (memory) in naye items ke baad free hote hain; notifications ki ginti par koi limit nahi. */
    private static final int KEEP_PICS = 40;
    private static final List<NoteInfo> items = new ArrayList<>();
    /** Jin apps ke group alag alag (khule hue) hain. */
    static final Set<String> open = new HashSet<>();
    static Listener listener;

    private NoteCenter() { }

    static void add(NoteInfo n) {
        remove0(n.key);
        items.add(0, n);
        for (int i = KEEP_PICS; i < items.size(); i++) items.get(i).picture = null;
        changed();
    }

    static void remove(String key) { if (remove0(key)) changed(); }

    private static boolean remove0(String key) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).key.equals(key)) { items.remove(i); return true; }
        return false;
    }

    static void clear() {
        items.clear();
        open.clear();
        changed();
    }

    static boolean isEmpty() { return items.isEmpty(); }

    /** App ke hisaab se group, sabse nayi notification wala group pehle. */
    static List<List<NoteInfo>> groups() {
        Map<String, List<NoteInfo>> m = new LinkedHashMap<>();
        for (NoteInfo n : items) {
            List<NoteInfo> g = m.get(n.pkg);
            if (g == null) { g = new ArrayList<>(); m.put(n.pkg, g); }
            g.add(n);
        }
        return new ArrayList<>(m.values());
    }

    private static void changed() { if (listener != null) listener.onChanged(); }
}
