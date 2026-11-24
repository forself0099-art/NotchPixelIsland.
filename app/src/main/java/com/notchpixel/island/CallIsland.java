package com.notchpixel.island;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Locale;

/**
 * Calls ka island. Call aate hi island apne aap badalta hai:
 * aati call (Answer / Decline), chalti call ka pill, bada card, "Call ended".
 * IslandView sirf draw / touch yahan bhej deta hai.
 */
final class CallIsland implements CallState.Listener {

    interface Env {
        void resize(int wPx, int hPx);
        void redraw();
        void haptic(int constant);
        void launch(Intent in);
        /** Call island screen le raha hai: baaki island ko band karo. */
        void onStart();
        /** Call island khatam: normal idle pill wapas. */
        void onGone();
    }

    static final int NONE = 0, INCOMING = 1, PILL = 2, CARD = 3, ENDED = 4, CLOSING = 5;
    private static final float IDLE_W = 120, IDLE_H = 34, IDLE_R = 17;
    //                                 NONE  INCOMING PILL  CARD  ENDED CLOSING
    private static final float[] W = {IDLE_W, 336,    176,  340,  220,  IDLE_W};
    private static final float[] H = {IDLE_H, 68,     34,   160,  44,   IDLE_H};
    private static final float[] R = {IDLE_R, 34,     17,   32,   22,   IDLE_R};
    private static final long WAVE_MS = 66;          // ~15 fps, itna kaafi hai

    private static final int B_NONE = -2, B_BODY = 0, B_ANSWER = 1, B_DECLINE = 2,
            B_OPEN = 3, B_HANG = 4, B_HEAD = 5;
    private static final long ENDED_MS = 2000;           // "Call ended" 2 second
    private static final long CARD_IDLE_MS = 10000;      // card 10 second baad pill ban jata hai

    private final Context ctx;
    private final Handler h;
    private final Env env;
    private final float d;
    private final float maxWdp;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path clip = new Path();
    private final Path gp = new Path();

    private int mode = NONE, prevMode = NONE;
    private CallInfo cur;

    // size animation (dp)
    private float w = IDLE_W, hh = IDLE_H, rad = IDLE_R;
    private float fromW, fromH, fromR, toW, toH, toR, t = 1f;
    private ValueAnimator morph;

    // buttons
    private final RectF answerR = new RectF(), declineR = new RectF();
    private final RectF openR = new RectF(), hangR = new RectF();
    private long endedDur;
    private int downCode = B_NONE, pressIdx = B_NONE;
    private float pressS = 1f;
    private ValueAnimator pressAnim;

    private final Runnable endRun = this::finish;
    private final Runnable cardIdle = () -> { if (mode == CARD) enter(PILL); };

    private final Runnable wave = new Runnable() {
        @Override public void run() {
            if (!animating()) return;
            env.redraw();
            h.postDelayed(this, WAVE_MS);
        }
    };

    CallIsland(Context ctx, Handler h, Env env) {
        this.ctx = ctx;
        this.h = h;
        this.env = env;
        this.d = ctx.getResources().getDisplayMetrics().density;
        this.maxWdp = ctx.getResources().getDisplayMetrics().widthPixels / d - 16f;
        bg.setColor(Color.BLACK);
    }

    boolean active() { return mode != NONE; }
    float shapeW() { return w * d; }
    float shapeH() { return hh * d; }

    void release() {
        h.removeCallbacks(wave);
        h.removeCallbacks(endRun);
        h.removeCallbacks(cardIdle);
        if (morph != null) morph.cancel();
        if (pressAnim != null) pressAnim.cancel();
        CallState.setListener(null);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---- CallState se khabar ----
    @Override public void onCall(CallInfo c) { h.post(() -> handleCall(c)); }

    @Override public void onEnded(CallInfo last, long durMs) { h.post(() -> handleEnded(last, durMs)); }

    private void handleCall(CallInfo c) {
        if (c == null) {
            if (mode != NONE && mode != ENDED) finish();
            return;
        }
        cur = c;
        if (c.state == CallInfo.State.INCOMING) {
            if (mode != INCOMING) {
                enter(INCOMING);
                env.haptic(HapticFeedbackConstants.LONG_PRESS);
            } else {
                env.redraw();
            }
        } else if (mode == PILL || mode == CARD) {
            env.redraw();                // naam / timer update
        } else {
            boolean fromRing = mode == INCOMING;
            enter(PILL);                 // chalti call: chhota pill
            if (fromRing) env.haptic(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void handleEnded(CallInfo last, long durMs) {
        if (last == null) return;
        cur = last;
        endedDur = durMs;
        enter(ENDED);
        h.postDelayed(endRun, ENDED_MS);     // 2 second, phir idle
    }

    // ---- halat badalna ----
    private boolean paused;       // screen band: waveform / pulse ruk jate hain (battery bachat)

    private boolean animating() {
        return !paused && (mode == INCOMING || mode == PILL || mode == CARD);
    }

    /** Screen band hui: animation loop band. */
    void pause() {
        paused = true;
        h.removeCallbacks(wave);
    }

    /** Screen on hui: animation wapas (timer to draw ke waqt hi sahi dikhta hai). */
    void resume() {
        paused = false;
        startWave();
        if (mode != NONE) env.redraw();
    }

    private void startWave() {
        h.removeCallbacks(wave);
        if (animating()) h.postDelayed(wave, WAVE_MS);
    }

    private void finish() {
        if (mode == NONE || mode == CLOSING) return;
        enter(CLOSING);
    }

    private void enter(int m) {
        h.removeCallbacks(endRun);
        h.removeCallbacks(cardIdle);
        if (m == CARD) h.postDelayed(cardIdle, CARD_IDLE_MS);
        if (mode == NONE) {
            env.onStart();
            w = IDLE_W; hh = IDLE_H; rad = IDLE_R;
            prevMode = NONE;
        } else {
            prevMode = mode;
        }
        fromW = w; fromH = hh; fromR = rad;
        mode = m;
        toW = Math.min(W[m], maxWdp);
        toH = H[m];
        toR = R[m];
        t = 0f;
        if (morph != null) morph.cancel();
        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(toW < fromW ? 220 : 260);
        morph.setInterpolator(new DecelerateInterpolator(1.6f));
        morph.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            w = lerp(fromW, toW, t);
            hh = lerp(fromH, toH, t);
            rad = lerp(fromR, toR, t);
            env.redraw();      // window resize nahi: shape canvas mein animate hota hai
        });
        morph.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled) env.resize(Math.round(toW * d), Math.round(toH * d));
                if (cancelled || mode != CLOSING) return;
                mode = NONE;
                prevMode = NONE;
                cur = null;
                env.onGone();
            }
        });
        // window ek baar bada (final size ka), animation ke baad ek baar final size
        env.resize(Math.round(Math.max(fromW, toW) * d), Math.round(Math.max(fromH, toH) * d));
        morph.setStartDelay(40);
        morph.start();
        startWave();
    }

    // ---- drawing ----
    void draw(Canvas c, float pw, float ph) {
        float r = rad * d;
        rf.set(0, 0, pw, ph);
        bg.setColor(Color.BLACK);
        c.drawRoundRect(rf, r, r, bg);

        clip.reset();
        clip.addRoundRect(rf, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        float aOld = 1f - clamp01(t / 0.4f);
        float aNew = clamp01((t - 0.45f) / 0.55f);
        if (prevMode != NONE && prevMode != CLOSING && aOld > 0.02f) drawMode(c, prevMode, pw, ph, aOld);
        if (mode != CLOSING && aNew > 0.02f) drawMode(c, mode, pw, ph, aNew);
        c.restore();
    }

    private void drawMode(Canvas c, int m, float pw, float ph, float a) {
        switch (m) {
            case INCOMING: drawIncoming(c, pw, ph, a); break;
            case PILL: drawPill(c, pw, ph, a); break;
            case CARD: drawCard(c, pw, ph, a); break;
            case ENDED: drawEnded(c, pw, ph, a); break;
            default: break;
        }
    }

    private void drawIncoming(Canvas c, float pw, float ph, float a) {
        CallInfo ci = cur;
        if (ci == null) return;
        int al = (int) (255 * a);
        float cy = ph / 2f, pad = 14 * d;

        // app ka badge + halka pulse ring
        float bx = pad + 20 * d;
        float phase = (SystemClock.uptimeMillis() % 1400) / 1400f;
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(2f * d);
        fill.setColor(ci.color);
        fill.setAlpha((int) (al * 0.6f * (1f - phase)));
        c.drawCircle(bx, cy, (20 + 8 * phase) * d, fill);
        badge(c, bx, cy, 20 * d, ci, al);

        // Decline (laal) aur Answer (hara)
        float aCx = pw - pad - 22 * d, dCx = aCx - 56 * d;
        answerR.set(aCx - 22 * d, cy - 22 * d, aCx + 22 * d, cy + 22 * d);
        declineR.set(dCx - 22 * d, cy - 22 * d, dCx + 22 * d, cy + 22 * d);
        circleBtn(c, dCx, cy, 22 * d, Theme.RED, false, press(B_DECLINE), al);
        circleBtn(c, aCx, cy, 22 * d, Theme.GREEN, true, press(B_ANSWER), al);

        // naam + app
        float tx = pad + 40 * d + 12 * d;
        float avail = dCx - 22 * d - 8 * d - tx;
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(15 * d);
        c.drawText(fit(ci.caller, txt, avail), tx, cy - 3 * d, txt);
        txt.setTypeface(Typeface.DEFAULT);
        txt.setColor(Theme.TEXT_DIM);
        txt.setAlpha(al);
        txt.setTextSize(12 * d);
        c.drawText(fit(ci.appName + " \u00B7 incoming call", txt, avail), tx, cy + 15 * d, txt);
    }

    // ---- chalti call ka pill: badge + hilti waveform + timer ----
    private void drawPill(Canvas c, float pw, float ph, float a) {
        CallInfo ci = cur;
        if (ci == null) return;
        int al = (int) (255 * a);
        float cy = ph / 2f;
        badge(c, 7 * d + 10 * d, cy, 10 * d, ci, al);

        String tm = clock(System.currentTimeMillis() - ci.startMs);
        txt.setTextAlign(Paint.Align.RIGHT);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(12.5f * d);
        c.drawText(tm, pw - 12 * d, cy + 4.4f * d, txt);

        float left = 7 * d + 20 * d + 9 * d;
        float right = pw - 12 * d - txt.measureText(tm) - 9 * d;
        bars(c, left, right, cy, ph * 0.62f, 3 * d, 2.6f * d, 11, ci.color, al);
    }

    // ---- bada card: naam, bada timer, waveform, Call kholo / Hang up ----
    private void drawCard(Canvas c, float pw, float ph, float a) {
        CallInfo ci = cur;
        if (ci == null) return;
        int al = (int) (255 * a);
        float pad = 18 * d;
        badge(c, pad + 22 * d, 40 * d, 22 * d, ci, al);

        float tx = pad + 44 * d + 12 * d;
        float avail = pw - pad - tx;
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(17 * d);
        c.drawText(fit(ci.caller, txt, avail), tx, 37 * d, txt);
        txt.setTypeface(Typeface.DEFAULT);
        txt.setColor(Theme.TEXT_DIM);
        txt.setAlpha(al);
        txt.setTextSize(12 * d);
        c.drawText(fit(ci.appName + " \u00B7 voice call", txt, avail), tx, 55 * d, txt);

        // bada timer (left) + waveform (right)
        String tm = clock(System.currentTimeMillis() - ci.startMs);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(32 * d);
        c.drawText(tm, pad, 99 * d, txt);
        float left = pad + txt.measureText(tm) + 16 * d;
        bars(c, left, pw - pad, 88 * d, 40 * d, 4 * d, 3 * d, 18, ci.color, al);

        // buttons
        float top = ph - 12 * d - 36 * d, bot = ph - 12 * d;
        float mid = pw / 2f, gap = 5 * d;
        openR.set(pad, top, mid - gap, bot);
        hangR.set(mid + gap, top, pw - pad, bot);
        pillBtn(c, openR, Theme.BTN, "Call kholo", press(B_OPEN), al);
        pillBtn(c, hangR, Theme.RED, "Hang up", press(B_HANG), al);
    }

    private void pillBtn(Canvas c, RectF r, int color, String label, float scale, int al) {
        c.save();
        c.scale(scale, scale, r.centerX(), r.centerY());
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        fill.setAlpha(al);
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(14 * d);
        c.drawText(label, r.centerX(), r.centerY() + 5 * d, txt);
        c.restore();
    }

    // ---- "Call ended 05:42" ----
    private void drawEnded(Canvas c, float pw, float ph, float a) {
        CallInfo ci = cur;
        if (ci == null) return;
        int al = (int) (255 * a);
        float cy = ph / 2f;
        badge(c, 10 * d + 13 * d, cy, 13 * d, ci, al);
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(14 * d);
        c.drawText("Call ended", 10 * d + 26 * d + 10 * d, cy + 5 * d, txt);
        txt.setTextAlign(Paint.Align.RIGHT);
        txt.setColor(Theme.RED);
        txt.setAlpha(al);
        c.drawText(clock(endedDur), pw - 14 * d, cy + 5 * d, txt);
    }

    /** Hilti hui bars. Yeh sirf animation hai, asli awaaz nahi (Android doosri app ki awaaz pakadne nahi deta). */
    private void bars(Canvas c, float left, float right, float cy, float maxH,
                      float bw, float gap, int maxN, int color, int al) {
        int n = (int) ((right - left + gap) / (bw + gap));
        if (n > maxN) n = maxN;
        if (n < 1) return;
        float total = n * bw + (n - 1) * gap;
        float x = (left + right) / 2f - total / 2f;
        long ms = SystemClock.uptimeMillis();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        fill.setAlpha(al);
        for (int i = 0; i < n; i++) {
            float bh = Math.max(bw, maxH * level(i, ms));
            rf.set(x, cy - bh / 2f, x + bw, cy + bh / 2f);
            c.drawRoundRect(rf, bw / 2f, bw / 2f, fill);
            x += bw + gap;
        }
    }

    private static float level(int i, long ms) {
        float s = ms / 1000f;
        float v1 = 0.5f + 0.5f * (float) Math.sin(s * (5.2f + (i % 4) * 1.3f) + i * 1.7f);
        float v2 = 0.5f + 0.5f * (float) Math.sin(s * 2.3f + i * 0.9f);
        return 0.18f + 0.82f * (v1 * 0.6f + v2 * 0.4f);
    }

    /** mm:ss, ek ghante ke baad h:mm:ss. */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long hr = s / 3600, mn = (s / 60) % 60, sc = s % 60;
        return hr > 0 ? String.format(Locale.US, "%d:%02d:%02d", hr, mn, sc)
                : String.format(Locale.US, "%02d:%02d", mn, sc);
    }

    /** App ka rang + pehla letter. */
    private void badge(Canvas c, float cx, float cy, float r, CallInfo ci, int al) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ci.color);
        fill.setAlpha(al);
        c.drawCircle(cx, cy, r, fill);
        String s = ci.appName == null || ci.appName.isEmpty() ? "?"
                : ci.appName.substring(0, 1).toUpperCase();
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(Color.WHITE);
        txt.setAlpha(al);
        txt.setTextSize(r * 0.95f);
        c.drawText(s, cx, cy + r * 0.34f, txt);
    }

    /** Gol button: andar tick (Answer) ya cross (Decline), dabne par chhota. */
    private void circleBtn(Canvas c, float cx, float cy, float r, int color,
                           boolean tick, float scale, int al) {
        c.save();
        c.scale(scale, scale, cx, cy);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        fill.setAlpha(al);
        c.drawCircle(cx, cy, r, fill);
        gp.reset();
        if (tick) {
            gp.moveTo(cx - 0.38f * r, cy + 0.02f * r);
            gp.lineTo(cx - 0.10f * r, cy + 0.30f * r);
            gp.lineTo(cx + 0.42f * r, cy - 0.28f * r);
        } else {
            gp.moveTo(cx - 0.30f * r, cy - 0.30f * r);
            gp.lineTo(cx + 0.30f * r, cy + 0.30f * r);
            gp.moveTo(cx + 0.30f * r, cy - 0.30f * r);
            gp.lineTo(cx - 0.30f * r, cy + 0.30f * r);
        }
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(2.6f * d);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.WHITE);
        fill.setAlpha(al);
        c.drawPath(gp, fill);
        fill.setStrokeCap(Paint.Cap.BUTT);
        fill.setStrokeJoin(Paint.Join.MITER);
        c.restore();
    }

    private String fit(String s, Paint p, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("\u2026") > max) n--;
        return s.substring(0, n) + "\u2026";
    }

    // ---- touch ----
    private boolean in(RectF r, float x, float y) {
        float s = 4 * d;
        return x >= r.left - s && x <= r.right + s && y >= r.top - s && y <= r.bottom + s;
    }

    private int hit(float x, float y) {
        if (mode == INCOMING) {
            if (in(answerR, x, y)) return B_ANSWER;
            if (in(declineR, x, y)) return B_DECLINE;
        }
        if (mode == CARD) {
            if (in(hangR, x, y)) return B_HANG;
            if (in(openR, x, y)) return B_OPEN;
            if (y < 68 * d) return B_HEAD;
        }
        return B_BODY;
    }

    private float press(int code) { return pressIdx == code ? pressS : 1f; }

    private void animPress(float to, long ms, boolean overshoot) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressS, to);
        pressAnim.setDuration(ms);
        pressAnim.setInterpolator(overshoot ? new OvershootInterpolator(2.5f)
                : new DecelerateInterpolator());
        pressAnim.addUpdateListener(a -> { pressS = (float) a.getAnimatedValue(); env.redraw(); });
        pressAnim.start();
    }

    private void unpress() {
        if (pressIdx != B_NONE) animPress(1f, 300, true);
    }

    boolean touch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mode == CARD) enter(PILL);   // bahar tap: card wapas pill
                return true;
            case MotionEvent.ACTION_DOWN:
                if (mode == CARD) {              // touch hote hi 10 second ka timer dobara
                    h.removeCallbacks(cardIdle);
                    h.postDelayed(cardIdle, CARD_IDLE_MS);
                }
                downCode = hit(e.getX(), e.getY());
                if (downCode >= B_ANSWER && downCode <= B_HANG) {
                    pressIdx = downCode;
                    animPress(0.92f, 120, false);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressIdx != B_NONE && hit(e.getX(), e.getY()) != pressIdx) {
                    unpress();
                    downCode = B_NONE;
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int code = hit(e.getX(), e.getY());
                boolean ok = code == downCode;
                unpress();
                downCode = B_NONE;
                if (ok) tap(code);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                unpress();
                downCode = B_NONE;
                return true;
            default:
                return true;
        }
    }

    private void tap(int code) {
        CallInfo ci = cur;
        if (ci == null) return;
        env.haptic(HapticFeedbackConstants.CLOCK_TICK);
        if (mode == INCOMING) {
            if (code == B_ANSWER) doAnswer(ci);
            else if (code == B_DECLINE) doDecline(ci);
            else openApp(ci);                // body par tap: app khol do
        } else if (mode == PILL) {
            enter(CARD);                     // pill par tap: bada card
        } else if (mode == CARD) {
            if (code == B_HANG) doHangUp(ci);
            else if (code == B_OPEN) { openApp(ci); enter(PILL); }
            else if (code == B_HEAD) enter(PILL);
        }
    }

    private void doAnswer(CallInfo ci) {
        if (ci.demo) {
            ci.state = CallInfo.State.ONGOING;
            ci.startMs = System.currentTimeMillis();
            CallState.post(ci);
            return;
        }
        if (!CallInfo.send(ci.answer)) openApp(ci);
    }

    private void doDecline(CallInfo ci) {
        if (ci.demo) { CallState.remove(ci.key); return; }
        if (!CallInfo.send(ci.decline)) openApp(ci);
    }

    private void doHangUp(CallInfo ci) {
        if (ci.demo) { CallState.remove(ci.key); return; }
        if (!CallInfo.send(ci.hangup)) openApp(ci);
    }

    private void openApp(CallInfo ci) {
        Intent li = ctx.getPackageManager().getLaunchIntentForPackage(ci.pkg);
        if (li != null) env.launch(li);
    }
}
