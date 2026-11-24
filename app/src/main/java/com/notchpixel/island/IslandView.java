package com.notchpixel.island;

import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Island: band hone par chhoti pill (time + battery dot), tap par bada panel
 * (time, date, battery, Torch / Wi-Fi / Bluetooth / Close).
 * Sizes desktop NotchPixel ke STATES (idle / expanded) jaise hain.
 */
public class IslandView extends View {

    public interface Host {
        void onResize(int w, int h);
        void onStop();
    }

    // ---- sizes (dp) ----
    private static final float IDLE_W = 120, IDLE_H = 34, IDLE_R = 17;
    private static final float EXP_W = 340, EXP_R = 32;
    // window sirf transitions par badalti hai; shape canvas ke andar animate hota hai (smooth)
    private static final float STAGE_H = 216, TREE_H = 410;
    private static final float LV_W = 300, LV_H = 44, LV_WIN_H = 56;   // volume/brightness level island
    private static final long TREE_DELAY_MS = 2000, DWELL_MS = 450;   // hold ke baad itni der right slide na ho to tree
    // tab: 0 Websites, 1 Controls, 2 Apps. Har tab ki apni height (dp)
    private static final float[] TAB_H = {216, 104, 216, 216};      // tab bar hata diya, Controls mein sirf time/date/battery
    private static final float TAB_BAR_H = 0;
    private static final float QP_H = 256;       // Quick panel ki height (dp)
    private static final long HOLD_MS = 350;
    private static final String[] TAB_ICON = {"\uD83C\uDF10", "\uD83C\uDF9B", "\uD83D\uDCF1"};
    private static final float TOP_GAP = 8;
    private static final long AUTO_COLLAPSE_MS = 10000;

    private final Host host;
    private final float d;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    private float p = 0f;                 // 0 = idle, 1 = expanded
    private boolean expanded = false;
    private ValueAnimator anim;

    private final Store store;
    private List<Store.Site> sites = new ArrayList<>();
    private final RectF[] siteRect = new RectF[8];
    private List<Apps.Item> apps = new ArrayList<>();
    private final RectF[] appRect = new RectF[8];
    private float downX, downY, downRawX;

    // hold + right slide = Quick panel
    private boolean holding, qpWindow, qpOpen;
    private boolean holdWin;                  // hold shuru: window pehle hi bada (idle<->bada resize glitch nahi)
    private boolean qpExp;                    // Quick panel khule hue island se nikla (size smooth neeche)
    private float qeW, qeH;                   // us waqt ka island size (px)
    private boolean movedAway;                // hold se pehle ungli hil gayi: UP par panel na kholo
    private float curW, curH;                 // shape ka size (px)
    private int winW, winH;                   // window ka abhi ka size (px)
    private boolean lvWin, lvVol;             // level island: window / volume ya brightness
    private float lvF, lvA;                   // level (0..1) aur dikhne ka progress
    private ValueAnimator lvAnim;
    private final Runnable lvHide = () -> animLevel(0f, 260);
    private final android.graphics.Path lvPath = new android.graphics.Path();
    private boolean treeWindow;               // long-press ka tree
    private float tr = 0f;
    private int hoverK = -1;                  // tree mein ungli kis button par hai
    private float dwell = 0f;                 // us par kitni der ruki (0..1)
    private ValueAnimator dwellAnim;
    private boolean consumed;                 // gesture kaam kar chuka: baaki touch ignore
    private ValueAnimator treeAnim;
    private final RectF[] treeRect = new RectF[7];
    private static final int[] TREE_Q = {1, 5, 2, 3, 11, 10};   // left 3, right 3 -> Quick tile index
    private float qp = 0f, swell = 0f;           // qp: 0 band, 1 khula; swell: pill phoolna
    private ValueAnimator qpAnim, swellAnim;
    // Quick panel ke 12 tiles
    private static final String[] Q_NAME = {"Mobile data", "Wi-Fi", "Flashlight", "Mute",
            "Screenshot", "Bluetooth", "Scanner", "Airplane", "Lock", "Location",
            "Auto-rotate", "DND"};
    private static final String[] Q_ICON = {"\u21C5", "\uD83D\uDCF6", "\uD83D\uDD26", "\uD83D\uDD15",
            "\uD83D\uDCF7", "\uD83D\uDD35", "\uD83D\uDCC4", "\u2708", "\uD83D\uDD12",
            "\uD83D\uDCCD", "\uD83D\uDD04", "\uD83C\uDF19"};
    private static final long TILE_GAP_MS = 20, TILE_DUR_MS = 200;
    private final RectF[] qRect = new RectF[12];
    private final RectF volHit = new RectF();       // slider ka chhoone wala hissa
    private float volTrackL, volTrackR;
    private boolean volDrag;
    private int lastVolTick = -1;
    private final AudioManager audio;
    private final NotificationManager nm;
    private final RectF briHit = new RectF();
    private boolean briDrag;
    private int lastBriTick = -1;
    private float tilesT = 0f;                  // 0..1, tiles ek ek karke aate hain
    private ValueAnimator tilesAnim;

    private final Runnable holdRun = () -> {
        holding = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        holdWin = true;
        boolean rs = settleWindow();                // window pehle bada, phir pill phoolta hai
        if (expanded) return;                       // khule panel par: sirf right slide ka intezaar
        runAfterResize(rs, () -> { if (holding && !qpWindow) animSwell(1f); });
        h.postDelayed(this.treeRun, TREE_DELAY_MS);
    };
    private final Runnable treeRun = () -> { if (holding && !qpWindow) openTree(); };

    // tile press animation: dabne par 0.92, chhodne par halka pop
    private int pressKind = 0, pressIdx = -1;      // kind: 1 button, 2 site, 3 app
    private float pressS = 1f;
    private ValueAnimator pressAnim;
    private float torchFill = 0f;                   // torch on hone par rang andar se bharta hai
    private boolean lastTorch;
    private ValueAnimator torchAnim;
    private boolean longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        launch(new Intent(getContext(), this.tab == 2 ? AppsActivity.class : SitesActivity.class));
    };
    private int tab, prevTab;             // aakhri tab Store se aata hai
    private float tp = 1f;                // tab badalne ka progress 0..1
    private ValueAnimator tabAnim;
    private final RectF[] tabRect = {new RectF(), new RectF(), new RectF()};

    private final SystemState sys;
    // Screen band ho to 15 second wala tick aur call animation dono ruk jate hain (battery bachat)
    private final android.content.BroadcastReceiver screenRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) screenSleep(true);
            else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) screenSleep(false);
        }
    };
    private NoteWidget noteW;              // notifications widget (tab 3)
    private float ncH = NoteWidget.MIN_H;   // widget tab ki abhi ki height (dp), smooth
    private ValueAnimator ncAnim;
    private boolean ncDragged;
    private float ncLastY;
    private NoteIsland noteUi;             // aam notifications ka island
    private final CallIsland callUi;       // calls ka island (aati / chalti call)

    private final RectF[] btn = new RectF[5];
    private static final String[] ICON = {"\uD83D\uDD26", "\uD83D\uDCF6", "\uD83D\uDD35", "\u2708", "\u2715"};
    private static final String[] LABEL = {"Torch", "Wi-Fi", "Bluetooth", "Airplane", "Close"};

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            h.postDelayed(this, 15000);
        }
    };
    private final Runnable autoCollapse = () -> { if (treeWindow) closeTree(); else if (qpWindow) closeQuick(); else setExpanded(false); };

    public IslandView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        this.d = ctx.getResources().getDisplayMetrics().density;
        store = new Store(ctx);
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        tab = prevTab = store.getTab();
        sites = store.sites();
        apps = Apps.load(ctx, store.apps(), 8);
        for (int i = 0; i < qRect.length; i++) qRect[i] = new RectF();
        for (int i = 0; i < siteRect.length; i++) { siteRect[i] = new RectF(); appRect[i] = new RectF(); }
        bg.setColor(Color.BLACK);
        curW = IDLE_W * d;
        curH = IDLE_H * d;
        winW = Math.round(IDLE_W * d);
        winH = Math.round(IDLE_H * d);
        for (int i = 0; i < treeRect.length; i++) treeRect[i] = new RectF();
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < btn.length; i++) btn[i] = new RectF();

        sys = new SystemState(ctx, h, this::onSysChanged);
        lastTorch = sys.torchOn;
        torchFill = lastTorch ? 1f : 0f;
        callUi = new CallIsland(ctx, h, new CallIsland.Env() {
            @Override public void resize(int w, int hgt) { winW = w; winH = hgt; host.onResize(w, hgt); }
            @Override public void redraw() { invalidate(); }
            @Override public void haptic(int constant) { performHapticFeedback(constant); }
            @Override public void launch(Intent in) {
                try {
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(in);
                } catch (Exception ignored) { }
            }
            @Override public void onStart() { if (noteUi != null) noteUi.dismissNow(); forceIdle(); }
            @Override public void onGone() { applySize(); invalidate(); }
        });
        CallState.setListener(callUi);
        noteUi = new NoteIsland(ctx, h, new NoteIsland.Env() {
            @Override public void resize(int w, int hgt) { winW = w; winH = hgt; host.onResize(w, hgt); }
            @Override public void redraw() { invalidate(); }
            @Override public void haptic(int constant) { performHapticFeedback(constant); }
            @Override public void launch(Intent in) {
                try {
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(in);
                } catch (Exception ignored) { }
            }
            @Override public void onStart() { forceIdle(); }
            @Override public void onGone() { applySize(); invalidate(); }
            @Override public boolean busy() { return callUi.active() || levelBusy(); }
        });
        NoteState.setListener(noteUi);
        noteW = new NoteWidget(ctx, new NoteWidget.Env() {
            @Override public void redraw() { invalidate(); }
            @Override public void launch(Intent in) {
                try {
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(in);
                } catch (Exception ignored) { }
            }
            @Override public void haptic(int constant) { performHapticFeedback(constant); }
            @Override public void heightChanged(float dp) { animNc(dp); }
        });
        NoteCenter.listener = () -> noteW.refresh();
        ncH = noteW.targetH();
        h.post(tick);
        android.content.IntentFilter sf = new android.content.IntentFilter();
        sf.addAction(Intent.ACTION_SCREEN_OFF);
        sf.addAction(Intent.ACTION_SCREEN_ON);
        ctx.registerReceiver(screenRx, sf);
        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) screenSleep(true);
    }

    public void release() {
        h.removeCallbacksAndMessages(null);
        if (anim != null) anim.cancel();
        if (tabAnim != null) tabAnim.cancel();
        if (pressAnim != null) pressAnim.cancel();
        if (qpAnim != null) qpAnim.cancel();
        if (tilesAnim != null) tilesAnim.cancel();
        if (swellAnim != null) swellAnim.cancel();
        if (treeAnim != null) treeAnim.cancel();
        if (lvAnim != null) lvAnim.cancel();
        if (torchAnim != null) torchAnim.cancel();
        try { getContext().unregisterReceiver(screenRx); } catch (Exception ignored) { }
        callUi.release();
        noteUi.release();
        NoteCenter.listener = null;
        if (ncAnim != null) ncAnim.cancel();
        sys.release();
    }

    // ---- sizes for service ----
    public int idleW() { return Math.round(IDLE_W * d); }
    public int idleH() { return Math.round(IDLE_H * d); }
    public int topGap() { return Math.round(TOP_GAP * d); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---- expand / collapse ----
    private void setExpanded(boolean on) {
        if (expanded == on) return;
        if (on) noteW.refresh();
        expanded = on;
        h.removeCallbacks(autoCollapse);
        if (on) {
            sites = store.sites();
            apps = Apps.load(getContext(), store.apps(), 8);
            h.postDelayed(autoCollapse, AUTO_COLLAPSE_MS);
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(p, on ? 1f : 0f);
        anim.setDuration(on ? 260 : 220);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            p = (float) a.getAnimatedValue();
            applySize();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                // khulne ke aakhir mein halka tick
                if (on && !cancelled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        if (on) { settleWindow(); anim.setStartDelay(40); }   // window pehle bada, phir animation
        anim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (consumed) {
            int ac = e.getActionMasked();
            if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) consumed = false;
            return true;
        }
        if (callUi.active()) {
            e.offsetLocation(-(getWidth() - callUi.shapeW()) / 2f, 0);
            return callUi.touch(e);
        }
        if (noteUi.active()) {
            e.offsetLocation(-(getWidth() - noteUi.shapeW()) / 2f, 0);
            return noteUi.touch(e);
        }
        if (treeWindow && !holding) return treeTouch(e);
        if (qpWindow && !holding) return qpTouch(e);
        if (!qpWindow && !treeWindow) e.offsetLocation(-(getWidth() - curW) / 2f, 0);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
                setExpanded(false);
                return true;
            case MotionEvent.ACTION_DOWN:
                resetTimer();
                downX = e.getX();
                downY = e.getY();
                longFired = false;
                holding = false;
                ncDragged = false;
                ncLastY = e.getY();
                downRawX = e.getRawX();
                movedAway = false;
                pressDown(downX, downY);
                boolean tileLong = expanded && tp > 0.9f && p > 0.9f
                        && ((tab == 0 && siteAt(downX, downY) >= 0)
                        || (tab == 2 && downY < curH - (TAB_BAR_H + 4) * d));
                if (tileLong) h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                else if (!expanded || (p > 0.9f && tp > 0.9f)) h.postDelayed(holdRun, HOLD_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (holding) {
                    if (treeWindow) { hoverTree(e); return true; }
                    float dx = e.getRawX() - downRawX;
                    if (!qpWindow && dx > 2 * ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        h.removeCallbacks(treeRun);          // slide hua: tree nahi, Quick panel
                        h.removeCallbacks(autoCollapse);
                        qpExp = expanded;
                        if (expanded) { qeW = curW; qeH = curH; }   // yahin se smooth neeche badhega
                        qpWindow = true;
                        animSwell(0f);
                        qp = 0f;
                        settleWindow();
                        startTiles();
                    }
                    if (qpWindow) { qp = clamp01(dx / (EXP_W * d)); invalidate(); }
                    return true;
                }
                resetTimer();
                if (expanded && tab == 3 && p > 0.9f && tp > 0.9f) {      // widget: upar/neeche scroll
                    if (Math.abs(e.getY() - downY) > 2 * ViewConfiguration.get(getContext()).getScaledTouchSlop())
                        ncDragged = true;
                    if (ncDragged) noteW.scrollBy(ncLastY - e.getY(), curH);
                    ncLastY = e.getY();
                }
                float mv = (float) Math.hypot(e.getX() - downX, e.getY() - downY);
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (mv > 2 * slop) {                 // hold ke liye thori ungli hilna maaf
                    movedAway = true;
                    h.removeCallbacks(longPress);
                    h.removeCallbacks(holdRun);
                    pressUp();
                } else if (mv > slop) {
                    h.removeCallbacks(longPress);
                    pressUp();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(holdRun);
                if (holding) endHold();
                h.removeCallbacks(longPress);
                pressUp();
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(holdRun);
                if (holding) {
                    boolean slid = qpWindow;
                    endHold();
                    if (!(expanded && !slid)) return true;   // khule panel par bina slide ke hold = normal tap
                }
                h.removeCallbacks(longPress);
                pressUp();
                if (longFired) return true;
                if (!expanded) {
                    if (!movedAway) setExpanded(true);       // ungli hat kar chhodi to panel nahi
                } else if (p > 0.9f && tab == 3 && tp > 0.9f) {
                    if (!ncDragged && noteW.tap(e.getX(), e.getY(), curH) == 2) setExpanded(false);
                    resetTimer();
                } else if (p > 0.9f) {
                    int hitTab = -1;
                    for (int i = 0; i < tabRect.length; i++)
                        if (tabRect[i].contains(e.getX(), e.getY())) hitTab = i;
                    int hitSite = hitTab < 0 && tab == 0 && tp > 0.9f
                            ? siteAt(e.getX(), e.getY()) : -1;
                    int hitApp = hitTab < 0 && tab == 2 && tp > 0.9f ? appAt(e.getX(), e.getY()) : -1;
                    int hit = -1;
                    if (hitTab < 0 && tab == 1 && tp > 0.9f)
                        for (int i = 0; i < btn.length; i++)
                            if (btn[i].contains(e.getX(), e.getY())) hit = i;
                    if (hitTab >= 0) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        setTab(hitTab);
                    } else if (hitSite == sites.size()) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        launch(new Intent(getContext(), SitesActivity.class));
                    } else if (hitSite >= 0 && hitSite < sites.size()) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        launch(new Intent(Intent.ACTION_VIEW, Uri.parse(sites.get(hitSite).url)));
                    } else if (hitApp >= 0 && hitApp < apps.size()) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        Intent li = getContext().getPackageManager()
                                .getLaunchIntentForPackage(apps.get(hitApp).pkg);
                        if (li != null) launch(li);
                    } else if (hit >= 0) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        onButton(hit);
                    } else if (tab == 1) {
                        setExpanded(false);   // abhi: Controls par khali jagah = band
                    }
                    resetTimer();
                }
                return true;
        }
        return true;
    }

    /** Kis tile par ungli hai (site ya "+ Add"); -1 = koi nahi. */
    private int siteAt(float x, float y) {
        for (int i = 0; i < siteRect.length; i++)
            if (siteRect[i].contains(x, y)) return i;
        return -1;
    }

    private int appAt(float x, float y) {
        for (int i = 0; i < appRect.length; i++)
            if (appRect[i].contains(x, y)) return i;
        return -1;
    }

    private void onSysChanged() {
        if (sys != null && sys.torchOn != lastTorch) {
            lastTorch = sys.torchOn;
            if (torchAnim != null) torchAnim.cancel();
            torchAnim = ValueAnimator.ofFloat(torchFill, lastTorch ? 1f : 0f);
            torchAnim.setDuration(300);
            torchAnim.addUpdateListener(a -> { torchFill = (float) a.getAnimatedValue(); invalidate(); });
            torchAnim.start();
        }
        invalidate();
    }

    private void animPress(float to, long ms, boolean overshoot, boolean clearAtEnd) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressS, to);
        pressAnim.setDuration(ms);
        pressAnim.setInterpolator(overshoot ? new OvershootInterpolator(2.5f)
                : new DecelerateInterpolator());
        pressAnim.addUpdateListener(a -> { pressS = (float) a.getAnimatedValue(); invalidate(); });
        if (clearAtEnd) pressAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (pressS >= 0.999f) { pressKind = 0; pressIdx = -1; }
            }
        });
        pressAnim.start();
    }

    private void pressDown(float x, float y) {
        if (!expanded || p < 0.9f || tp < 0.9f) return;
        int k = 0, idx = -1;
        if (tab == 1) { for (int i = 0; i < btn.length; i++) if (btn[i].contains(x, y)) { k = 1; idx = i; } }
        else if (tab == 0) { idx = siteAt(x, y); if (idx >= 0) k = 2; }
        else { idx = appAt(x, y); if (idx >= 0) k = 3; }
        if (k == 0) return;
        pressKind = k;
        pressIdx = idx;
        animPress(0.92f, 120, false, false);
    }

    private void pressUp() {
        if (pressKind != 0) animPress(1f, 300, true, true);
    }

    private float press(int kind, int idx) {
        return pressKind == kind && pressIdx == idx ? pressS : 1f;
    }

    private void animSwell(float to) {
        if (swellAnim != null) swellAnim.cancel();
        swellAnim = ValueAnimator.ofFloat(swell, to);
        swellAnim.setDuration(120);
        swellAnim.addUpdateListener(a -> { swell = (float) a.getAnimatedValue(); applySize(); invalidate(); });
        swellAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled || to != 0f || holding || qpWindow || treeWindow) return;
                holdWin = false;                    // swell khatam hone ke BAAD hi window chhoti
                settleWindow();
                invalidate();
            }
        });
        swellAnim.start();
    }

    private void animQp(float to, long ms, Runnable end) {
        if (qpAnim != null) qpAnim.cancel();
        qpAnim = ValueAnimator.ofFloat(qp, to);
        qpAnim.setDuration(ms);
        qpAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        qpAnim.addUpdateListener(a -> { qp = (float) a.getAnimatedValue(); invalidate(); });
        qpAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled && end != null) end.run();
            }
        });
        qpAnim.start();
    }

    private void openQuick() {
        animQp(1f, 240, () -> {
            if (qpExp) {                            // panel poora: peeche ka khula island ab pill (dikhta nahi)
                qpExp = false;
                expanded = false;
                p = 0f;
                if (anim != null) anim.cancel();
            }
            qpOpen = true;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            h.removeCallbacks(autoCollapse);
            h.postDelayed(autoCollapse, AUTO_COLLAPSE_MS);
        });
    }

    private void closeQuick() {
        h.removeCallbacks(autoCollapse);
        qpOpen = false;
        animQp(0f, 220, () -> {
            qpWindow = false;
            qpExp = false;
            holdWin = false;
            tilesT = 0f;
            swell = 0f;
            applySize();
            if (expanded) resetTimer();
        });
    }

    /** Hold chhodna: kafi slide hua (>40%) to Quick panel tik jata hai, warna wapas. */
    private void endHold() {
        holding = false;
        h.removeCallbacks(treeRun);
        if (treeWindow) { cancelDwell(); closeTree(); return; }   // ungli uthi: normal
        if (qpWindow) { if (qp > 0.4f) openQuick(); else closeQuick(); }
        else if (expanded) { holdWin = false; settleWindow(); resetTimer(); }
        else animSwell(0f);
    }

    // ---- long-press tree: pill se stem, left/right shaakhein, 7 gol buttons ----
    private static float seg(float t, float a, float b) { return clamp01((t - a) / (b - a)); }

    private static float overshoot(float t) {
        float k = 1.70158f;
        t -= 1f;
        return 1f + (k + 1f) * t * t * t + k * t * t;
    }

    private boolean qOn(int i) {
        switch (i) {
            case 2: return torchFill > 0.5f;
            case 3: return audio != null && audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
            case 10: return rotateOn();
            case 11: return dndOn();
            default: return false;
        }
    }

    private int qColor(int i) {
        return i == 2 ? Theme.YELLOW : i == 3 ? Theme.TILE[3] : i == 10 ? Theme.TILE[0] : Theme.TILE[4];
    }

    private void openTree() {
        if (treeWindow || qpWindow || expanded || callUi.active() || noteUi.active()) return;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        treeWindow = true;
        holdWin = false;
        tr = 0f;
        animSwell(0f);                          // pill smooth wapas normal size
        boolean rs = settleWindow();            // window pehle bada
        runAfterResize(rs, () -> animTree(1f, 420, true));
        h.removeCallbacks(autoCollapse);
        h.postDelayed(autoCollapse, AUTO_COLLAPSE_MS);
    }

    private void animTree(float to, long ms, boolean opening) {
        if (treeAnim != null) treeAnim.cancel();
        treeAnim = ValueAnimator.ofFloat(tr, to);
        treeAnim.setDuration(ms);
        treeAnim.setInterpolator(new DecelerateInterpolator(1.2f));
        treeAnim.addUpdateListener(a -> { tr = (float) a.getAnimatedValue(); invalidate(); });
        treeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled && to == 0f) {
                    treeWindow = false;
                    settleWindow();
                    invalidate();
                }
            }
        });
        treeAnim.start();
    }

    private void closeTree() {
        h.removeCallbacks(autoCollapse);
        animTree(0f, 260, false);
    }

    private int treeAt(float x, float y) {
        for (int i = 0; i < treeRect.length; i++)
            if (treeRect[i].contains(x, y)) return i;
        return -1;
    }

    private void openFullQuick() {
        h.removeCallbacks(autoCollapse);
        if (treeAnim != null) treeAnim.cancel();
        treeWindow = false;
        tr = 0f;
        qpWindow = true;
        qp = 0f;
        boolean rs = settleWindow();
        runAfterResize(rs, () -> { startTiles(); openQuick(); });
    }

    private static int treeTab(int k) {
        return k == 0 ? 0 : k == 6 ? 1 : k == 3 ? 2 : k == 1 ? 3 : -1;
    }

    private boolean treeTouch(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_OUTSIDE) {
            closeTree();
        } else if (a == MotionEvent.ACTION_DOWN) {
            resetTimer();
            int k = tr > 0.9f ? treeAt(e.getX(), e.getY()) : -1;
            if (treeTab(k) >= 0) {
                pressKind = 5;
                pressIdx = k;
                animPress(0.92f, 120, false, false);
            }
        } else if (a == MotionEvent.ACTION_MOVE) {
            resetTimer();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            pressUp();
            if (a == MotionEvent.ACTION_UP && tr > 0.9f) {
                int k = treeAt(e.getX(), e.getY());
                if (k >= 0) {
                    int t = treeTab(k);   // Websites, Controls, Apps, Notifications
                    if (t >= 0) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        openTab(t);
                    }
                } else {
                    closeTree();
                }
            }
        }
        return true;
    }

    private static final String[] TREE_ICON = {"\uD83C\uDF10", "\uD83D\uDD14", "", "\uD83D\uDCF1", "", "", "\uD83C\uDF9B"};
    private static final String[] TREE_LABEL = {"Websites", "Notifications", "", "Apps", "", "", "Controls"};
    private android.graphics.DashPathEffect dash;

    // ---- volume / brightness: island khud failkar icon + bar + number dikhata hai ----
    private boolean levelBusy() {
        return expanded || qpWindow || treeWindow || holding || callUi.active() || noteUi.active() || p > 0.001f;
    }

    void levelShow(boolean vol, float f) {
        if (levelBusy()) return;
        lvVol = vol;
        lvF = f;
        h.removeCallbacks(lvHide);
        if (!lvWin) { lvWin = true; settleWindow(); }       // window pehle bada
        animLevel(1f, 200);
    }

    void levelUpdate(float f) { lvF = f; invalidate(); }

    void levelHideSoon() {
        h.removeCallbacks(lvHide);
        h.postDelayed(lvHide, 700);
    }

    private void animLevel(float to, long ms) {
        if (lvAnim != null) lvAnim.cancel();
        lvAnim = ValueAnimator.ofFloat(lvA, to);
        lvAnim.setDuration(ms);
        lvAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        if (to > 0f && lvA < 0.01f) lvAnim.setStartDelay(40);
        lvAnim.addUpdateListener(a -> { lvA = (float) a.getAnimatedValue(); applySize(); });
        lvAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled && to == 0f) { lvA = 0f; lvWin = false; settleWindow(); invalidate(); }
            }
        });
        lvAnim.start();
    }

    private void drawLevel(Canvas c, float w, float hgt, float a) {
        int al = (int) (255 * a);
        float cy = hgt / 2f, tl = 56 * d, tr = w - 58 * d, tw = 6 * d;
        drawLevelIcon(c, 28 * d, cy, al);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(60, 60, 66));
        fill.setAlpha(al);
        rf.set(tl, cy - tw / 2f, tr, cy + tw / 2f);
        c.drawRoundRect(rf, tw / 2f, tw / 2f, fill);
        fill.setColor(Color.rgb(255, 214, 102));
        fill.setAlpha(al);
        rf.set(tl, cy - tw / 2f, tl + (tr - tl) * Math.max(0.02f, lvF), cy + tw / 2f);
        c.drawRoundRect(rf, tw / 2f, tw / 2f, fill);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Color.rgb(88, 150, 240));
        txt.setAlpha(al);
        txt.setTextSize(14 * d);
        c.drawText(String.valueOf(Math.round(lvF * 100)), w - 28 * d, cy + 5 * d, txt);
    }

    private void drawLevelIcon(Canvas c, float cx, float cy, int al) {
        fill.setColor(Color.WHITE);
        fill.setAlpha(al);
        fill.setStrokeCap(Paint.Cap.ROUND);
        if (!lvVol) {                                       // sun
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(1.8f * d);
            c.drawCircle(cx, cy, 4.2f * d, fill);
            for (int i = 0; i < 8; i++) {
                double ang = Math.PI / 4 * i;
                float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                c.drawLine(cx + cs * 7.2f * d, cy + sn * 7.2f * d, cx + cs * 10f * d, cy + sn * 10f * d, fill);
            }
            fill.setStyle(Paint.Style.FILL);
            return;
        }
        fill.setStyle(Paint.Style.FILL);                    // speaker
        lvPath.reset();
        lvPath.moveTo(cx - 9 * d, cy - 3 * d);
        lvPath.lineTo(cx - 5 * d, cy - 3 * d);
        lvPath.lineTo(cx, cy - 7 * d);
        lvPath.lineTo(cx, cy + 7 * d);
        lvPath.lineTo(cx - 5 * d, cy + 3 * d);
        lvPath.lineTo(cx - 9 * d, cy + 3 * d);
        lvPath.close();
        c.drawPath(lvPath, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.8f * d);
        if (lvF <= 0.001f) {                                // mute: cross
            c.drawLine(cx + 4 * d, cy - 4 * d, cx + 11 * d, cy + 4 * d, fill);
            c.drawLine(cx + 4 * d, cy + 4 * d, cx + 11 * d, cy - 4 * d, fill);
        } else {
            int n = lvF < 0.34f ? 1 : lvF < 0.67f ? 2 : 3;
            for (int i = 1; i <= n; i++) {
                float rad = (3 + 3.2f * i) * d;
                rf.set(cx - rad, cy - rad, cx + rad, cy + rad);
                c.drawArc(rf, -45, 90, false, fill);
            }
        }
        fill.setStyle(Paint.Style.FILL);
    }

    private void cancelDwell() {
        if (dwellAnim != null) dwellAnim.cancel();
        hoverK = -1;
        dwell = 0f;
    }

    /** Tree khula hai aur ungli neeche hai: button par ruko to wo khul jata hai. */
    private void hoverTree(MotionEvent e) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        int k = tr > 0.9f ? treeAt(e.getRawX() - loc[0], e.getRawY() - loc[1]) : -1;
        if (treeTab(k) < 0) k = -1;
        if (k == hoverK) return;
        cancelDwell();
        hoverK = k;
        if (k >= 0) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dwellAnim = ValueAnimator.ofFloat(0f, 1f);
            dwellAnim.setDuration(DWELL_MS);
            dwellAnim.addUpdateListener(a -> { dwell = (float) a.getAnimatedValue(); invalidate(); });
            dwellAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (!cancelled) activateHover();
                }
            });
            dwellAnim.start();
        }
        invalidate();
    }

    private void activateHover() {
        int t = treeTab(hoverK);      // Websites, Controls, Apps, Notifications
        holding = false;
        consumed = true;                                   // ungli uthne tak baaki touch ignore
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        openTab(t);
    }

    /** Tree se tab kholna: tab bar ab sirf yahin hai. */
    private void openTab(int t) {
        h.removeCallbacks(autoCollapse);
        if (treeAnim != null) treeAnim.cancel();
        cancelDwell();
        treeWindow = false;
        tr = 0f;
        prevTab = tab = t;
        tp = 1f;
        store.setTab(t);
        setExpanded(true);
    }

    private void drawTree(Canvas c) {
        float cx = getWidth() / 2f;
        float sw = 1f + 0.08f * swell;
        float pw = IDLE_W * d * sw, ph = IDLE_H * d * sw, r = 26 * d;
        rf.set(cx - pw / 2f, 0, cx + pw / 2f, ph);
        c.drawRoundRect(rf, IDLE_R * d * sw, IDLE_R * d * sw, bg);
        c.save();
        c.translate(cx - pw / 2f, 0);
        drawIdle(c, pw, ph, 1f);
        c.restore();

        float[] rowY = {100 * d, 190 * d, 280 * d};
        float botY = 365 * d, lx = cx - 125 * d, rx = cx + 125 * d;
        float stemEnd = botY - r;
        if (dash == null) dash = new android.graphics.DashPathEffect(new float[]{6 * d, 5 * d}, 0);

        // stem + shaakhein
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(2 * d);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setColor(Theme.OUTLINE);
        fill.setAlpha(255);
        c.drawLine(cx, ph, cx, ph + (stemEnd - ph) * seg(tr, 0f, 0.45f), fill);
        for (int i = 0; i < 3; i++) {
            float sb = seg(tr, 0.2f + 0.08f * i, 0.55f + 0.08f * i);
            if (sb <= 0f) continue;
            c.drawLine(cx, rowY[i], cx - (cx - (lx + r)) * sb, rowY[i], fill);
            c.drawLine(cx, rowY[i], cx + ((rx - r) - cx) * sb, rowY[i], fill);
        }
        fill.setStyle(Paint.Style.FILL);

        for (int k = 0; k < 7; k++) {
            float ccx = k < 3 ? lx : k < 6 ? rx : cx;
            float ccy = k < 3 ? rowY[k] : k < 6 ? rowY[k - 3] : botY;
            treeRect[k].set(ccx - r - 6 * d, ccy - r - 6 * d, ccx + r + 6 * d, ccy + r + 6 * d);
            float sc = k < 6 ? seg(tr, 0.45f + 0.08f * (k % 3), 0.8f + 0.08f * (k % 3))
                    : seg(tr, 0.5f, 0.9f);
            if (sc <= 0.01f) continue;
            int al = (int) (255 * clamp01(sc * 1.5f));
            boolean filled = !TREE_ICON[k].isEmpty();
            float s = overshoot(sc) * (k == hoverK ? 1f + 0.12f * clamp01(dwell * 6f) : 1f);
            c.save();
            c.scale(s, s, ccx, ccy);
            if (filled) {
                fill.setColor(Theme.BTN);
                fill.setAlpha(al);
                c.drawCircle(ccx, ccy, r, fill);
                txt.setTextAlign(Paint.Align.CENTER);
                txt.setTypeface(android.graphics.Typeface.DEFAULT);
                txt.setColor(Color.WHITE);
                txt.setAlpha(al);
                txt.setTextSize(22 * d);
                c.drawText(TREE_ICON[k], ccx, ccy + 8 * d, txt);
                txt.setTextSize(10 * d);
                txt.setColor(Theme.TEXT_DIM);
                txt.setAlpha(al);
                c.drawText(TREE_LABEL[k], ccx, ccy + r + 14 * d, txt);
                if (k == hoverK && dwell > 0f) {          // ruko: ring bharti hai
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(3 * d);
                    fill.setColor(Color.WHITE);
                    fill.setAlpha(al);
                    rf.set(ccx - r - 5 * d, ccy - r - 5 * d, ccx + r + 5 * d, ccy + r + 5 * d);
                    c.drawArc(rf, -90, 360 * dwell, false, fill);
                    fill.setStyle(Paint.Style.FILL);
                }
            } else {          // khali gol: sirf dotted outline
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(2 * d);
                fill.setColor(Theme.OUTLINE);
                fill.setAlpha(al);
                fill.setPathEffect(dash);
                c.drawCircle(ccx, ccy, r - d, fill);
                fill.setPathEffect(null);
                fill.setStyle(Paint.Style.FILL);
            }
            c.restore();
        }
    }

    private void startTiles() {
        if (tilesAnim != null) tilesAnim.cancel();
        long total = 11 * TILE_GAP_MS + TILE_DUR_MS;
        tilesT = 0f;
        tilesAnim = ValueAnimator.ofFloat(0f, 1f);
        tilesAnim.setDuration(total);
        tilesAnim.addUpdateListener(a -> { tilesT = (float) a.getAnimatedValue(); invalidate(); });
        tilesAnim.start();
    }

    /** Tile i ka aane ka progress 0..1 (20 ms ke fasle se). */
    private float tileIn(int i) {
        float total = 11 * TILE_GAP_MS + TILE_DUR_MS;
        return clamp01((tilesT * total - i * TILE_GAP_MS) / TILE_DUR_MS);
    }

    private int qTileAt(float x, float y) {
        for (int i = 0; i < qRect.length; i++)
            if (qRect[i].contains(x, y)) return i;
        return -1;
    }

    /** Ring <-> Vibrate. Poora silent DND access maangta hai, isliye vibrate. */
    private void toggleVibrate() {
        if (audio == null) return;
        try {
            audio.setRingerMode(audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL);
        } catch (Exception ignored) { }
        invalidate();
    }

    private float volFrac() {
        if (audio == null) return 0f;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0f : audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
    }

    private void setVolumeFromX(float x) {
        if (audio == null) return;
        float f = clamp01((x - volTrackL) / (volTrackR - volTrackL));
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0); }
        catch (Exception ignored) { }
        int t = (int) (f * 10);                       // har 10% par tick
        if (t != lastVolTick) {
            if (lastVolTick != -1) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lastVolTick = t;
        }
        invalidate();
    }

    private float briFrac() {
        try {
            return Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
        } catch (Exception e) { return 0.5f; }
    }

    private void setBrightnessFromX(float x) {
        float f = clamp01((x - volTrackL) / (volTrackR - volTrackL));
        try {
            android.content.ContentResolver cr = getContext().getContentResolver();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(1, Math.round(f * 255)));
        } catch (Exception ignored) { }
        int t = (int) (f * 10);
        if (t != lastBriTick) {
            if (lastBriTick != -1) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lastBriTick = t;
        }
        invalidate();
    }

    private boolean rotateOn() {
        try {
            return Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (Exception e) { return false; }
    }

    private boolean dndOn() {
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return false;
        int f = nm.getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    /** Auto-rotate seedha on/off (Modify system settings permission). */
    private void toggleRotate() {
        if (!Settings.System.canWrite(getContext())) {
            launch(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName())));
            return;
        }
        try {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, rotateOn() ? 0 : 1);
        } catch (Exception ignored) { }
        invalidate();
    }

    /** DND seedha on/off (DND access ek baar). On = sirf priority (alarm, zaroori calls). */
    private void toggleDnd() {
        if (nm == null) return;
        if (!nm.isNotificationPolicyAccessGranted()) {
            launch(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        try {
            nm.setInterruptionFilter(dndOn()
                    ? NotificationManager.INTERRUPTION_FILTER_ALL
                    : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (Exception ignored) { }
        invalidate();
    }

    // Scanner: pehli jo app mile (MIUI Scanner, Google Lens, Huawei Scanner), warna camera
    private static final String[] SCANNERS = {
            "com.xiaomi.scanner", "com.google.ar.lens", "com.huawei.scanner"};

    private void openScanner() {
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        for (String pkg : SCANNERS) {
            Intent in = pm.getLaunchIntentForPackage(pkg);
            if (in != null && launch(in)) return;
        }
        launch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    private void onQuickTile(int i) {
        switch (i) {
            case 0:     // Mobile data: system seedha on/off nahi karne deta
                launchFirst(new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                        new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                break;
            case 1: launchFirst(wifiIntent(), new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
            case 5: launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case 7: launch(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); break;
            case 9: launch(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); break;
            case 6: openScanner(); break;
            case 10: toggleRotate(); break;
            case 11: toggleDnd(); break;
            case 2: sys.toggleTorch(); break;
            case 3: toggleVibrate(); break;
            default: break;     // baaki tiles agle steps mein
        }
    }

    private boolean qpTouch(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_OUTSIDE) {
            closeQuick();
        } else if (a == MotionEvent.ACTION_DOWN) {
            resetTimer();
            if (qp > 0.9f && volHit.contains(e.getX(), e.getY())) {
                volDrag = true;
                lastVolTick = -1;
                setVolumeFromX(e.getX());
                return true;
            }
            if (qp > 0.9f && briHit.contains(e.getX(), e.getY())) {
                if (Settings.System.canWrite(getContext())) {
                    briDrag = true;
                    lastBriTick = -1;
                    setBrightnessFromX(e.getX());
                } else {       // ek baar ki permission: "Modify system settings"
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    launch(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + getContext().getPackageName())));
                }
                return true;
            }
            int t = qp > 0.9f ? qTileAt(e.getX(), e.getY()) : -1;
            if (t >= 0) {
                pressKind = 4;
                pressIdx = t;
                animPress(0.92f, 120, false, false);
            }
        } else if (a == MotionEvent.ACTION_MOVE) {
            resetTimer();
            if (volDrag) setVolumeFromX(e.getX());
            if (briDrag) setBrightnessFromX(e.getX());
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (volDrag || briDrag) {
                volDrag = false;
                briDrag = false;
                invalidate();
                resetTimer();
                return true;
            }
            pressUp();
            if (a == MotionEvent.ACTION_UP && qpOpen) {
                int t = qTileAt(e.getX(), e.getY());
                if (t >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    onQuickTile(t);
                    resetTimer();
                } else {
                    closeQuick();
                }
            }
        }
        return true;
    }

    private void screenSleep(boolean off) {
        h.removeCallbacks(tick);
        if (off) {
            callUi.pause();
            noteUi.pause();
        } else {
            callUi.resume();
            noteUi.resume();
            h.post(tick);
        }
    }

    /** Call aane par: khula panel / quick panel turant band, taake call island saaf dikhe. */
    private void forceIdle() {
        h.removeCallbacks(autoCollapse);
        h.removeCallbacks(holdRun);
        h.removeCallbacks(longPress);
        if (anim != null) anim.cancel();
        if (qpAnim != null) qpAnim.cancel();
        if (swellAnim != null) swellAnim.cancel();
        if (tilesAnim != null) tilesAnim.cancel();
        if (tabAnim != null) tabAnim.cancel();
        if (pressAnim != null) pressAnim.cancel();
        p = 0f;
        expanded = false;
        qp = 0f;
        qpWindow = false;
        qpExp = false;
        holdWin = false;
        qpOpen = false;
        holding = false;
        treeWindow = false;
        tr = 0f;
        h.removeCallbacks(treeRun);
        h.removeCallbacks(lvHide);
        if (lvAnim != null) lvAnim.cancel();
        lvA = 0f;
        lvWin = false;
        cancelDwell();
        consumed = false;
        if (treeAnim != null) treeAnim.cancel();
        swell = 0f;
        tilesT = 0f;
        volDrag = false;
        briDrag = false;
        pressKind = 0;
        pressIdx = -1;
        pressS = 1f;
        prevTab = tab;
        tp = 1f;
    }

    private float tabH(int i) { return i == 3 ? ncH : TAB_H[i]; }

    private float expH() { return lerp(tabH(prevTab), tabH(tab), tp); }

    private void animNc(float to) {
        if (ncAnim != null) ncAnim.cancel();
        if (!expanded || (tab != 3 && prevTab != 3)) { ncH = to; return; }
        ncAnim = ValueAnimator.ofFloat(ncH, to);
        ncAnim.setDuration(220);
        ncAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        ncAnim.addUpdateListener(a -> { ncH = (float) a.getAnimatedValue(); applySize(); });
        ncAnim.start();
    }

    private void applySize() {
        if (qpWindow || treeWindow || callUi.active() || noteUi.active()) return;
        float sw = 1f + 0.08f * swell;
        curW = lerp(IDLE_W, EXP_W, p) * d * sw;
        curH = lerp(IDLE_H, expH(), p) * d * sw;
        if (lvA > 0.001f) {                    // volume/brightness: island failta hai
            curW = Math.max(curW, lerp(IDLE_W, LV_W, lvA) * d);
            curH = Math.max(curH, lerp(IDLE_H, LV_H, lvA) * d);
        }
        settleWindow();
        invalidate();
    }

    /** Window ka size: sirf jab mode badle (idle / stage / quick / tree), har frame nahi. */
    private boolean settleWindow() {
        if (callUi.active() || noteUi.active()) return false;
        int w, hh;
        if (treeWindow) { w = Math.round(EXP_W * d); hh = Math.round(TREE_H * d); }
        else if (qpWindow || holdWin) {
            w = Math.round(EXP_W * d);
            float eh = QP_H;
            if (qpExp) eh = Math.max(eh, qeH / d);
            else if (expanded) eh = Math.max(eh, (tab == 3 || prevTab == 3) ? NoteWidget.MAX_H : STAGE_H);
            hh = Math.round(eh * d);
        }
        else if (lvWin && !expanded && p <= 0.001f && swell <= 0.001f) {
            w = Math.round(EXP_W * d); hh = Math.round(LV_WIN_H * d);
        }
        else if (expanded || p > 0.001f || tp < 1f || swell > 0.001f) {
            w = Math.round(EXP_W * d);
            hh = Math.round(((tab == 3 || prevTab == 3) ? NoteWidget.MAX_H : STAGE_H) * d);
        } else { w = Math.round(IDLE_W * d); hh = Math.round(IDLE_H * d); }
        if (w != winW || hh != winH) { winW = w; winH = hh; host.onResize(w, hh); return true; }
        return false;
    }

    // window bada hone ke baad hi animation shuru: pehle frames kat kar glitch na karein
    private Runnable afterResize;
    private final Runnable afterResizeFallback = this::flushAfterResize;

    private void runAfterResize(boolean resized, Runnable r) {
        if (!resized) { r.run(); return; }
        afterResize = r;
        h.removeCallbacks(afterResizeFallback);
        h.postDelayed(afterResizeFallback, 120);
    }

    private void flushAfterResize() {
        h.removeCallbacks(afterResizeFallback);
        Runnable r = afterResize;
        afterResize = null;
        if (r != null) r.run();
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        if (afterResize != null && w == winW && hh == winH) post(this::flushAfterResize);
    }

    /** Tab badalna: crossfade 150 ms, dot slide, height naye tab ke hisaab se. */
    private void setTab(int t) {
        if (t == tab) return;
        prevTab = tab;
        tab = t;
        store.setTab(t);
        tp = 0f;
        if (tabAnim != null) tabAnim.cancel();
        tabAnim = ValueAnimator.ofFloat(0f, 1f);
        tabAnim.setDuration(150);
        tabAnim.addUpdateListener(a -> {
            tp = (float) a.getAnimatedValue();
            applySize();
        });
        tabAnim.start();
    }

    /** Tab t ki dikhne wali alpha (0..1). */
    private float tabAlpha(int t) {
        if (t == tab) return prevTab == tab ? 1f : tp;
        if (t == prevTab) return 1f - tp;
        return 0f;
    }

    /** Touch hote hi 10 second ka timer dobara shuru. */
    private void resetTimer() {
        if (!expanded && !qpOpen && !treeWindow) return;
        h.removeCallbacks(autoCollapse);
        h.postDelayed(autoCollapse, AUTO_COLLAPSE_MS);
    }

    private void onButton(int i) {
        switch (i) {
            case 0:
                sys.toggleTorch();
                break;
            case 1:
                launchFirst(wifiIntent(), new Intent(Settings.ACTION_WIFI_SETTINGS));
                break;
            case 2:
                launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                break;
            case 3:
                launch(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
                break;
            case 4:
                host.onStop();
                break;
        }
    }

    private boolean launch(Intent in) {
        try {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(in);
            setExpanded(false);
            if (qpWindow) closeQuick();
            if (treeWindow) closeTree();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Pehla jo khul jaye (kuch phones par koi settings screen nahi hoti). */
    private void launchFirst(Intent... list) {
        for (Intent in : list) if (launch(in)) return;
    }

    private Intent wifiIntent() {
        return Build.VERSION.SDK_INT >= 29
                ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                : new Intent(Settings.ACTION_WIFI_SETTINGS);
    }

    // ---- drawing ----
    @Override
    protected void onDraw(Canvas c) {
        if (callUi.active()) {
            float cw = callUi.shapeW(), ch = callUi.shapeH();
            c.save();
            c.translate((getWidth() - cw) / 2f, 0);
            callUi.draw(c, cw, ch);
            c.restore();
            return;
        }
        if (noteUi.active()) {
            float nw = noteUi.shapeW(), nh = noteUi.shapeH();
            c.save();
            c.translate((getWidth() - nw) / 2f, 0);
            noteUi.draw(c, nw, nh);
            c.restore();
            return;
        }
        if (treeWindow) { drawTree(c); return; }
        if (qpWindow) { drawQuick(c); return; }
        float w = curW, hgt = curH;
        c.save();
        c.translate((getWidth() - w) / 2f, 0);
        float r = lerp(IDLE_R, EXP_R, p) * d;
        if (lvA > 0.001f) r = Math.max(r, lerp(IDLE_R, LV_H / 2f, lvA) * d);
        rf.set(0, 0, w, hgt);
        c.drawRoundRect(rf, r, r, bg);

        float idleA = (1f - clamp01(p * 2.2f)) * (1f - clamp01(lvA * 2.5f));
        float expA = clamp01((p - 0.55f) / 0.45f);
        if (idleA > 0.02f) drawIdle(c, w, hgt, idleA);
        if (expA > 0.02f) {
            float a0 = tabAlpha(0) * expA, a1 = tabAlpha(1) * expA, a2 = tabAlpha(2) * expA, a3 = tabAlpha(3) * expA;
            if (a0 > 0.02f) drawSites(c, w, a0);
            if (a1 > 0.02f) drawExpanded(c, w, a1);
            if (a2 > 0.02f) drawApps(c, w, a2);
            if (a3 > 0.02f) noteW.draw(c, w, hgt, (int) (255 * a3));
        }
        if (lvA > 0.3f) drawLevel(c, w, hgt, clamp01((lvA - 0.3f) / 0.7f));
        c.restore();
    }

    private String timeStr() {
        String pat = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm";
        return DateFormat.format(pat, new Date()).toString();
    }

    private void drawIdle(Canvas c, float w, float hgt, float a) {
        int al = (int) (255 * a);
        float cy = hgt / 2f;
        txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(13 * d);
        txt.setTextAlign(Paint.Align.CENTER);
        c.drawText(timeStr(), w / 2f, cy + 4.6f * d, txt);

        fill.setStyle(Paint.Style.FILL);
        // torch on: peela dot (left), panel band ho tab bhi
        if (sys.torchOn) {
            fill.setColor(Theme.YELLOW);
            fill.setAlpha(al);
            c.drawCircle(14 * d, cy, 3.6f * d, fill);
        }
        // battery dot (right): charging = hara, kam (<=15) = laal, warna dhundla
        if (sys.battery >= 0) {
            boolean low = !sys.charging && sys.battery <= 15;
            fill.setColor(sys.charging ? Theme.GREEN : low ? Theme.RED : Theme.DOT_GRAY);
            fill.setAlpha(al);
            c.drawCircle(w - 14 * d, cy, 3.2f * d, fill);
            if (sys.charging) {
                txt.setColor(Theme.GREEN);
                txt.setAlpha(al);
                txt.setTextSize(10 * d);
                c.drawText("\u26A1", w - 26 * d, cy + 3.6f * d, txt);
            }
        }
    }

    private void drawQuick(Canvas c) {
        float qw = getWidth(), qh = QP_H * d;
        float sw = 1f + 0.08f * swell;
        float pw = qpExp ? qeW : IDLE_W * d * sw, ph = qpExp ? qeH : IDLE_H * d * sw;
        // island kisi kinare se nahi aata: pill hi apne size se panel ban kar failti hai
        float e = qp * qp * (3f - 2f * qp);
        float w = lerp(pw, qw, e), hh = lerp(ph, qh, e);
        float r = lerp(qpExp ? EXP_R * d : IDLE_R * d * sw, EXP_R * d, e);
        float l = (qw - w) / 2f;
        rf.set(l, 0, l + w, hh);
        c.drawRoundRect(rf, r, r, bg);
        float ta = clamp01((qp - 0.35f) / 0.65f);
        if (qp < 0.98f && !qpExp) {             // pill ka idle content (time/dots) fade out
            c.save();
            c.translate((qw - pw) / 2f, 0);
            drawIdle(c, pw, ph, 1f - clamp01(qp * 3f));
            c.restore();
        } else if (qp < 0.98f) {                // khule island ka content fade out, size smooth neeche
            float oa = 1f - clamp01(qp * 3f);
            if (oa > 0.02f) {
                android.graphics.Path oc = new android.graphics.Path();
                oc.addRoundRect(rf, r, r, android.graphics.Path.Direction.CW);
                c.save();
                c.clipPath(oc);
                c.translate((qw - qeW) / 2f, 0);
                float a0 = tabAlpha(0) * oa, a1 = tabAlpha(1) * oa, a2 = tabAlpha(2) * oa, a3 = tabAlpha(3) * oa;
                if (a0 > 0.02f) drawSites(c, qeW, a0);
                if (a1 > 0.02f) drawExpanded(c, qeW, a1);
                if (a2 > 0.02f) drawApps(c, qeW, a2);
                if (a3 > 0.02f) noteW.draw(c, qeW, qeH, (int) (255 * a3));
                c.restore();
            }
        }
        if (ta > 0.01f) {
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(rf, r, r, android.graphics.Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.saveLayerAlpha(0, 0, qw, getHeight(), (int) (255 * ta));
            drawQuickTiles(c, qw);
            c.restore();
            c.restore();
        }
    }

    private void drawQuickTiles(Canvas c, float qw) {
        float cell = (qw - 28 * d) / 4f;
        float tw = cell - 8 * d, th = 56 * d;
        boolean vib = audio != null && audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        boolean rot = rotateOn(), dnd = dndOn();
        for (int i = 0; i < qRect.length; i++) {
            float cx = 14 * d + cell * (i % 4 + 0.5f);
            float top = (16 + (i / 4) * 64) * d;
            qRect[i].set(cx - tw / 2f, top, cx + tw / 2f, top + th);
            float in = tileIn(i);
            if (in <= 0.01f) continue;
            int al = (int) (255 * in);
            float sc = (0.8f + 0.2f * in) * press(4, i);
            boolean on = (i == 2 && torchFill > 0.5f) || (i == 3 && vib)
                    || (i == 10 && rot) || (i == 11 && dnd);
            boolean dark = i == 2;      // peele tile par kala text
            c.save();
            c.scale(sc, sc, cx, top + th / 2f);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(on ? (i == 2 ? Theme.YELLOW : i == 3 ? Theme.TILE[3]
                    : i == 10 ? Theme.TILE[0] : Theme.TILE[4]) : Theme.BTN);
            fill.setAlpha(al);
            rf.set(cx - tw / 2f, top, cx + tw / 2f, top + th);
            c.drawRoundRect(rf, 16 * d, 16 * d, fill);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(android.graphics.Typeface.DEFAULT);
            txt.setColor(on && dark ? Color.BLACK : Color.WHITE);
            txt.setAlpha(al);
            txt.setTextSize(19 * d);
            drawVec(c, i == 3 && vib ? 13 : i, cx, top + 21 * d, 24 * d,
                    on && dark ? Color.BLACK : Color.WHITE, al);
            txt.setTextSize(10 * d);
            txt.setColor(on && dark ? Color.BLACK : on ? Color.WHITE : Theme.TEXT_DIM);
            txt.setAlpha(al);
            c.drawText(fit(i == 3 && vib ? "Vibrate" : Q_NAME[i], tw - 4 * d), cx, top + 46 * d, txt);
            c.restore();
        }
        drawSlider(c, qw, tileIn(11), 232 * d, "", briFrac(), briDrag, briHit);
    }

    private static final int[] CIC = {2, 1, 5, 7, 12};
    private final Drawable[] vec = new Drawable[15];
    private static final int[] VEC_ID = {R.drawable.ic_q0, R.drawable.ic_q1, R.drawable.ic_q2,
            R.drawable.ic_q3, R.drawable.ic_q4, R.drawable.ic_q5, R.drawable.ic_q6, R.drawable.ic_q7,
            R.drawable.ic_q8, R.drawable.ic_q9, R.drawable.ic_q10, R.drawable.ic_q11,
            R.drawable.ic_q12, R.drawable.ic_q13, R.drawable.ic_q14};

    /** Vector icon (cx, cy markaz, sz size) ko tint + alpha ke saath canvas par banata hai. */
    private void drawVec(Canvas c, int k, float cx, float cy, float sz, int color, int al) {
        if (vec[k] == null) vec[k] = getContext().getDrawable(VEC_ID[k]).mutate();
        Drawable dr = vec[k];
        dr.setBounds((int) (cx - sz / 2), (int) (cy - sz / 2), (int) (cx + sz / 2), (int) (cy + sz / 2));
        dr.setTint(color);
        dr.setAlpha(al);
        dr.draw(c);
    }

    private void drawSlider(Canvas c, float qw, float a, float cy, String icon,
                            float f, boolean drag, RectF hit) {
        if (a <= 0.01f) return;
        int al = (int) (255 * a);
        volTrackL = 52 * d;
        volTrackR = qw - 26 * d;
        hit.set(14 * d, cy - 18 * d, qw - 14 * d, cy + 18 * d);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(17 * d);
        drawVec(c, 14, 30 * d, cy, 20 * d, Color.WHITE, al);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Theme.BTN);
        fill.setAlpha(al);
        rf.set(volTrackL, cy - 5 * d, volTrackR, cy + 5 * d);
        c.drawRoundRect(rf, 5 * d, 5 * d, fill);
        fill.setColor(Color.WHITE);
        fill.setAlpha(al);
        float x = volTrackL + (volTrackR - volTrackL) * f;
        rf.set(volTrackL, cy - 5 * d, Math.max(x, volTrackL + 10 * d), cy + 5 * d);
        c.drawRoundRect(rf, 5 * d, 5 * d, fill);
        c.drawCircle(x, cy, (drag ? 11 : 8) * d, fill);   // touch par thumb bada
    }

    private String fit(String str, float maxW) {
        int k = txt.breakText(str, true, maxW, null);
        return k >= str.length() ? str : str.substring(0, Math.max(0, k - 1)) + "\u2026";
    }

    private void drawSites(Canvas c, float w, float a) {
        int al = (int) (255 * a);
        float cell = (w - 28 * d) / 4f;
        for (RectF r : siteRect) r.setEmpty();
        int n = Math.min(sites.size() + 1, siteRect.length);   // +1 = "Add" tile
        for (int i = 0; i < n; i++) {
            boolean isAdd = i == sites.size();
            Store.Site s = isAdd ? new Store.Site("+", "Add a site") : sites.get(i);
            float cx = 14 * d + cell * (i % 4 + 0.5f);
            float top = (14 + (i / 4) * 88) * d;
            siteRect[i].set(cx - cell / 2f, top, cx + cell / 2f, top + 82 * d);
            float ps = press(2, i);
            c.save();
            c.scale(ps, ps, cx, top + 30 * d);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(isAdd ? Theme.BTN : Theme.tile(s.name));
            fill.setAlpha(al);
            rf.set(cx - 22 * d, top + 4 * d, cx + 22 * d, top + 48 * d);
            c.drawRoundRect(rf, 12 * d, 12 * d, fill);

            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            txt.setColor(Color.WHITE);
            txt.setAlpha(al);
            txt.setTextSize(20 * d);
            c.drawText(isAdd ? "+" : s.letter(), cx, top + 33 * d, txt);

            txt.setTextSize(11 * d);
            c.drawText(isAdd ? "Add a site" : fit(s.name, cell - 4 * d), cx, top + 62 * d, txt);
            txt.setTypeface(android.graphics.Typeface.DEFAULT);
            txt.setColor(Theme.TEXT_DIM);
            txt.setAlpha(al);
            txt.setTextSize(9 * d);
            if (!isAdd) c.drawText(fit(s.host(), cell - 4 * d), cx, top + 75 * d, txt);
            c.restore();
        }
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Theme.OUTLINE);
        txt.setAlpha(al);
        txt.setTextSize(10 * d);
        c.drawText("Tap: kholo \u00B7 Long-press: sites manage karo",
                w / 2f, (TAB_H[0] - TAB_BAR_H - 8) * d, txt);
    }

    private void drawApps(Canvas c, float w, float a) {
        int al = (int) (255 * a);
        float cell = (w - 28 * d) / 4f;
        for (RectF r : appRect) r.setEmpty();
        txt.setTextAlign(Paint.Align.CENTER);
        if (apps.isEmpty()) {
            txt.setTypeface(android.graphics.Typeface.DEFAULT);
            txt.setColor(Theme.TEXT_DIM);
            txt.setAlpha(al);
            txt.setTextSize(14 * d);
            c.drawText("Koi app nahi mili", w / 2f, 110 * d, txt);
        }
        for (int i = 0; i < apps.size(); i++) {
            Apps.Item it = apps.get(i);
            float cx = 14 * d + cell * (i % 4 + 0.5f);
            float top = (14 + (i / 4) * 88) * d;
            appRect[i].set(cx - cell / 2f, top, cx + cell / 2f, top + 82 * d);
            float ps = press(3, i);
            c.save();
            c.scale(ps, ps, cx, top + 30 * d);
            Drawable dr = it.icon;
            dr.setBounds(Math.round(cx - 22 * d), Math.round(top + 4 * d),
                    Math.round(cx + 22 * d), Math.round(top + 48 * d));
            dr.setAlpha(al);
            dr.draw(c);
            txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            txt.setColor(Color.WHITE);
            txt.setAlpha(al);
            txt.setTextSize(11 * d);
            c.drawText(fit(it.label, cell - 4 * d), cx, top + 62 * d, txt);
            c.restore();
        }
        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Theme.OUTLINE);
        txt.setAlpha(al);
        txt.setTextSize(10 * d);
        c.drawText("Tap: app kholo \u00B7 Long-press: apps ki list badlo",
                w / 2f, (TAB_H[2] - TAB_BAR_H - 8) * d, txt);
    }

    private void drawPlaceholder(Canvas c, float w, float a, String name) {
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Theme.TEXT_DIM);
        txt.setAlpha((int) (255 * a));
        txt.setTextSize(15 * d);
        c.drawText(name + " (jaldi aa raha hai)", w / 2f, 110 * d, txt);
    }

    private void drawTabs(Canvas c, float w, float hgt, float a) {
        float cell = (w - 28 * d) / 3f;
        float top = hgt - TAB_BAR_H * d;
        for (int i = 0; i < 3; i++) {
            float cx = 14 * d + cell * (i + 0.5f);
            tabRect[i].set(cx - cell / 2f, top - 4 * d, cx + cell / 2f, hgt);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(android.graphics.Typeface.DEFAULT);
            txt.setColor(Color.WHITE);
            txt.setAlpha((int) (255 * a * (0.4f + 0.6f * tabAlpha(i))));
            txt.setTextSize(16 * d);
            c.drawText(TAB_ICON[i], cx, top + 14 * d, txt);
        }
        // neeche ka dot naye icon tak slide karta hai
        float pos = lerp(prevTab, tab, tp);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * a));
        c.drawCircle(14 * d + cell * (pos + 0.5f), hgt - 7 * d, 2.2f * d, fill);
    }

    private void drawExpanded(Canvas c, float w, float a) {
        int al = (int) (255 * a);
        // time + date (left)
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(38 * d);
        c.drawText(timeStr(), 22 * d, 62 * d, txt);

        txt.setTypeface(android.graphics.Typeface.DEFAULT);
        txt.setColor(Theme.TEXT_DIM);
        txt.setAlpha(al);
        txt.setTextSize(13 * d);
        String date = DateFormat.format("EEE, d MMM", new Date()).toString();
        c.drawText(date.toUpperCase(Locale.getDefault()), 24 * d, 84 * d, txt);

        // battery (right)
        if (sys.battery >= 0) {
            float bw = 54 * d, bh = 22 * d;
            float bx = w - 22 * d - bw, by = 30 * d;
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(1.6f * d);
            fill.setColor(Theme.OUTLINE);
            fill.setAlpha(al);
            rf.set(bx, by, bx + bw, by + bh);
            c.drawRoundRect(rf, 6 * d, 6 * d, fill);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(sys.charging ? Theme.GREEN
                    : sys.battery <= 15 ? Theme.RED : Color.WHITE);
            fill.setAlpha(al);
            rf.set(bx + 3 * d, by + 3 * d, bx + 3 * d + (bw - 6 * d) * sys.battery / 100f, by + bh - 3 * d);
            c.drawRoundRect(rf, 3.5f * d, 3.5f * d, fill);

            txt.setTextAlign(Paint.Align.RIGHT);
            txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            txt.setColor(Color.WHITE);
            txt.setAlpha(al);
            txt.setTextSize(13 * d);
            c.drawText((sys.charging ? "⚡ " : "") + sys.battery + "%", w - 22 * d, by + bh + 20 * d, txt);
        }

        // buttons row
        float cell = (w - 28 * d) / 5f;
        float cy = 132 * d, rad = 22 * d;
        for (int i = 0; i < 0; i++) {      // buttons ab hold + right wale Quick panel mein
            float cx = 14 * d + cell * (i + 0.5f);
            btn[i].set(cx - cell / 2f, cy - rad - 4 * d, cx + cell / 2f, cy + rad + 24 * d);
            float ps = press(1, i);
            c.save();
            c.scale(ps, ps, cx, cy);
            boolean on = (i == 0 && torchFill > 0.5f);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(i == 4 ? Theme.BTN_CLOSE : Theme.BTN);
            fill.setAlpha(al);
            c.drawCircle(cx, cy, rad, fill);
            if (i == 0 && torchFill > 0.01f) {      // rang andar se bharta hai
                fill.setColor(Theme.YELLOW);
                fill.setAlpha(al);
                c.drawCircle(cx, cy, rad * torchFill, fill);
            }

            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(android.graphics.Typeface.DEFAULT);
            txt.setColor(on ? Color.BLACK : Color.WHITE);
            txt.setAlpha(al);
            txt.setTextSize(20 * d);
            drawVec(c, CIC[i], cx, cy, 24 * d, on ? Color.BLACK : Color.WHITE, al);

            txt.setColor(Theme.TEXT_DIM);
            txt.setAlpha(al);
            txt.setTextSize(11 * d);
            c.drawText(LABEL[i], cx, cy + rad + 17 * d, txt);
            c.restore();
        }
    }
}
