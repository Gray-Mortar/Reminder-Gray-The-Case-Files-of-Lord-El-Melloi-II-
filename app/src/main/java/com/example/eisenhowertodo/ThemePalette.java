package com.example.eisenhowertodo;

import android.graphics.Color;

final class ThemePalette {
    static final int TEXT = Color.rgb(41, 56, 71);
    static final int TEXT_SECONDARY = Color.rgb(95, 109, 123);
    static final int ACCENT = Color.rgb(40, 175, 168);
    static final int SILVER = Color.rgb(183, 188, 193);
    static final int SILVER_THUMB = Color.rgb(244, 246, 247);

    static final int[] QUADRANT = {
            Color.rgb(166, 95, 105),
            Color.rgb(75, 156, 160),
            Color.rgb(93, 131, 176),
            Color.rgb(119, 122, 128)
    };

    private ThemePalette() {}

    static int quadrantFor(boolean important, boolean urgent) {
        if (important && urgent) return 0;
        if (important) return 1;
        if (urgent) return 2;
        return 3;
    }

    static boolean isImportant(int quadrant) {
        return quadrant == 0 || quadrant == 1;
    }

    static boolean isUrgent(int quadrant) {
        return quadrant == 0 || quadrant == 2;
    }
}
