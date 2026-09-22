package com.example.eisenhowertodo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ViewUtils {
    private ViewUtils() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedWithStroke(int color, int strokeColor, float radiusDp, Context context) {
        return roundedWithStroke(color, strokeColor, 1f, radiusDp, context);
    }

    static GradientDrawable roundedWithStroke(int color, int strokeColor, float strokeDp,
                                               float radiusDp, Context context) {
        GradientDrawable drawable = rounded(color, radiusDp, context);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    static void setPadding(View view, Context context, int horizontalDp, int verticalDp) {
        int horizontal = dp(context, horizontalDp);
        int vertical = dp(context, verticalDp);
        view.setPadding(horizontal, vertical, horizontal, vertical);
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    static String formatDueTime(long dueAt) {
        if (dueAt <= 0L) return "";
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(dueAt));
    }
}
