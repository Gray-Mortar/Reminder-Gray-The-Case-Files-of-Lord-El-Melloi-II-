package com.example.eisenhowertodo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class QuickAddActivity extends Activity {
    private EditText input;
    private SilverToggleSwitch important;
    private SilverToggleSwitch urgent;
    private Button time;
    private final long[] dueAt = {0L};
    private TaskStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new TaskStore(this);
        setFinishOnTouchOutside(true);
        setContentView(createContent());

        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.BOTTOM;
        window.setAttributes(params);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private LinearLayout createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ViewUtils.dp(this, 20), ViewUtils.dp(this, 18),
                ViewUtils.dp(this, 20), ViewUtils.dp(this, 22));
        root.setBackgroundColor(0xFFF4F6F7);

        TextView title = text("快速添加待办", 21, ThemePalette.TEXT, true);
        root.addView(title);

        input = new EditText(this);
        input.setHint("现在要记下什么？");
        input.setTextSize(18);
        input.setTextColor(ThemePalette.TEXT);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 58));
        inputParams.setMargins(0, ViewUtils.dp(this, 8), 0, ViewUtils.dp(this, 5));
        root.addView(input, inputParams);

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);
        important = new SilverToggleSwitch(this);
        urgent = new SilverToggleSwitch(this);
        options.addView(switchGroup("重要", important),
                new LinearLayout.LayoutParams(ViewUtils.dp(this, 112), ViewUtils.dp(this, 50)));
        options.addView(switchGroup("紧急", urgent),
                new LinearLayout.LayoutParams(ViewUtils.dp(this, 112), ViewUtils.dp(this, 50)));
        time = new Button(this);
        time.setAllCaps(false);
        time.setTextSize(13);
        time.setTextColor(ThemePalette.TEXT_SECONDARY);
        time.setBackground(ViewUtils.roundedWithStroke(
                Color.WHITE, 0xFFD9DEE2, 12, this));
        updateTime();
        time.setOnClickListener(view -> TaskEditorDialog.pickDateTime(
                this, dueAt, this::updateTime));
        time.setOnLongClickListener(view -> {
            dueAt[0] = 0L;
            updateTime();
            return true;
        });
        options.addView(time, new LinearLayout.LayoutParams(
                0, ViewUtils.dp(this, 44), 1));
        root.addView(options);

        Button save = new Button(this);
        save.setText("添加并返回桌面");
        save.setTextColor(Color.WHITE);
        save.setTextSize(16);
        save.setAllCaps(false);
        save.setBackground(ViewUtils.rounded(ThemePalette.ACCENT, 12, this));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 50));
        saveParams.setMargins(0, ViewUtils.dp(this, 10), 0, 0);
        root.addView(save, saveParams);

        save.setOnClickListener(view -> save());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });
        return root;
    }

    private LinearLayout switchGroup(String label, SilverToggleSwitch toggle) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(text(label, 14, ThemePalette.TEXT, false));
        group.addView(toggle, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 68), ViewUtils.dp(this, 46)));
        return group;
    }

    private void updateTime() {
        time.setText(dueAt[0] > 0L
                ? "◷ " + ViewUtils.formatDueTime(dueAt[0])
                : "◷ 时间（可选）");
    }

    private void save() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            input.setError("请输入待办内容");
            return;
        }
        int quadrant = ThemePalette.quadrantFor(important.isChecked(), urgent.isChecked());
        store.add(value, quadrant, dueAt[0]);
        Toast.makeText(this, "已添加到“" + MainActivity.TITLES[quadrant] + "”",
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(color);
        if (bold) textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        return textView;
    }
}
