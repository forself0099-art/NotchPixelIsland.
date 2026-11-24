package com.notchpixel.island;

import java.util.LinkedHashMap;
import java.util.Map;

/** Abhi kaun si calls chal rahi hain. CallListener bharta hai, IslandView sunta hai. */
final class CallState {
    interface Listener {
        /** Sabse nayi call (null = koi call nahi). */
        void onCall(CallInfo c);
        /** Chalti call khatam hui, durMs = kitni der chali. */
        void onEnded(CallInfo last, long durMs);
    }

    private static final Map<String, CallInfo> calls = new LinkedHashMap<>();
    private static Listener listener;

    private CallState() { }

    static void setListener(Listener l) {
        listener = l;
        if (l != null) l.onCall(current());
    }

    static CallInfo current() {
        CallInfo last = null;
        for (CallInfo c : calls.values()) last = c;
        return last;
    }

    static void post(CallInfo c) {
        calls.remove(c.key);          // nayi update sabse aakhir mein
        calls.put(c.key, c);
        if (listener != null) listener.onCall(current());
    }

    static void remove(String key) {
        CallInfo r = calls.remove(key);
        if (r == null || listener == null) return;
        if (r.state == CallInfo.State.ONGOING)
            listener.onEnded(r, Math.max(0, System.currentTimeMillis() - r.startMs));
        listener.onCall(current());
    }

    static void clear() {
        calls.clear();
        if (listener != null) listener.onCall(null);
    }
}
