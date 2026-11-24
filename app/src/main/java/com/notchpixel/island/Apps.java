package com.notchpixel.island;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** Chune hue apps ka naam + asli icon. Jo app install nahi, wo chhod di jaati hai. */
final class Apps {
    private Apps() { }

    static final class Item {
        final String pkg, label;
        final Drawable icon;
        Item(String pkg, String label, Drawable icon) {
            this.pkg = pkg; this.label = label; this.icon = icon;
        }
    }

    static List<Item> load(Context ctx, List<String> pkgs, int max) {
        PackageManager pm = ctx.getPackageManager();
        List<Item> out = new ArrayList<>();
        for (String p : pkgs) {
            if (out.size() >= max) break;
            try {
                if (pm.getLaunchIntentForPackage(p) == null) continue;
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0));
                out.add(new Item(p, label.toString(), pm.getApplicationIcon(p)));
            } catch (Exception ignored) { }
        }
        return out;
    }
}
