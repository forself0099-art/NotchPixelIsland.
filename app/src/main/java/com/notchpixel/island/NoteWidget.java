package com.notchpixel.island;

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
import android.view.HapticFeedbackConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications widget (island ka 4th tab). Har app ki notifications ek stack mein; kinare par
 * chevron dabao to us app ki saari notifications alag alag khul jati hain, dobara dabao to wapas
 * ek stack. Neeche scroll hota hai.
 */
final class NoteWidget {

    interface Env {
        void redraw();
        void launch(Intent in);
        void haptic(int constant);
        /** Widget ki munasib height (dp) badli: island smooth size badle. */
        void heightChanged(float dp);
    }

    static final float MAX_H = 340, MIN_H = 112;
    private static final float TOP = 40, PAD = 10, CARD = 64, HEAD = 46, ITEM = 54;
    private static final int T_CLEAR = 0, T_CARD = 1, T_CHEV = 2, T_HEAD = 3, T_ITEM = 4;

    private static final class Row {
        int type;
        final RectF r = new RectF();
        List<NoteInfo> g;
        NoteInfo n;
    }

    private final Context ctx;
    private final Env env;
    private final float d;
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path gp = new Path();
    private final Matrix mx = new Matrix();
    private final List<Row> rows = new ArrayList<>();
    private float scroll, contentH, viewW;

    NoteWidget(Context ctx, Env env) {
        this.ctx = ctx;
        this.env = env;
        this.d = ctx.getResources().getDisplayMetrics().density;
        viewW = 340 * d;
        build(viewW);
    }

    /** Notifications badli ya group khula/band hua: height dobara nikalo. */
    void refresh() {
        build(viewW);
        env.heightChanged(targetH());
        env.redraw();
    }

    float targetH() {
        float full = TOP + contentH + 8;
        return Math.max(MIN_H, Math.min(MAX_H, full / d));
    }

    private float maxScroll(float viewHpx) { return Math.max(0f, (TOP + contentH + 8) * d - viewHpx); }

    void scrollBy(float dy, float viewHpx) {
        scroll = Math.max(0f, Math.min(maxScroll(viewHpx), scroll + dy));
        env.redraw();
    }

    // ---- layout (dp -> px yahin) ----
    private void build(float w) {
        viewW = w;
        rows.clear();
        float y = 0;                                        // content coordinates (px), TOP ke baad se
        List<List<NoteInfo>> groups = NoteCenter.groups();
        for (List<NoteInfo> g : groups) {
            boolean open = g.size() > 1 && NoteCenter.open.contains(g.get(0).pkg);
            if (!open) {
                int ghosts = g.size() > 2 ? 2 : g.size() > 1 ? 1 : 0;
                Row c = new Row();
                c.type = T_CARD; c.g = g; c.n = g.get(0);
                c.r.set(PAD * d, y, w - PAD * d, y + CARD * d);
                rows.add(c);
                if (g.size() > 1) {
                    Row ch = new Row();
                    ch.type = T_CHEV; ch.g = g;
                    ch.r.set(w - PAD * d - 52 * d, y, w - PAD * d, y + CARD * d);
                    rows.add(ch);
                }
                y += (CARD + ghosts * 5 + 8) * d;
            } else {
                float hgt = HEAD + ITEM * g.size() + 6;
                Row hd = new Row();
                hd.type = T_HEAD; hd.g = g; hd.n = g.get(0);
                hd.r.set(PAD * d, y, w - PAD * d, y + HEAD * d);
                rows.add(hd);
                Row ch = new Row();
                ch.type = T_CHEV; ch.g = g;
                ch.r.set(w - PAD * d - 52 * d, y, w - PAD * d, y + HEAD * d);
                rows.add(ch);
                float iy = y + HEAD * d;
                for (NoteInfo n : g) {
                    Row it = new Row();
                    it.type = T_ITEM; it.g = g; it.n = n;
                    it.r.set(PAD * d, iy, w - PAD * d, iy + ITEM * d);
                    rows.add(it);
                    iy += ITEM * d;
                }
                // poora group card: draw mein hgt se bante hain
                y += (hgt + 8) * d;
            }
        }
        contentH = y / d;
    }

    // ---- drawing ----
    void draw(Canvas c, float w, float hgt, int al) {
        if (Math.abs(w - viewW) > 1f) build(w);
        scroll = Math.max(0f, Math.min(maxScroll(hgt), scroll));
        float top = TOP * d;
        c.save();
        c.clipRect(0, top, w, hgt);
        c.translate(0, top - scroll);
        if (rows.isEmpty()) {
            label(c, "Koi notification nahi", w / 2f, 38 * d, 13, Theme.TEXT_DIM, Paint.Align.CENTER, false, al);
        }
        // group card ka peechla hissa (khule groups ka container, band groups ke ghost layers)
        for (Row r : rows) {
            if (r.type == T_HEAD) {
                rf.set(r.r.left, r.r.top, r.r.right, r.r.top + (HEAD + ITEM * r.g.size() + 6) * d);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Theme.BTN);
                fill.setAlpha(al);
                c.drawRoundRect(rf, 18 * d, 18 * d, fill);
            } else if (r.type == T_CARD && r.g.size() > 1) {
                int gh = r.g.size() > 2 ? 2 : 1;
                for (int i = gh; i >= 1; i--) {
                    rf.set(r.r.left + 8 * d * i, r.r.top + 5 * d * i, r.r.right - 8 * d * i, r.r.bottom + 5 * d * i);
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(i == 1 ? Color.rgb(36, 36, 42) : Color.rgb(28, 28, 33));
                    fill.setAlpha(al);
                    c.drawRoundRect(rf, 18 * d, 18 * d, fill);
                }
            }
        }
        for (Row r : rows) {
            switch (r.type) {
                case T_CARD: drawCard(c, r, w, al); break;
                case T_HEAD: drawHead(c, r, al); break;
                case T_CHEV: drawChevron(c, r, al); break;
                case T_ITEM: drawItem(c, r, al); break;
                default: break;
            }
        }
        c.restore();
        // upar ki patti: title + Clear all (scroll ke upar rehti hai)
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.BLACK);
        fill.setAlpha(al);
        c.drawRect(0, 0, w, top, fill);
        label(c, "Notifications", 20 * d, 26 * d, 14, Color.WHITE, Paint.Align.LEFT, true, al);
        if (!NoteCenter.isEmpty()) {
            float tw = tw("Clear all", 11.5f, true) + 22 * d;
            rf.set(w - 16 * d - tw, 9 * d, w - 16 * d, 31 * d);
            fill.setColor(Theme.BTN);
            fill.setAlpha(al);
            c.drawRoundRect(rf, 11 * d, 11 * d, fill);
            label(c, "Clear all", rf.centerX(), 24.5f * d, 11.5f, Color.rgb(220, 220, 228), Paint.Align.CENTER, true, al);
        }
    }

    private void drawCard(Canvas c, Row r, float w, int al) {
        NoteInfo n = r.n;
        rf.set(r.r);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Theme.BTN);
        fill.setAlpha(al);
        c.drawRoundRect(rf, 18 * d, 18 * d, fill);
        float cy = r.r.centerY();
        icon(c, n, r.r.left + 14 * d + 18 * d, cy, 18 * d, al);
        float tx = r.r.left + 14 * d + 36 * d + 12 * d;
        float rightPad = r.g.size() > 1 ? 54 * d : 14 * d;
        float mw = r.r.right - rightPad - tx;
        String head = n.appName + (r.g.size() > 1 ? "  ·  " + r.g.size() : "");
        label(c, fit(head, bold(10.5f), mw - 36 * d), tx, r.r.top + 20 * d, 10.5f, n.color, Paint.Align.LEFT, true, al);
        label(c, rel(n.when), r.r.right - rightPad + 6 * d, r.r.top + 20 * d, 10, Theme.TEXT_DIM, Paint.Align.RIGHT, false, al);
        label(c, fit(n.title, bold(13), mw), tx, r.r.top + 38 * d, 13, Color.WHITE, Paint.Align.LEFT, true, al);
        label(c, fit(n.text, norm(12), mw), tx, r.r.top + 54 * d, 12, Color.rgb(200, 200, 208), Paint.Align.LEFT, false, al);
    }

    private void drawHead(Canvas c, Row r, int al) {
        NoteInfo n = r.n;
        float cy = r.r.centerY();
        icon(c, n, r.r.left + 14 * d + 14 * d, cy, 14 * d, al);
        float tx = r.r.left + 14 * d + 28 * d + 10 * d;
        label(c, n.appName, tx, cy + 4.5f * d, 13, Color.WHITE, Paint.Align.LEFT, true, al);
        label(c, r.g.size() + " notifications", tx + tw(n.appName, 13, true) + 8 * d, cy + 4 * d, 11,
                Theme.TEXT_DIM, Paint.Align.LEFT, false, al);
    }

    private void drawItem(Canvas c, Row r, int al) {
        NoteInfo n = r.n;
        float cy = r.r.centerY();
        float ax = r.r.left + 14 * d + 17 * d;
        if (n.avatar != null) {
            rf.set(ax - 17 * d, cy - 17 * d, ax + 17 * d, cy + 17 * d);
            bmpRound(c, n.avatar, rf, 17 * d, al);
        } else {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(n.color);
            fill.setAlpha(al);
            c.drawCircle(ax, cy, 17 * d, fill);
            String s = n.title.isEmpty() ? "?" : n.title.substring(0, 1).toUpperCase();
            label(c, s, ax, cy + 5.5f * d, 15, Color.WHITE, Paint.Align.CENTER, true, al);
        }
        float tx = r.r.left + 14 * d + 34 * d + 12 * d, mw = r.r.right - 14 * d - tx - 30 * d;
        label(c, fit(n.title, bold(13), mw), tx, cy - 3 * d, 13, Color.WHITE, Paint.Align.LEFT, true, al);
        label(c, fit(n.text, norm(12), mw), tx, cy + 14 * d, 12, Color.rgb(200, 200, 208), Paint.Align.LEFT, false, al);
        label(c, rel(n.when), r.r.right - 14 * d, cy - 3 * d, 10, Theme.TEXT_DIM, Paint.Align.RIGHT, false, al);
    }

    /** Chevron button: band group par "v" (alag alag kholo), khule par "^" (ek saath karo). */
    private void drawChevron(Canvas c, Row r, int al) {
        boolean open = NoteCenter.open.contains(r.g.get(0).pkg);
        float cx = r.r.right - 22 * d, cy = r.r.centerY();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(62, 62, 70));
        fill.setAlpha(al);
        c.drawCircle(cx, cy, 14 * d, fill);
        float k = open ? -1f : 1f;
        gp.reset();
        gp.moveTo(cx - 5.5f * d, cy - 2.5f * d * k);
        gp.lineTo(cx, cy + 3f * d * k);
        gp.lineTo(cx + 5.5f * d, cy - 2.5f * d * k);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(2.2f * d);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.WHITE);
        fill.setAlpha(al);
        c.drawPath(gp, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    /** App ka asli logo, na ho to rang + letter. */
    private void icon(Canvas c, NoteInfo n, float cx, float cy, float r, int al) {
        rf.set(cx - r, cy - r, cx + r, cy + r);
        if (n.appIcon != null) { bmpRound(c, n.appIcon, rf, r * 0.62f, al); return; }
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(n.color);
        fill.setAlpha(al);
        c.drawRoundRect(rf, r * 0.62f, r * 0.62f, fill);
        String s = n.appName == null || n.appName.isEmpty() ? "?" : n.appName.substring(0, 1).toUpperCase();
        label(c, s, cx, cy + r * 0.34f, r * 0.95f / d, Color.WHITE, Paint.Align.CENTER, true, al);
    }

    // ---- touch ----
    /** Tap: 0 = kuch nahi, 1 = handle hua, 2 = app khuli (island band kar do). */
    int tap(float x, float y, float viewHpx) {
        if (y < TOP * d) {
            if (!NoteCenter.isEmpty() && x > viewW - 120 * d && y > 6 * d) {
                env.haptic(HapticFeedbackConstants.CLOCK_TICK);
                NoteCenter.clear();
                return 1;
            }
            return 0;
        }
        float cy = y - TOP * d + scroll;
        for (int i = rows.size() - 1; i >= 0; i--) {          // chevron pehle (card ke upar hai)
            Row r = rows.get(i);
            if (r.type != T_CHEV || !r.r.contains(x, cy)) continue;
            toggle(r.g.get(0).pkg);
            return 1;
        }
        for (Row r : rows) {
            if (!r.r.contains(x, cy)) continue;
            if (r.type == T_HEAD) { toggle(r.g.get(0).pkg); return 1; }
            if (r.type == T_CARD) { openNote(r.n, false); return 2; }
            if (r.type == T_ITEM) { openNote(r.n, true); return 2; }
        }
        return 0;
    }

    private void toggle(String pkg) {
        env.haptic(HapticFeedbackConstants.CLOCK_TICK);
        if (!NoteCenter.open.remove(pkg)) NoteCenter.open.add(pkg);
        refresh();
    }

    private void openNote(NoteInfo n, boolean removeIt) {
        env.haptic(HapticFeedbackConstants.CLOCK_TICK);
        boolean sent = false;
        try { if (n.content != null) { n.content.send(); sent = true; } } catch (Exception ignored) { }
        if (!sent) {
            Intent li = ctx.getPackageManager().getLaunchIntentForPackage(n.pkg);
            if (li != null) env.launch(li);
        }
        NoteCenter.remove(n.key);
    }

    // ---- helpers ----
    private Paint bold(float sp) { txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(sp * d); return txt; }
    private Paint norm(float sp) { txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(sp * d); return txt; }
    private float tw(String s, float sp, boolean b) { return (b ? bold(sp) : norm(sp)).measureText(s); }

    private void label(Canvas c, String s, float x, float y, float sp, int color, Paint.Align al, boolean b, int a) {
        if (s == null || s.isEmpty()) return;
        Paint p = b ? bold(sp) : norm(sp);
        p.setTextAlign(al);
        p.setColor(color);
        p.setAlpha((int) (Color.alpha(color) * a / 255f));
        c.drawText(s, x, y, p);
    }

    private String fit(String s, Paint p, float max) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        if (p.measureText(s) <= max) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > max) n--;
        return s.substring(0, n) + "…";
    }

    private static String rel(long when) {
        long m = Math.max(0, (System.currentTimeMillis() - when) / 60000);
        if (m < 1) return "now";
        if (m < 60) return m + "m";
        if (m < 1440) return (m / 60) + "h";
        return (m / 1440) + "d";
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
}
