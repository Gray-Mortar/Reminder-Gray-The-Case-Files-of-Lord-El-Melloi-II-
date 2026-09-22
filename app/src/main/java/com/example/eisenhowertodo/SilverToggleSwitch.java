package com.example.eisenhowertodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** A device-independent silver/teal switch matching the approved design. */
final class SilverToggleSwitch extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;

    SilverToggleSwitch(Context context) {
        this(context, null);
    }

    SilverToggleSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setContentDescription("开关");
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        refreshDrawableState();
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        setChecked(!checked);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = ViewUtils.dp(getContext(), 64);
        int desiredHeight = ViewUtils.dp(getContext(), 38);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float outerInset = 1f * density;
        float outerHeight = 37f * density;
        float outerTop = (getHeight() - outerHeight) / 2f;
        RectF outer = new RectF(outerInset, outerTop,
                getWidth() - outerInset, outerTop + outerHeight);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, outer.top, 0, outer.bottom,
                0xFFFAFBFC, 0xFFE1E4E6, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(outer, outerHeight / 2f, outerHeight / 2f, paint);

        float innerInset = 5f * density;
        float trackHeight = 30f * density;
        float top = (getHeight() - trackHeight) / 2f;
        RectF track = new RectF(innerInset, top,
                getWidth() - innerInset, top + trackHeight);
        int trackStart = checked ? 0xFF31BDB5 : 0xFFD8DBDE;
        int trackEnd = checked ? 0xFF20A79F : 0xFFBFC4C8;
        paint.setShader(new LinearGradient(track.left, track.top, track.right, track.bottom,
                trackStart, trackEnd, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, paint);

        float radius = 14f * density;
        float centerX = checked ? track.right - trackHeight / 2f : track.left + trackHeight / 2f;
        paint.setShader(new RadialGradient(centerX - 4f * density,
                track.centerY() - 4f * density, radius * 1.5f,
                new int[] {0xFFFFFFFF, 0xFFF0F2F3, 0xFFCDD1D4},
                new float[] {0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, track.centerY(), radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * density);
        paint.setColor(0xFFB9BEC2);
        canvas.drawCircle(centerX, track.centerY(), radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
