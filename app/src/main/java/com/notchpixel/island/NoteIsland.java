package com.notchpixel.island;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications ka island. Notification aate hi pill apni jagah se phail kar card ban jata hai
 * (kisi kinare se nahi aata). Har kism ka apna layout: Message (WhatsApp...), Video (YouTube...),
 * Mail, Social, Progress (download) aur aam Alert. Tap = app khulti hai, upar swipe = band.
 */
final class NoteIsland implements NoteState.Listener {

    interface Env {
        void resize(int wPx, int hPx);
        void redraw();
        void haptic(int constant);
        void launch(Intent in);
        void onStart();
        void onGone();
        /** User panel / tree / call mein hai: notification na dikhao. */
        boolean busy();
    }

    private static final float IDLE_W = 120, IDLE_H = 34, IDLE_R = 17, CARD_W = 340;
    private static final long SHOW_MS = 5000, LONG_MS = 6500, WAVE_MS = 66;
    private static final int NONE = 0, SHOW = 1, CLOSING = 2;

    private final Context ctx;
    private final Handler h;
    private final Env env;
    private final float d;
    private final float maxWdp;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path clip = new Path();
    private final Path gp = new Path();
    private final Matrix mx = new Matrix();

    private int mode = NONE;
    private NoteInfo cur;
    private int more;                      // card dikhte waqt aur kitni aayin
    private boolean fresh;                 // NONE se aaya: content fade-in

    private float w = IDLE_W, hh = IDLE_H, rad = IDLE_R;
    private float fromW, fromH, fromR, toW, toH, toR, t = 1f;
    private ValueAnimator morph;
    private boolean paused;

    private float downY;
    private boolean swiped;

    private final Runnable hide = this::finish;
    private final Runnable wave = new Runnable() {
        @Override public void run() {
            if (mode != SHOW || paused || cur == null || !cur.indeterminate) return;
            env.redraw();
            h.postDelayed(this, WAVE_MS);
        }
    };

    NoteIsland(Context ctx, Handler h, Env env) {
        this.ctx = ctx;
        this.h = h;
        this.env = env;
        this.d = ctx.getResources().getDisplayMetrics().density;
        this.maxWdp = ctx.getResources().getDisplayMetrics().widthPixels / d - 16f;
    }

    boolean active() { return mode != NONE; }
    float shapeW() { return w * d; }
    float shapeH() { return hh * d; }

    void release() {
        h.removeCallbacks(hide);
        h.removeCallbacks(wave);
        if (morph != null) morph.cancel();
        NoteState.setListener(null);
    }

    void pause() { paused = true; h.removeCallbacks(wave); }
    void resume() { paused = false; startWave(); if (mode != NONE) env.redraw(); }

    /** Call aayi: notification card turant hata do. */
    void dismissNow() {
        h.removeCallbacks(hide);
        h.removeCallbacks(wave);
        if (morph != null) morph.cancel();
        mode = NONE;
        cur = null;
        w = IDLE_W; hh = IDLE_H; rad = IDLE_R; t = 1f;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float heightOf(NoteInfo.Kind k) {
        switch (k) {
            case MESSAGE: return 78;
            case VIDEO: return 94;
            case MAIL: return 98;
            case SOCIAL: return 76;
            case PROGRESS: return 70;
            default: return 72;
        }
    }

    // ---- NoteState se khabar ----
    @Override public void onNote(NoteInfo n) { h.post(() -> handle(n)); }

    @Override public void onRemoved(String key) {
        h.post(() -> { if (mode == SHOW && cur != null && key.equals(cur.key)) finish(); });
    }

    private void handle(NoteInfo n) {
        if (paused || env.busy() && mode == NONE) return;
        boolean sameCard = mode == SHOW && cur != null && n.key.equals(cur.key);
        if (mode == SHOW && !sameCard) more++;
        else if (mode != SHOW) more = 0;
        cur = n;
        long ms = n.kind == NoteInfo.Kind.VIDEO || n.kind == NoteInfo.Kind.MAIL ? LONG_MS : SHOW_MS;
        if (n.kind == NoteInfo.Kind.PROGRESS && n.progMax > 0 && n.progress >= n.progMax) ms = 2200;
        h.removeCallbacks(hide);
        h.postDelayed(hide, ms);
        if (mode == SHOW) { morphTo(CARD_W, heightOf(n.kind)); env.redraw(); }
        else { fresh = true; mode = SHOW; env.onStart(); morphTo(CARD_W, heightOf(n.kind)); }
        startWave();
    }

    private void startWave() {
        h.removeCallbacks(wave);
        if (mode == SHOW && !paused && cur != null && cur.indeterminate) h.postDelayed(wave, WAVE_MS);
    }

    private void finish() {
        if (mode != SHOW) return;
        h.removeCallbacks(hide);
        h.removeCallbacks(wave);
        mode = CLOSING;
        morphTo(IDLE_W, IDLE_H);
    }

    private void morphTo(float tw, float th) {
        tw = Math.min(tw, maxWdp);
        fromW = w; fromH = hh; fromR = rad;
        toW = tw; toH = th;
        toR = mode == CLOSING ? IDLE_R : Math.min(30f, th / 2f);
        t = 0f;
        if (morph != null) morph.cancel();
        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(toW < fromW || toH < fromH ? 220 : 280);
        morph.setInterpolator(new DecelerateInterpolator(1.6f));
        morph.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            w = lerp(fromW, toW, t);
            hh = lerp(fromH, toH, t);
            rad = lerp(fromR, toR, t);
            env.redraw();
        });
        final float ftw = tw, fth = th;
        morph.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled) return;
                fresh = false;
                if (mode == CLOSING) {
                    mode = NONE;
                    cur = null;
                    w = IDLE_W; hh = IDLE_H; rad = IDLE_R;
                    env.onGone();
                } else {
                    env.resize(Math.round(ftw * d), Math.round(fth * d));
                }
            }
        });
        // window pehle final (bade) size ki, phir shape canvas mein smooth badalta hai
        env.resize(Math.round(Math.max(fromW, tw) * d), Math.round(Math.max(fromH, th) * d));
        morph.setStartDelay(40);
        morph.start();
    }

    // ---- drawing ----
    void draw(Canvas c, float pw, float ph) {
        float r = rad * d;
        rf.set(0, 0, pw, ph);
        bg.setColor(Color.BLACK);
        c.drawRoundRect(rf, r, r, bg);
        if (cur == null || mode == NONE) return;
        float a = mode == CLOSING ? 1f - clamp01(t / 0.4f)
                : fresh ? clamp01((t - 0.4f) / 0.6f) : 1f;
        if (a <= 0.02f) return;
        clip.reset();
        clip.addRoundRect(rf, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        int al = (int) (255 * a);
        switch (cur.kind) {
            case MESSAGE: drawMessage(c, pw, ph, al); break;
            case VIDEO: drawVideo(c, pw, ph, al); break;
            case MAIL: drawMail(c, pw, ph, al); break;
            case SOCIAL: drawSocial(c, pw, ph, al); break;
            case PROGRESS: drawProgress(c, pw, ph, al); break;
            default: drawAlert(c, pw, ph, al); break;
        }
        if (more > 0) chip(c, pw - 18 * d, ph - 14 * d, "+" + more, Theme.BTN, Color.WHITE, al);
        c.restore();
    }

    // ---- har kism ka layout ----
    /** WhatsApp / Telegram / SMS: gol photo, naam, message, naye messages ka count. */
    private void drawMessage(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float cy = ph / 2f, ax = 14 * d + 23 * d;
        avatar(c, n, ax, cy, 23 * d, al);
        badge(c, n, ax + 16 * d, cy + 16 * d, 9 * d, al);
        float tx = 76 * d, mw = pw - tx - 14 * d;
        label(c, "now", pw - 14 * d, 20 * d, 10, Theme.TEXT_DIM, Paint.Align.RIGHT, false, al);
        label(c, fit(n.title, bold(14), mw - 36 * d), tx, 26 * d, 14, Color.WHITE, Paint.Align.LEFT, true, al);
        List<String> ln = wrap(n.text, norm(13), mw - (n.count > 1 ? 30 * d : 0), 2);
        for (int i = 0; i < ln.size(); i++)
            label(c, ln.get(i), tx, (45 + 16 * i) * d, 13, Color.rgb(215, 215, 222), Paint.Align.LEFT, false, al);
        if (n.count > 1) chip(c, pw - 24 * d, cy + 10 * d, String.valueOf(n.count), n.color, Color.WHITE, al);
    }

    /** YouTube: bari thumbnail, channel ka naam, video ka title. */
    private void drawVideo(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float tw = 104 * d, th = 66 * d, tl = 14 * d, tt = (ph - th) / 2f;
        rf.set(tl, tt, tl + tw, tt + th);
        if (n.picture != null) {
            bmpRound(c, n.picture, rf, 10 * d, al);
        } else {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(darken(n.color));
            fill.setAlpha(al);
            c.drawRoundRect(rf, 10 * d, 10 * d, fill);
            fill.setColor(Color.WHITE);
            fill.setAlpha(al);
            gp.reset();
            float cx = tl + tw / 2f, cy = tt + th / 2f;
            gp.moveTo(cx - 8 * d, cy - 11 * d);
            gp.lineTo(cx + 12 * d, cy);
            gp.lineTo(cx - 8 * d, cy + 11 * d);
            gp.close();
            c.drawPath(gp, fill);
        }
        if (n.avatar != null) {
            avatar(c, n, tl + 14 * d, tt + th - 6 * d, 12 * d, al);
        }
        float tx = tl + tw + 12 * d, mw = pw - tx - 14 * d;
        tag(c, n.appName, tx, 16 * d, n.color, al);
        label(c, fit(n.title, bold(13), mw), tx, 44 * d, 13, Color.WHITE, Paint.Align.LEFT, true, al);
        List<String> ln = wrap(n.text, norm(12.5f), mw, 2);
        for (int i = 0; i < ln.size(); i++)
            label(c, ln.get(i), tx, (62 + 15 * i) * d, 12.5f, Color.rgb(205, 205, 214), Paint.Align.LEFT, false, al);
    }

    /** Gmail / Outlook: bhejne wala, subject, snippet. */
    private void drawMail(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float ax = 14 * d + 20 * d, cy = 32 * d;
        if (n.avatar != null) avatar(c, n, ax, cy, 20 * d, al);
        else {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(n.color);
            fill.setAlpha(al);
            c.drawCircle(ax, cy, 20 * d, fill);
            envelope(c, ax, cy, 10 * d, al);
        }
        float tx = 68 * d, mw = pw - tx - 14 * d;
        tag(c, n.appName, pw - 14 * d - txtW(n.appName, 9, true) - 14 * d, 14 * d, n.color, al);
        label(c, fit(n.title, bold(14), mw - 70 * d), tx, 26 * d, 14, Color.WHITE, Paint.Align.LEFT, true, al);
        label(c, fit(n.text, bold(13), mw), tx, 46 * d, 13, Color.rgb(235, 235, 240), Paint.Align.LEFT, true, al);
        String body = n.sub.isEmpty() ? "" : n.sub;
        List<String> ln = wrap(body, norm(12), pw - 28 * d, 2);
        for (int i = 0; i < ln.size(); i++)
            label(c, ln.get(i), 14 * d, (70 + 15 * i) * d, 12, Theme.TEXT_DIM, Paint.Align.LEFT, false, al);
    }

    /** Instagram / Facebook / X: app ka tag, kis ne kya kiya. */
    private void drawSocial(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float cy = ph / 2f, ax = 14 * d + 20 * d;
        avatar(c, n, ax, cy, 20 * d, al);
        badge(c, n, ax + 14 * d, cy + 14 * d, 8 * d, al);
        float tx = 68 * d, mw = pw - tx - 14 * d;
        tag(c, n.appName, tx, 14 * d, n.color, al);
        label(c, fit(n.title, bold(14), mw), tx, 40 * d, 14, Color.WHITE, Paint.Align.LEFT, true, al);
        List<String> ln = wrap(n.text, norm(12.5f), mw, 1);
        for (int i = 0; i < ln.size(); i++)
            label(c, ln.get(i), tx, 58 * d, 12.5f, Color.rgb(205, 205, 214), Paint.Align.LEFT, false, al);
    }

    /** Download / upload: app ka logo, naam, progress bar aur percent. */
    private void drawProgress(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float il = 14 * d, it = 14 * d, is = 40 * d;
        rf.set(il, it, il + is, it + is);
        if (n.appIcon != null) bmpRound(c, n.appIcon, rf, 11 * d, al);
        else {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(n.color);
            fill.setAlpha(al);
            c.drawRoundRect(rf, 11 * d, 11 * d, fill);
        }
        float tx = 64 * d, tr = pw - 14 * d, mw = tr - tx;
        boolean done = !n.indeterminate && n.progMax > 0 && n.progress >= n.progMax;
        float frac = n.progMax > 0 ? clamp01(n.progress / (float) n.progMax) : 0f;
        String pct = n.indeterminate ? "" : Math.round(frac * 100) + "%";
        label(c, fit(n.title, bold(13), mw - 44 * d), tx, 24 * d, 13, Color.WHITE, Paint.Align.LEFT, true, al);
        label(c, done ? "Done" : pct, tr, 24 * d, 12, done ? Theme.GREEN : Theme.TEXT_DIM, Paint.Align.RIGHT, true, al);
        float by = 36 * d, bh = 5 * d;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(60, 60, 66));
        fill.setAlpha(al);
        rf.set(tx, by, tr, by + bh);
        c.drawRoundRect(rf, bh / 2f, bh / 2f, fill);
        fill.setColor(done ? Theme.GREEN : n.color);
        fill.setAlpha(al);
        if (n.indeterminate) {
            float seg = (tr - tx) * 0.3f;
            float p = (System.currentTimeMillis() % 1200) / 1200f;
            float x0 = tx + ((tr - tx) + seg) * p - seg;
            rf.set(Math.max(tx, x0), by, Math.min(tr, x0 + seg), by + bh);
        } else {
            rf.set(tx, by, tx + (tr - tx) * Math.max(0.02f, frac), by + bh);
        }
        if (rf.width() > 0) c.drawRoundRect(rf, bh / 2f, bh / 2f, fill);
        String sub = !n.text.isEmpty() ? n.text : n.appName;
        label(c, fit(sub, norm(11), mw), tx, 58 * d, 11, Theme.TEXT_DIM, Paint.Align.LEFT, false, al);
    }

    /** Baaki sab apps: logo, app ka naam, title, text. */
    private void drawAlert(Canvas c, float pw, float ph, int al) {
        NoteInfo n = cur;
        float cy = ph / 2f, il = 14 * d, is = 40 * d;
        rf.set(il, cy - is / 2f, il + is, cy + is / 2f);
        if (n.appIcon != null) bmpRound(c, n.appIcon, rf, 11 * d, al);
        else if (n.avatar != null) avatar(c, n, il + is / 2f, cy, is / 2f, al);
        else letterBadge(c, n, il + is / 2f, cy, is / 2f, al);
        float tx = 64 * d, mw = pw - tx - 14 * d;
        tag(c, n.appName, tx, 15 * d, n.color, al);
        label(c, fit(n.title, bold(14), mw), tx, 38 * d, 14, Color.WHITE, Paint.Align.LEFT, true, al);
        List<String> ln = wrap(n.text, norm(12.5f), mw, 1);
        for (int i = 0; i < ln.size(); i++)
            label(c, ln.get(i), tx, 56 * d, 12.5f, Color.rgb(205, 205, 214), Paint.Align.LEFT, false, al);
    }

    // ---- chhote drawing helpers ----
    private Paint bold(float sp) { txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(sp * d); return txt; }
    private Paint norm(float sp) { txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(sp * d); return txt; }

    private float txtW(String s, float sp, boolean b) {
        return (b ? bold(sp) : norm(sp)).measureText(s == null ? "" : s);
    }

    private void label(Canvas c, String s, float x, float y, float sp, int color,
                       Paint.Align al, boolean b, int a) {
        if (s == null || s.isEmpty()) return;
        Paint p = b ? bold(sp) : norm(sp);
        p.setTextAlign(al);
        p.setColor(color);
        p.setAlpha((int) (Color.alpha(color) * a / 255f));
        c.drawText(s, x, y, p);
    }

    private String fit(String s, Paint p, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > max) n--;
        return s.substring(0, n) + "…";
    }

    /** Lafzon par tod kar kam se kam lines (aakhri line "..." ke saath). */
    private List<String> wrap(String s, Paint p, float max, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        s = s.replace('\n', ' ').trim();
        int i = 0, len = s.length();
        while (i < len && out.size() < maxLines) {
            boolean last = out.size() == maxLines - 1;
            int n = p.breakText(s, i, len, true, max, null);
            if (i + n >= len) { out.add(s.substring(i)); break; }
            if (last) { out.add(fit(s.substring(i), p, max)); break; }
            int sp = s.lastIndexOf(' ', i + n);
            if (sp > i) n = sp - i;
            out.add(s.substring(i, i + n).trim());
            i += n;
            while (i < len && s.charAt(i) == ' ') i++;
        }
        return out;
    }

    private void tag(Canvas c, String name, float x, float y, int color, int al) {
        String s = name == null ? "" : name.toUpperCase();
        float sp = 9;
        float tw = txtW(s, sp, true);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        fill.setAlpha(al);
        c.drawCircle(x + 3 * d, y - 3 * d, 3 * d, fill);
        label(c, s, x + 10 * d, y, sp, color, Paint.Align.LEFT, true, al);
    }

    private void chip(Canvas c, float cx, float cy, String s, int bgc, int fg, int al) {
        float tw = txtW(s, 11, true);
        float r = Math.max(10 * d, tw / 2f + 6 * d);
        rf.set(cx - r, cy - 10 * d, cx + r, cy + 10 * d);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(bgc);
        fill.setAlpha(al);
        c.drawRoundRect(rf, 10 * d, 10 * d, fill);
        label(c, s, cx, cy + 4 * d, 11, fg, Paint.Align.CENTER, true, al);
    }

    private static int darken(int c) {
        return Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10);
    }

    private void letterBadge(Canvas c, NoteInfo n, float cx, float cy, float r, int al) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(n.color);
        fill.setAlpha(al);
        c.drawCircle(cx, cy, r, fill);
        String s = !n.title.isEmpty() ? n.title.substring(0, 1)
                : n.appName != null && !n.appName.isEmpty() ? n.appName.substring(0, 1) : "?";
        label(c, s.toUpperCase(), cx, cy + r * 0.34f, r * 0.95f / d, Color.WHITE, Paint.Align.CENTER, true, al);
    }

    /** Gol photo (contact), na ho to rang + pehla letter. */
    private void avatar(Canvas c, NoteInfo n, float cx, float cy, float r, int al) {
        if (n.avatar == null) { letterBadge(c, n, cx, cy, r, al); return; }
        rf.set(cx - r, cy - r, cx + r, cy + r);
        bmpRound(c, n.avatar, rf, r, al);
    }

    /** Chhota app logo, kinare par kala ring ke saath. */
    private void badge(Canvas c, NoteInfo n, float cx, float cy, float r, int al) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.BLACK);
        fill.setAlpha(al);
        c.drawCircle(cx, cy, r + 2 * d, fill);
        if (n.appIcon != null) {
            rf.set(cx - r, cy - r, cx + r, cy + r);
            bmpRound(c, n.appIcon, rf, r, al);
        } else {
            fill.setColor(n.color);
            fill.setAlpha(al);
            c.drawCircle(cx, cy, r, fill);
        }
    }

    private void bmpRound(Canvas c, Bitmap b, RectF dst, float rr, int al) {
        BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float s = Math.max(dst.width() / b.getWidth(), dst.height() / b.getHeight());
        mx.reset();
        mx.setScale(s, s);
        mx.postTranslate(dst.left + (dst.width() - b.getWidth() * s) / 2f,
                dst.top + (dst.height() - b.getHeight() * s) / 2f);
        sh.setLocalMatrix(mx);
        bmp.setShader(sh);
        bmp.setAlpha(al);
        c.drawRoundRect(dst, rr, rr, bmp);
        bmp.setShader(null);
    }

    private void envelope(Canvas c, float cx, float cy, float r, int al) {
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.8f * d);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.WHITE);
        fill.setAlpha(al);
        rf.set(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.7f);
        c.drawRoundRect(rf, 2 * d, 2 * d, fill);
        gp.reset();
        gp.moveTo(cx - r, cy - r * 0.7f);
        gp.lineTo(cx, cy + r * 0.05f);
        gp.lineTo(cx + r, cy - r * 0.7f);
        c.drawPath(gp, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    // ---- touch: tap = app kholo, upar swipe = band, bahar tap = band ----
    boolean touch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
                finish();
                return true;
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                swiped = false;
                h.removeCallbacks(hide);          // ungli neeche: timer ruk jata hai
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!swiped && downY - e.getY() > 28 * d) {
                    swiped = true;
                    env.haptic(HapticFeedbackConstants.CLOCK_TICK);
                    finish();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (swiped || mode != SHOW) return true;
                if (Math.abs(e.getY() - downY) <= ViewConfiguration.get(ctx).getScaledTouchSlop() * 2) {
                    env.haptic(HapticFeedbackConstants.CLOCK_TICK);
                    open();
                } else {
                    h.postDelayed(hide, 2000);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mode == SHOW) h.postDelayed(hide, 2000);
                return true;
            default:
                return true;
        }
    }

    private void open() {
        NoteInfo n = cur;
        finish();
        if (n == null) return;
        try {
            if (n.content != null) { n.content.send(); return; }
        } catch (Exception ignored) { }
        Intent li = ctx.getPackageManager().getLaunchIntentForPackage(n.pkg);
        if (li != null) env.launch(li);
    }
}
