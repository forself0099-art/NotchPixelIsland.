package com.notchpixel.island;

import android.app.PendingIntent;

/** Ek chalti ya aati call ki jaankari (notification se). */
final class CallInfo {
    enum State { INCOMING, ONGOING }

    /** Notification ka apna button (Answer / Decline / Hang up). */
    static final class Act {
        final String title;
        final PendingIntent pi;
        Act(String title, PendingIntent pi) { this.title = title; this.pi = pi; }
    }

    String key, pkg, appName, caller, subtitle;
    State state = State.INCOMING;
    long startMs;          // call shuru hone ka waqt (timer iss se)
    int color;             // app ka rang
    Act answer, decline, hangup;
    boolean demo;          // MainActivity ki "nakli call" (test ke liye)

    static boolean send(Act a) {
        if (a == null || a.pi == null) return false;
        try { a.pi.send(); return true; } catch (Exception e) { return false; }
    }
}
