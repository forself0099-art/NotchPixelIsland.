package com.notchpixel.island;

import android.graphics.Color;

/** Saare rang ek jagah. */
final class Theme {
    private Theme() { }
    static final int GREEN = Color.rgb(48, 209, 88);
    static final int RED = Color.rgb(255, 69, 58);
    static final int YELLOW = Color.rgb(255, 214, 10);
    static final int DOT_GRAY = Color.rgb(120, 120, 128);
    static final int TEXT_DIM = Color.rgb(160, 160, 170);
    static final int OUTLINE = Color.rgb(110, 110, 118);
    static final int BTN = Color.rgb(44, 44, 50);
    static final int BTN_CLOSE = Color.rgb(70, 30, 30);

    /** Site / app tiles ke brand jaise rang (naam se chunte hain). */
    static final int[] TILE = {
        Color.rgb(66, 133, 244), Color.rgb(234, 67, 53), Color.rgb(37, 211, 102),
        Color.rgb(255, 149, 0), Color.rgb(175, 82, 222), Color.rgb(0, 150, 136),
        Color.rgb(233, 30, 99), Color.rgb(90, 200, 250)
    };
    static int tile(String name) { return TILE[Math.abs(name.hashCode()) % TILE.length]; }
}
