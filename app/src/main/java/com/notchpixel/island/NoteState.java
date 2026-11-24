package com.notchpixel.island;

/** CallListener nayi notifications yahan bhejta hai, NoteIsland sunta hai. */
final class NoteState {
    interface Listener {
        void onNote(NoteInfo n);
        void onRemoved(String key);
    }

    private static Listener listener;

    private NoteState() { }

    static void setListener(Listener l) { listener = l; }
    static void post(NoteInfo n) { if (listener != null) listener.onNote(n); }
    static void remove(String key) { if (listener != null) listener.onRemoved(key); }
}
