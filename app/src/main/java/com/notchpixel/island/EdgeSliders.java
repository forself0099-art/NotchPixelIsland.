package com.notchpixel.island;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Screen ke kinaaron par patli overlay patti: right par upar/neeche slide = volume,
 * left par = brightness. Slide karte waqt island khud failkar icon + bar + number dikhata hai.
 */
final class EdgeSliders {
    private final Context ctx;
    private final WindowManager wm;
    private final float d;
    private EdgeView left, right;
    private final IslandView island;      // level (icon + bar + number) island ke andar dikhta hai

    EdgeSliders(Context ctx, IslandView island) {
        this.ctx = ctx;
        this.island = island;
        wm = ctx.getSystemService(WindowManager.class);
        d = ctx.getResources().getDisplayMetrics().density;
    }

    void show() {
        if (left != null) return;
        left = new EdgeView(false);
        right = new EdgeView(true);
        left.attach();
        right.attach();
    }

    void hide() {
        if (left == null) return;
        left.detach();
        right.detach();
        left = right = null;
    }

    // ---------------- patli touch patti ----------------
    private final class EdgeView extends View {
        private final boolean isRight;
        private final int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        private final AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        private boolean active;
        private float startY, startF, f;
        private int lastTick, lastApplied = -1;

        EdgeView(boolean isRight) { super(ctx); this.isRight = isRight; }

        void attach() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    Math.round(16 * d), Math.round(screenH * 0.42f),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | (isRight ? Gravity.END : Gravity.START);
            lp.x = 0;
            lp.y = Math.round(screenH * 0.28f);
            lp.setTitle(isRight ? "NotchEdgeR" : "NotchEdgeL");
            try { wm.addView(this, lp); } catch (Exception ignored) { }
        }

        void detach() {
            try { wm.removeView(this); } catch (Exception ignored) { }
        }

        private float readLevel() {
            if (isRight) {
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                return max <= 0 ? 0f : audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
            }
            try {
                return Settings.System.getInt(ctx.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
            } catch (Exception e) { return 0.5f; }
        }

        private void apply(float v) {
            if (isRight) {
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int n = Math.round(v * max);
                if (n == lastApplied) return;
                lastApplied = n;
                try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0); } catch (Exception ignored) { }
            } else {
                int n = Math.max(1, Math.round(v * 255));
                if (n == lastApplied) return;
                lastApplied = n;
                try {
                    Settings.System.putInt(ctx.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, n);
                } catch (Exception ignored) { }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!isRight && !Settings.System.canWrite(ctx)) {     // ek baar ki permission
                        try {
                            ctx.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:" + ctx.getPackageName()))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception ignored) { }
                        return true;
                    }
                    active = true;
                    startY = e.getRawY();
                    startF = f = readLevel();
                    lastTick = (int) (f * 10);
                    lastApplied = -1;
                    island.levelShow(isRight, f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!active) return true;
                    f = Math.max(0f, Math.min(1f, startF + (startY - e.getRawY()) / (screenH * 0.34f)));
                    apply(f);
                    int t = (int) (f * 10);
                    if (t != lastTick) {
                        lastTick = t;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    island.levelUpdate(f);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!active) return true;
                    active = false;
                    island.levelHideSoon();
                    return true;
            }
            return true;
        }
    }
}
