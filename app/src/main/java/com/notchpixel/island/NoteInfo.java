package com.notchpixel.island;

import android.app.PendingIntent;
import android.graphics.Bitmap;

/** Ek aam notification (call nahi) ki jaankari: island ke layout ke liye. */
final class NoteInfo {
    enum Kind { MESSAGE, VIDEO, MAIL, SOCIAL, PROGRESS, ALERT }

    String key, pkg, appName, title = "", text = "", sub = "";
    Kind kind = Kind.ALERT;
    int color;                 // app ka rang
    int count;                 // kitne naye messages (0/1 = ek)
    int progress, progMax;     // PROGRESS kind
    boolean indeterminate;
    PendingIntent content;     // tap par yahi khulta hai
    Bitmap avatar;             // contact / channel ki tasveer (large icon)
    Bitmap picture;            // big picture (video thumbnail waghera)
    Bitmap appIcon;            // app ka asli logo
    long when = System.currentTimeMillis();
}
