package com.notchpixel.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Handler;

/** Battery, charging aur torch ki halat. Badalne par listener ko batata hai. */
final class SystemState {
    interface Listener { void onChanged(); }

    int battery = -1;
    boolean charging = false;
    boolean torchOn = false;

    private final Context ctx;
    private final Listener listener;
    private CameraManager cam;
    private String torchId;
    private CameraManager.TorchCallback torchCb;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                battery = lvl < 0 ? -1 : Math.round(lvl * 100f / scale);
                int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING
                        || st == BatteryManager.BATTERY_STATUS_FULL;
            }
            listener.onChanged();
        }
    };

    SystemState(Context ctx, Handler h, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        Intent sticky = ctx.registerReceiver(rx, f);
        if (sticky != null) rx.onReceive(ctx, sticky);
        initTorch(h);
    }

    private void initTorch(Handler h) {
        try {
            cam = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cam.getCameraIdList()) {
                CameraCharacteristics cc = cam.getCameraCharacteristics(id);
                Boolean flash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null
                        && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchId = id;
                    break;
                }
            }
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) {
                    if (id.equals(torchId)) { torchOn = enabled; listener.onChanged(); }
                }
            };
            cam.registerTorchCallback(torchCb, h);
        } catch (Exception e) {
            torchId = null;
        }
    }

    void toggleTorch() {
        if (torchId == null) return;
        try { cam.setTorchMode(torchId, !torchOn); } catch (Exception ignored) { }
    }

    void release() {
        try { ctx.unregisterReceiver(rx); } catch (Exception ignored) { }
        try { if (cam != null && torchCb != null) cam.unregisterTorchCallback(torchCb); }
        catch (Exception ignored) { }
    }
}
