package com.notchpixel.island;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Notification access se VoIP / messaging calls pakadta hai (WhatsApp, Instagram, Skype...).
 * Sirf call wali notifications dekhta hai, baaki ko ignore. Koi INTERNET permission nahi.
 */
public class CallListener extends NotificationListenerService {

    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Map<String, Integer> seenHash = new HashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        firstSeen.remove(sbn.getKey());
        seenHash.remove(sbn.getKey());
        CallState.remove(sbn.getKey());
        NoteState.remove(sbn.getKey());
        NoteCenter.remove(sbn.getKey());
    }

    @Override
    public void onListenerDisconnected() {
        firstSeen.clear();
        CallState.clear();
        NoteCenter.clear();
    }

    private void handle(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName())) return;
        Bundle ex = n.extras;
        int callType = ex.getInt("android.callType", 0);       // CallStyle: 1 aati, 2 chalti
        if (!Notification.CATEGORY_CALL.equals(n.category) && callType == 0) {
            handleNote(sbn);
            return;
        }

        boolean chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        CallInfo c = new CallInfo();
        c.key = sbn.getKey();
        c.pkg = pkg;
        c.appName = appName(pkg);
        c.color = appColor(pkg, c.appName);
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
        c.caller = title == null ? c.appName : title.toString();
        c.subtitle = text == null ? "" : text.toString();
        c.state = callType == 2 || (callType == 0 && chrono)
                ? CallInfo.State.ONGOING : CallInfo.State.INCOMING;

        // timer: notification ke chronometer se, warna jab pehli baar dikhi tab se
        Long seen = firstSeen.get(c.key);
        if (seen == null) { seen = System.currentTimeMillis(); firstSeen.put(c.key, seen); }
        c.startMs = chrono && n.when > 0 ? n.when : seen;

        classify(c, n.actions);
        CallState.post(c);
    }

    // ---------------- aam notifications (messages, YouTube, mail...) ----------------
    private static final java.util.Set<String> MSG = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thunderdog.challegram", "com.facebook.orca", "org.thoughtcrime.securesms",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.discord", "com.viber.voip", "com.slack", "com.microsoft.teams", "im.vector.app",
            "com.skype.raider", "com.truecaller"));
    private static final java.util.Set<String> VID = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.google.android.youtube", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.netflix.mediaclient", "tv.twitch.android.app", "com.google.android.apps.youtube.kids"));
    private static final java.util.Set<String> MAIL = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android", "com.google.android.gm.lite"));
    private static final java.util.Set<String> SOC = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.twitter.android",
            "com.snapchat.android", "com.linkedin.android", "com.reddit.frontpage",
            "com.pinterest", "com.threads.android", "com.instagram.barcelona"));

    private void handleNote(StatusBarNotification sbn) {
        if (!new Store(this).notesOn()) return;
        Notification n = sbn.getNotification();
        int f = n.flags;
        if ((f & Notification.FLAG_ONGOING_EVENT) != 0 || (f & Notification.FLAG_GROUP_SUMMARY) != 0
                || (f & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;
        Bundle ex = n.extras;
        String tmpl = ex.getString(Notification.EXTRA_TEMPLATE, "");
        if (tmpl.contains("MediaStyle") || Notification.CATEGORY_TRANSPORT.equals(n.category)
                || Notification.CATEGORY_SERVICE.equals(n.category)
                || Notification.CATEGORY_SYSTEM.equals(n.category)) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return;
        try {                               // khamosh (low importance) notifications chhodo
            RankingMap rm = getCurrentRanking();
            Ranking r = new Ranking();
            if (rm != null && rm.getRanking(sbn.getKey(), r)
                    && r.getImportance() < android.app.NotificationManager.IMPORTANCE_DEFAULT) return;
        } catch (Exception ignored) { }

        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
        CharSequence conv = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        NoteInfo ni = new NoteInfo();
        ni.key = sbn.getKey();
        ni.pkg = pkg;
        ni.appName = appName(pkg);
        ni.color = appColor(pkg, ni.appName);
        ni.title = title == null ? "" : title.toString();
        ni.text = text == null ? "" : text.toString();
        if (ni.title.isEmpty() && ni.text.isEmpty()) return;
        ni.content = n.contentIntent;

        // messaging style: aakhri message + kitne naye
        android.os.Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0) {
            ni.count = msgs.length;
            if (msgs[msgs.length - 1] instanceof Bundle) {
                Bundle m = (Bundle) msgs[msgs.length - 1];
                CharSequence mt = m.getCharSequence("text");
                if (mt != null) ni.text = mt.toString();
                if (conv != null && conv.length() > 0) {            // group: "Group: bhejne wala"
                    CharSequence sender = m.getCharSequence("sender");
                    if (sender != null && sender.length() > 0) ni.text = sender + ": " + ni.text;
                    ni.title = conv.toString();
                }
            }
        } else if (n.number > 1) {
            ni.count = n.number;
        }
        if (big != null && big.length() > ni.text.length() && !MSG.contains(pkg)) ni.sub = big.toString();
        else if (sub != null) ni.sub = sub.toString();
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (ni.sub.isEmpty() && lines != null && lines.length > 0) ni.sub = lines[lines.length - 1].toString();

        // kism
        int max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        boolean ind = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        String cat = n.category == null ? "" : n.category;
        if (max > 0 || ind || Notification.CATEGORY_PROGRESS.equals(cat)) {
            ni.kind = NoteInfo.Kind.PROGRESS;
            ni.progMax = max;
            ni.progress = ex.getInt(Notification.EXTRA_PROGRESS, 0);
            ni.indeterminate = ind || max <= 0;
        } else if (VID.contains(pkg)) ni.kind = NoteInfo.Kind.VIDEO;
        else if (MAIL.contains(pkg) || Notification.CATEGORY_EMAIL.equals(cat)) ni.kind = NoteInfo.Kind.MAIL;
        else if (MSG.contains(pkg) || Notification.CATEGORY_MESSAGE.equals(cat)) ni.kind = NoteInfo.Kind.MESSAGE;
        else if (SOC.contains(pkg) || Notification.CATEGORY_SOCIAL.equals(cat)) ni.kind = NoteInfo.Kind.SOCIAL;
        else ni.kind = NoteInfo.Kind.ALERT;

        // wahi notification dobara (app ne sirf update ki) to dobara na dikhao; progress ke siwa
        int hash = (ni.title + "\u0001" + ni.text + "\u0001" + ni.count).hashCode();
        Integer old = seenHash.get(ni.key);
        if (ni.kind != NoteInfo.Kind.PROGRESS && old != null && old == hash) return;
        seenHash.put(ni.key, hash);

        // tasveerein: contact photo, thumbnail, app ka asli logo
        try {
            android.graphics.drawable.Icon li = n.getLargeIcon();
            if (li != null) ni.avatar = toBitmap(li.loadDrawable(this), 120);
        } catch (Exception ignored) { }
        try {
            Object pic = ex.get(Notification.EXTRA_PICTURE);
            if (pic instanceof Bitmap) ni.picture = scale((Bitmap) pic, 240);
        } catch (Exception ignored) { }
        try { ni.appIcon = toBitmap(getPackageManager().getApplicationIcon(pkg), 96); } catch (Exception ignored) { }
        NoteCenter.add(ni);                         // widget mein hamesha jata hai
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;               // screen band: island card nahi
        NoteState.post(ni);
    }

    private static Bitmap toBitmap(Drawable d, int px) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(c);
        return b;
    }

    private static Bitmap scale(Bitmap b, int maxW) {
        if (b.getWidth() <= maxW) return b;
        int h = Math.max(1, b.getHeight() * maxW / b.getWidth());
        return Bitmap.createScaledBitmap(b, maxW, h, true);
    }

    /** Buttons ke naam se pehchano; na mile to position se (aati: Decline, Answer). */
    private void classify(CallInfo c, Notification.Action[] acts) {
        if (acts == null) return;
        java.util.List<CallInfo.Act> rest = new java.util.ArrayList<>();
        for (Notification.Action a : acts) {
            if (a == null || a.actionIntent == null) continue;
            String t = a.title == null ? "" : a.title.toString();
            String l = t.toLowerCase(Locale.ROOT);
            CallInfo.Act act = new CallInfo.Act(t, a.actionIntent);
            if (has(l, "answer", "accept", "pick", "jawab")) c.answer = act;
            else if (has(l, "decline", "reject", "dismiss", "ignore")) c.decline = act;
            else if (has(l, "hang", "end", "leave", "disconnect", "cut")) c.hangup = act;
            else rest.add(act);
        }
        if (c.state == CallInfo.State.ONGOING) {
            if (c.hangup == null && !rest.isEmpty()) c.hangup = rest.get(0);
        } else {
            if (c.decline == null && c.answer == null && rest.size() >= 2) {
                c.decline = rest.get(0);
                c.answer = rest.get(1);
            } else if (c.decline == null && !rest.isEmpty() && c.answer != null) {
                c.decline = rest.get(0);
            } else if (c.answer == null && !rest.isEmpty() && c.decline != null) {
                c.answer = rest.get(0);
            }
        }
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private String appName(String pkg) {
        switch (pkg) {
            case "com.whatsapp": case "com.whatsapp.w4b": return "WhatsApp";
            case "com.instagram.android": return "Instagram";
            case "com.skype.raider": case "com.skype.m2": return "Skype";
            case "com.google.android.youtube": return "YouTube";
            case "com.google.android.gm": return "Gmail";
            case "org.telegram.messenger": return "Telegram";
            case "com.google.android.apps.googlevoice": return "Google Voice";
        }
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private int appColor(String pkg, String name) {
        switch (pkg) {
            case "com.whatsapp": case "com.whatsapp.w4b": return android.graphics.Color.rgb(37, 211, 102);
            case "com.instagram.android": return android.graphics.Color.rgb(225, 48, 108);
            case "com.skype.raider": case "com.skype.m2": return android.graphics.Color.rgb(0, 175, 240);
            case "com.google.android.youtube": return android.graphics.Color.rgb(255, 48, 48);
            case "com.google.android.gm": return android.graphics.Color.rgb(234, 67, 53);
            case "org.telegram.messenger": case "org.telegram.messenger.web": return android.graphics.Color.rgb(42, 171, 238);
            case "com.facebook.orca": return android.graphics.Color.rgb(0, 132, 255);
            case "com.facebook.katana": return android.graphics.Color.rgb(24, 119, 242);
            case "com.twitter.android": return android.graphics.Color.rgb(29, 155, 240);
            case "com.snapchat.android": return android.graphics.Color.rgb(255, 221, 0);
            case "com.zhiliaoapp.musically": return android.graphics.Color.rgb(254, 44, 85);
            case "com.netflix.mediaclient": return android.graphics.Color.rgb(229, 9, 20);
            case "com.discord": return android.graphics.Color.rgb(88, 101, 242);
            case "com.microsoft.office.outlook": return android.graphics.Color.rgb(0, 120, 212);
            case "com.google.android.apps.googlevoice": return android.graphics.Color.rgb(66, 133, 244);
        }
        return Theme.tile(name);
    }
}
