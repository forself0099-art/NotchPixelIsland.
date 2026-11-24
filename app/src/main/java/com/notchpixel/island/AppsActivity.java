package com.notchpixel.island;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Island ke Apps tab ke liye apps chunne ki screen (zyada se zyada 8). */
public class AppsActivity extends Activity {
    private static final int MAX_APPS = 8;
    private Store store;
    private final Set<String> picked = new LinkedHashSet<>();

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        picked.addAll(store.apps());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(40));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("Apps chuno (max " + MAX_APPS + ")");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(all, (x, y) -> x.loadLabel(pm).toString()
                .compareToIgnoreCase(y.loadLabel(pm).toString()));

        Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo ri : all) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(ri.loadLabel(pm));
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(16);
            cb.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
            Drawable ic = ri.loadIcon(pm);
            ic.setBounds(0, 0, dp(36), dp(36));
            cb.setCompoundDrawables(ic, null, null, null);
            cb.setCompoundDrawablePadding(dp(12));
            cb.setChecked(picked.contains(pkg));
            cb.setOnCheckedChangeListener((v, on) -> {
                if (on) {
                    if (picked.size() >= MAX_APPS) {
                        v.setChecked(false);
                        Toast.makeText(this, "Zyada se zyada " + MAX_APPS + " apps",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    picked.add(pkg);
                } else {
                    picked.remove(pkg);
                }
                store.saveApps(new ArrayList<>(picked));
            });
            root.addView(cb);
        }
    }
}
