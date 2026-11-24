package com.notchpixel.island;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Chhota control screen: overlay permission do, Island start/stop karo. */
public class MainActivity extends Activity {
    private TextView status;
    private boolean setupRunning;
    private int demoIdx;
    private int lastAsked = -1;
    private Button permBtn, toggleBtn, briBtn, callBtn, testBtn, guideBtn, edgeBtn, noteBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(12, 12, 14));
        int pad = (int) (28 * d);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("NotchPixel Island");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.rgb(170, 170, 180));
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, (int) (14 * d), 0, (int) (28 * d));
        root.addView(status);

        permBtn = makeBtn("Allow all + Start (ek click)", d);
        permBtn.setOnClickListener(v -> {
            if (IslandService.running) {
                startService(new Intent(this, IslandService.class)
                        .setAction(IslandService.ACTION_STOP));
                IslandService.running = false;
                refresh();
                return;
            }
            setupRunning = true;
            lastAsked = -1;
            runSetup();
        });
        root.addView(permBtn);
        toggleBtn = permBtn;      // purane code ke liye
        briBtn = callBtn = permBtn;

        testBtn = makeBtn("Test: nakli aati call", d);
        testBtn.setOnClickListener(v -> {
            if (!IslandService.running) {
                android.widget.Toast.makeText(this, "Pehle Island start karo", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            CallInfo ci = new CallInfo();
            ci.key = "demo";
            ci.pkg = "com.whatsapp";
            ci.appName = "WhatsApp";
            ci.caller = "Ali Raza";
            ci.color = Color.rgb(37, 211, 102);
            ci.state = CallInfo.State.INCOMING;
            ci.demo = true;
            CallState.post(ci);
        });
        root.addView(testBtn);

        guideBtn = makeBtn("MIUI / battery guide", d);
        guideBtn.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        root.addView(guideBtn);

        edgeBtn = makeBtn("", d);
        edgeBtn.setOnClickListener(v -> {
            Store st = new Store(this);
            st.setEdge(!st.edgeOn());
            edgeBtn.setText(edgeLabel());
        });
        edgeBtn.setText(edgeLabel());
        root.addView(edgeBtn);

        Button demoNote = makeBtn("Test: nakli notification (har tap par naya layout)", d);
        demoNote.setOnClickListener(v -> {
            if (!IslandService.running) {
                android.widget.Toast.makeText(this, "Pehle Island start karo", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            NoteInfo n = new NoteInfo();
            n.key = "demo" + System.nanoTime();
            n.pkg = getPackageName();
            switch (demoIdx++ % 6) {
                case 0: n.kind = NoteInfo.Kind.MESSAGE; n.appName = "WhatsApp"; n.color = Color.rgb(37, 211, 102);
                    n.title = "Ali Khan"; n.text = "Bhai kal meeting ka time kya hai? Mujhe confirm kar dena"; n.count = 3; break;
                case 1: n.kind = NoteInfo.Kind.VIDEO; n.appName = "YouTube"; n.color = Color.rgb(255, 48, 48);
                    n.title = "Trading Mentor"; n.text = "XAUUSD liquidity sweep explained: ICT concepts for beginners"; break;
                case 2: n.kind = NoteInfo.Kind.MAIL; n.appName = "Gmail"; n.color = Color.rgb(234, 67, 53);
                    n.title = "TradingView"; n.text = "Alert triggered: XAUUSD crossed 2650";
                    n.sub = "Your alert on XAUUSD was triggered at 2650.10 on the 15 minute chart."; break;
                case 3: n.kind = NoteInfo.Kind.SOCIAL; n.appName = "Instagram"; n.color = Color.rgb(225, 48, 108);
                    n.title = "sara.khan"; n.text = "liked your photo"; break;
                case 4: n.kind = NoteInfo.Kind.PROGRESS; n.appName = "Download"; n.color = Color.rgb(66, 133, 244);
                    n.title = "NotchPixel.apk"; n.text = "12.4 MB of 20 MB"; n.progMax = 100; n.progress = 62; break;
                default: n.kind = NoteInfo.Kind.ALERT; n.appName = "Calendar"; n.color = Color.rgb(255, 149, 0);
                    n.title = "Meeting in 10 minutes"; n.text = "London session review"; break;
            }
            n.pkg = "demo." + n.appName;
            if (n.kind == NoteInfo.Kind.MESSAGE) {              // group dikhane ke liye 2 aur messages
                String[][] extra = {{"Sara", "Kal milte hain?"}, {"Trading Group", "XAUUSD buy setup mil gaya"}};
                for (String[] x : extra) {
                    NoteInfo o = new NoteInfo();
                    o.key = "demo" + System.nanoTime() + x[0];
                    o.pkg = n.pkg; o.appName = n.appName; o.color = n.color;
                    o.kind = n.kind; o.title = x[0]; o.text = x[1];
                    NoteCenter.add(o);
                }
            }
            NoteCenter.add(n);
            NoteState.post(n);
        });
        root.addView(demoNote);

        noteBtn = makeBtn("", d);
        noteBtn.setOnClickListener(v -> {
            Store st = new Store(this);
            st.setNotes(!st.notesOn());
            noteBtn.setText(noteLabel());
        });
        noteBtn.setText(noteLabel());
        root.addView(noteBtn);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private String noteLabel() {
        return "Notifications island: " + (new Store(this).notesOn() ? "ON" : "OFF")
                + " (messages, YouTube, mail...). Tap = badlo";
    }

    private String edgeLabel() {
        return "Edge sliders: " + (new Store(this).edgeOn() ? "ON" : "OFF")
                + " (right = volume, left = brightness). Tap = badlo";
    }

    private Button makeBtn(String text, float d) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(38, 38, 44));
        bg.setCornerRadius(28 * d);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (56 * d));
        lp.bottomMargin = (int) (12 * d);
        btn.setLayoutParams(lp);
        return btn;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (setupRunning) runSetup();
        refresh();
    }

    private boolean listenerOk() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean dndOk() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        return nm == null || nm.isNotificationPolicyAccessGranted();
    }

    /** Agli missing permission ka number (0 overlay, 1 brightness, 2 call, 3 DND) ya -1 sab mil gayin. */
    private int nextMissing() {
        if (!Settings.canDrawOverlays(this)) return 0;
        if (!Settings.System.canWrite(this)) return 1;
        if (!listenerOk()) return 2;
        if (!dndOk()) return 3;
        return -1;
    }

    private void runSetup() {
        int n = nextMissing();
        if (n == lastAsked && n >= 0) {            // user ne diye bina wapas aa gaya: loop band
            setupRunning = false;
            android.widget.Toast.makeText(this, "Permission nahi mili, dobara button dabao",
                    android.widget.Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        if (n < 0) {
            setupRunning = false;
            if (!IslandService.running) {
                startForegroundService(new Intent(this, IslandService.class));
                IslandService.running = true;
            }
            refresh();
            return;
        }
        lastAsked = n;
        Intent in;
        String pkg = "package:" + getPackageName();
        if (n == 0) in = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg));
        else if (n == 1) in = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse(pkg));
        else if (n == 2) in = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        else in = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        String[] msg = {"Display over other apps: ON karo", "Modify system settings: ON karo",
                "NotchPixel ko notification access do", "NotchPixel ko Do Not Disturb access do"};
        android.widget.Toast.makeText(this, msg[n], android.widget.Toast.LENGTH_LONG).show();
        try { startActivity(in); } catch (Exception e) { setupRunning = false; }
    }

    private void refresh() {
        boolean all = nextMissing() < 0;
        permBtn.setText(IslandService.running ? "Stop Island"
                : all ? "Start Island" : "Allow all + Start (ek click)");
        status.setText(IslandService.running ? "Island chal raha hai. Screen ke upar pill ko tap karo."
                : all ? "Sab permissions mil gayin. Start dabao."
                : "Ek button dabao: baari baari permissions khulengi, bas ON karke wapas aao.");
    }
}
