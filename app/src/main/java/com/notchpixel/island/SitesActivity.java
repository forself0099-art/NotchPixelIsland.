package com.notchpixel.island;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Sites manage karne ki screen: list, delete, naya add. Code se bani, koi XML nahi. */
public class SitesActivity extends Activity {
    private static final int MAX_SITES = 8;
    private Store store;
    private LinearLayout root;
    private EditText etName, etUrl;
    private List<Store.Site> list;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(40));
        sv.addView(root);
        setContentView(sv);

        etName = field("Naam (jaise YouTube)", InputType.TYPE_CLASS_TEXT);
        etUrl = field("Link (jaise youtube.com)", InputType.TYPE_TEXT_VARIATION_URI);
        rebuild();
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Theme.OUTLINE);
        return e;
    }

    private TextView label(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private void rebuild() {
        root.removeAllViews();
        list = store.sites();
        TextView title = label("Sites manage karo", 22, true, Color.WHITE);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            Store.Site s = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView t = label(s.name + "\n" + s.host(), 15, false, Color.WHITE);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button del = new Button(this);
            del.setText("Delete");
            del.setOnClickListener(v -> {
                list.remove(idx);
                store.saveSites(list);
                rebuild();
            });
            row.addView(del);
            root.addView(row);
        }

        TextView add = label("Nayi site", 17, true, Color.WHITE);
        add.setPadding(0, dp(20), 0, 0);
        root.addView(add);
        root.addView(etName);
        root.addView(etUrl);
        Button btn = new Button(this);
        btn.setText("Add");
        btn.setOnClickListener(v -> addSite());
        root.addView(btn);
    }

    private void addSite() {
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        if (name.isEmpty() || url.isEmpty()) {
            Toast.makeText(this, "Naam aur link dono likho", Toast.LENGTH_SHORT).show();
            return;
        }
        if (list.size() >= MAX_SITES) {
            Toast.makeText(this, "Zyada se zyada " + MAX_SITES + " sites", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.contains("://")) url = "https://" + url;
        list.add(new Store.Site(name, url));
        store.saveSites(list);
        etName.setText("");
        etUrl.setText("");
        rebuild();
    }
}
