package com.notchpixel.island;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MIUI (Xiaomi / Redmi / POCO) setup guide. MIUI background apps ko jaldi band kar deta hai,
 * to Island aur call detection chalti rakhne ke liye yeh settings ek baar karni hoti hain.
 * Har step ka "Kholo" button seedha wahi screen kholta hai (na mile to App info).
 */
public class GuideActivity extends Activity {

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(12, 12, 14));
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        sv.addView(root);

        root.addView(text("MIUI / Battery guide", 24, Color.WHITE, true));
        TextView intro = text("MIUI background apps ko jaldi band kar deta hai. "
                + "Island pill ghayab ho ya call detect na ho to yeh 6 cheezein ek baar kar do.",
                14, Theme.TEXT_DIM, false);
        intro.setPadding(0, dp(8), 0, dp(18));
        root.addView(intro);

        String pkg = getPackageName();

        step(root, "1. Autostart ON",
                "Security > Permissions > Autostart mein NotchPixel Island ko ON karo.",
                new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")));

        step(root, "2. Battery saver: No restrictions",
                "App info > Battery saver > \"No restrictions\". Warna MIUI Island ko so jane par band kar deta hai.",
                new Intent().setComponent(new ComponentName("com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                        .putExtra("package_name", pkg)
                        .putExtra("package_label", "NotchPixel Island"),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));

        step(root, "3. Pop-up windows allow",
                "Other permissions mein \"Display pop-up windows while running in the background\" "
                        + "aur \"Show on Lock screen\" ON karo.",
                new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setComponent(new ComponentName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                        .putExtra("extra_pkgname", pkg));

        step(root, "4. Display over other apps",
                "Island ko screen ke upar dikhne ke liye. Pehle se ON hai to dobara kuch nahi karna.",
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg)));

        step(root, "5. Notification access (calls ke liye)",
                "NotchPixel Island ko ON karo. Island isi se WhatsApp / Instagram wagera ki call dekhta hai.",
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));

        step(root, "6. Recents mein lock karo",
                "Recent apps kholo, NotchPixel Island ke card ko neeche dabao (ya long-press) aur Lock ka "
                        + "nishan lagao. Phir \"Clear all\" se bhi band nahi hoga.",
                (Intent[]) null);

        root.addView(text("Phir bhi pill ghayab ho jaye to app kholo aur Start Island dobara dabao.",
                13, Theme.TEXT_DIM, false));
        setContentView(sv);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private void step(LinearLayout root, String title, String desc, Intent... tries) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 28, 32));
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        card.addView(text(title, 16, Color.WHITE, true));
        TextView d = text(desc, 13, Theme.TEXT_DIM, false);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);

        if (tries != null) {
            Button btn = new Button(this);
            btn.setText("Kholo");
            btn.setAllCaps(false);
            btn.setTextColor(Color.WHITE);
            GradientDrawable bb = new GradientDrawable();
            bb.setColor(Theme.BTN);
            bb.setCornerRadius(dp(20));
            btn.setBackground(bb);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            bl.topMargin = dp(10);
            bl.gravity = Gravity.START;
            btn.setPadding(dp(22), 0, dp(22), 0);
            btn.setLayoutParams(bl);
            btn.setOnClickListener(v -> open(tries));
            card.addView(btn);
        }
        root.addView(card);
    }

    /** Pehla jo khul jaye; kuch na khule to App info. */
    private void open(Intent[] tries) {
        for (Intent in : tries) {
            try {
                startActivity(in);
                return;
            } catch (Exception ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "Yahan se Permissions / Battery dhundo", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) { }
    }
}
