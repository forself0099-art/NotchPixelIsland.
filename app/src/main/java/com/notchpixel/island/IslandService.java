package com.notchpixel.island;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;

/** Foreground service: screen ke top-center par island overlay lagata hai. */
public class IslandService extends Service {
    public static final String ACTION_STOP = "com.notchpixel.island.STOP";
    public static volatile boolean running = false;
    private static final String CHANNEL = "island";

    private WindowManager wm;
    private IslandView view;
    private WindowManager.LayoutParams lp;
    private EdgeSliders edge;
    private android.content.SharedPreferences prefs;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> {
                if (!"edge".equals(key) || edge == null) return;
                if (sp.getBoolean("edge", true)) edge.show(); else edge.hide();
            };

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (view == null) addIsland();
        running = true;
        return START_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Island", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, IslandService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("NotchPixel Island chal raha hai")
                .setContentText("Band karne ke liye Stop dabao")
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void addIsland() {
        wm = getSystemService(WindowManager.class);
        view = new IslandView(this, new IslandView.Host() {
            @Override public void onResize(int w, int h) {
                if (lp == null || view == null) return;
                lp.width = w;
                lp.height = h;
                try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
            }
            @Override public void onStop() {
                stopSelf();
            }
        });
        lp = new WindowManager.LayoutParams(
                view.idleW(), view.idleH(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = view.topGap();
        // Camera cutout ke upar bhi draw karne do
        lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.setTitle("NotchPixelIsland");
        wm.addView(view, lp);

        // kinaare ki patti: right = volume, left = brightness
        edge = new EdgeSliders(this, view);
        prefs = getSharedPreferences("island", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        if (prefs.getBoolean("edge", true)) edge.show();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        if (edge != null) { edge.hide(); edge = null; }
        if (view != null) {
            view.release();
            try { wm.removeView(view); } catch (Exception ignored) { }
            view = null;
        }
        super.onDestroy();
    }
}
