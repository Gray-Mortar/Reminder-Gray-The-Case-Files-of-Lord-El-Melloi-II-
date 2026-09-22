package com.example.eisenhowertodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

final class TaskEditorDialog {
    interface Callback {
        void onChanged();
    }

    private TaskEditorDialog() {}

    static void show(Activity activity, TaskStore store, Task task, int defaultQuadrant,
                     Callback callback) {
        boolean editing = task != null;
        int quadrant = editing ? task.quadrant : defaultQuadrant;
        long[] dueAt = {editing ? task.dueAt : 0L};

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = ViewUtils.dp(activity, 22);
        content.setPadding(horizontal, ViewUtils.dp(activity, 8), horizontal,
                ViewUtils.dp(activity, 2));

        EditText input = new EditText(activity);
        input.setHint("待办内容");
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setText(editing ? task.text : "");
        input.setTextColor(ThemePalette.TEXT);
        input.setTextSize(17);
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout priority = new LinearLayout(activity);
        priority.setGravity(Gravity.CENTER_VERTICAL);
        priority.setPadding(0, ViewUtils.dp(activity, 8), 0, ViewUtils.dp(activity, 4));

        SilverToggleSwitch important = createSwitch(activity, "重要", ThemePalette.isImportant(quadrant));
        SilverToggleSwitch urgent = createSwitch(activity, "紧急", ThemePalette.isUrgent(quadrant));
        priority.addView(labeledSwitch(activity, "重要", important),
                new LinearLayout.LayoutParams(0, ViewUtils.dp(activity, 48), 1));
        priority.addView(labeledSwitch(activity, "紧急", urgent),
                new LinearLayout.LayoutParams(0, ViewUtils.dp(activity, 48), 1));
        content.addView(priority);

        LinearLayout timeRow = new LinearLayout(activity);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        Button time = new Button(activity);
        time.setAllCaps(false);
        time.setTextSize(14);
        time.setTextColor(ThemePalette.TEXT_SECONDARY);
        time.setBackground(ViewUtils.roundedWithStroke(0xC9FFFFFF, 0xAAFFFFFF, 12, activity));
        Button clearTime = new Button(activity);
        clearTime.setAllCaps(false);
        clearTime.setText("清除时间");
        clearTime.setTextSize(13);
        clearTime.setTextColor(ThemePalette.TEXT_SECONDARY);
        updateTimeButtons(time, clearTime, dueAt[0]);
        time.setOnClickListener(view -> pickDateTime(activity, dueAt, () ->
                updateTimeButtons(time, clearTime, dueAt[0])));
        clearTime.setOnClickListener(view -> {
            dueAt[0] = 0L;
            updateTimeButtons(time, clearTime, dueAt[0]);
        });
        timeRow.addView(time, new LinearLayout.LayoutParams(0, ViewUtils.dp(activity, 48), 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewUtils.dp(activity, 96), ViewUtils.dp(activity, 48));
        clearParams.setMargins(ViewUtils.dp(activity, 8), 0, 0, 0);
        timeRow.addView(clearTime, clearParams);
        content.addView(timeRow);

        CheckBox done = new CheckBox(activity);
        done.setText("已完成");
        done.setTextColor(ThemePalette.TEXT);
        done.setChecked(editing && task.done);
        done.setVisibility(editing ? android.view.View.VISIBLE : android.view.View.GONE);
        content.addView(done);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(editing ? "编辑待办" : "新增待办")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        if (editing) {
            builder.setNeutralButton("删除", null);
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ThemePalette.ACCENT);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) {
                    input.setError("请输入待办内容");
                    return;
                }
                int selectedQuadrant = ThemePalette.quadrantFor(
                        important.isChecked(), urgent.isChecked());
                if (editing) {
                    store.update(task.id, value, selectedQuadrant, done.isChecked(), dueAt[0]);
                } else {
                    store.add(value, selectedQuadrant, dueAt[0]);
                }
                dialog.dismiss();
                callback.onChanged();
            });
            if (editing) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.rgb(174, 74, 83));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    store.delete(task.id);
                    dialog.dismiss();
                    callback.onChanged();
                });
            }
        });
        dialog.show();
    }

    static void pickDateTime(Activity activity, long[] dueAt, Runnable callback) {
        Calendar selected = Calendar.getInstance();
        if (dueAt[0] > 0L) selected.setTimeInMillis(dueAt[0]);
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(activity, (timePicker, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                dueAt[0] = selected.getTimeInMillis();
                callback.run();
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static SilverToggleSwitch createSwitch(Activity activity, String description, boolean checked) {
        SilverToggleSwitch toggle = new SilverToggleSwitch(activity);
        toggle.setChecked(checked);
        toggle.setContentDescription(description);
        return toggle;
    }

    private static LinearLayout labeledSwitch(Activity activity, String label, SilverToggleSwitch toggle) {
        LinearLayout group = new LinearLayout(activity);
        group.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextColor(ThemePalette.TEXT);
        text.setTextSize(15);
        group.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        group.addView(toggle, new LinearLayout.LayoutParams(
                ViewUtils.dp(activity, 66), ViewUtils.dp(activity, 48)));
        return group;
    }

    private static void updateTimeButtons(Button time, Button clear, long dueAt) {
        time.setText(dueAt > 0L ? "◷  " + ViewUtils.formatDueTime(dueAt) : "◷  时间（可选）");
        clear.setVisibility(dueAt > 0L ? android.view.View.VISIBLE : android.view.View.GONE);
    }
}
