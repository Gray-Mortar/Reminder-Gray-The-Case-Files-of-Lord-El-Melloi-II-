package com.example.eisenhowertodo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Displays the approved switch artwork directly, without redrawing its finish. */
final class SilverToggleSwitch extends View {
    private static Bitmap onArtwork;
    private static Bitmap offArtwork;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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
        int desiredWidth = ViewUtils.dp(getContext(), 68);
        int desiredHeight = ViewUtils.dp(getContext(), 42);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    private static synchronized Bitmap artwork(Context context, boolean checked) {
        if (onArtwork == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            onArtwork = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.switch_on_reference, options);
            offArtwork = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.switch_off_reference, options);
        }
        return checked ? onArtwork : offArtwork;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap image = artwork(getContext(), checked);
        float width = Math.min(getWidth(), getHeight() * image.getWidth() / (float) image.getHeight());
        float height = width * image.getHeight() / image.getWidth();
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        canvas.drawBitmap(image, null, new RectF(left, top, left + width, top + height), paint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
