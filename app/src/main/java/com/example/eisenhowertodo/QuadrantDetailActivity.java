package com.example.eisenhowertodo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class QuadrantDetailActivity extends Activity {
    static final String EXTRA_QUADRANT = "quadrant";

    private TaskStore store;
    private int quadrant;
    private LinearLayout taskList;
    private TextView count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        quadrant = Math.max(0, Math.min(3, getIntent().getIntExtra(EXTRA_QUADRANT, 0)));
        store = new TaskStore(this);
        setContentView(createContent());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (taskList != null) render();
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);

        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.app_background_tall);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setAlpha(0.48f);
        root.addView(background, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View veil = new View(this);
        veil.setBackgroundColor(0x9FF4F6F7);
        root.addView(veil, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        final int baseTopPadding = ViewUtils.dp(this, 22);
        final int minimumTopClearance = ViewUtils.dp(this, 24);
        page.setPadding(ViewUtils.dp(this, 14), baseTopPadding + minimumTopClearance,
                ViewUtils.dp(this, 14), ViewUtils.dp(this, 18));
        final int baseBottomPadding = ViewUtils.dp(this, 18);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int topClearance = Math.max(insets.getSystemWindowInsetTop(), minimumTopClearance);
            view.setPadding(view.getPaddingLeft(), baseTopPadding + topClearance,
                    view.getPaddingRight(), baseBottomPadding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.requestApplyInsets();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ViewUtils.dp(this, 8), ViewUtils.dp(this, 6),
                ViewUtils.dp(this, 8), ViewUtils.dp(this, 6));
        header.setBackground(ViewUtils.roundedWithStroke(
                0xD9FFFFFF, 0xE8FFFFFF, 1f, 15, this));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(ThemePalette.TEXT);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 44), ViewUtils.dp(this, 48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(MainActivity.TITLES[quadrant], 22, ThemePalette.TEXT, true);
        labels.addView(title);
        TextView hint = text("长按拖动排序，越靠上越优先；点击可编辑", 11,
                ThemePalette.TEXT_SECONDARY, false);
        labels.addView(hint);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        count = text("0", 15, ThemePalette.TEXT, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(ViewUtils.rounded(
                ViewUtils.withAlpha(MainActivity.COLORS[quadrant], 62), 12, this));
        header.addView(count, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 46), ViewUtils.dp(this, 34)));
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        taskList.setPadding(0, ViewUtils.dp(this, 10), 0, ViewUtils.dp(this, 10));
        taskList.setOnDragListener((view, event) -> handleListDrag(event));
        scroll.addView(taskList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(this);
        add.setText("新增待办");
        add.setAllCaps(false);
        add.setTextColor(Color.WHITE);
        add.setTextSize(16);
        add.setBackground(ViewUtils.rounded(ThemePalette.ACCENT, 13, this));
        add.setOnClickListener(view -> TaskEditorDialog.show(
                this, store, null, quadrant, this::render));
        page.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 52)));

        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void render() {
        taskList.removeAllViews();
        List<Task> tasks = store.all();
        int total = 0;
        for (Task task : tasks) {
            if (task.quadrant != quadrant) continue;
            total++;
            taskList.addView(createTaskRow(task));
        }
        count.setText(String.valueOf(total));
        if (total == 0) {
            TextView empty = text("点击下方按钮添加待办", 14,
                    ThemePalette.TEXT_SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            taskList.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 140)));
        }
    }

    private View createTaskRow(Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ViewUtils.dp(this, 8), ViewUtils.dp(this, 6),
                ViewUtils.dp(this, 12), ViewUtils.dp(this, 6));
        row.setBackground(ViewUtils.roundedWithStroke(
                0xE6FFFFFF, ViewUtils.withAlpha(MainActivity.COLORS[quadrant], 72),
                1f, 14, this));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 72));
        rowParams.setMargins(0, 0, 0, ViewUtils.dp(this, 8));
        row.setLayoutParams(rowParams);

        CheckBox check = new CheckBox(this);
        check.setChecked(task.done);
        check.setButtonTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ThemePalette.ACCENT, ThemePalette.TEXT_SECONDARY}));
        check.setOnClickListener(view -> {
            store.toggle(task.id);
            render();
        });
        row.addView(check, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 44), ViewUtils.dp(this, 48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(task.text, 16,
                task.done ? 0x99616C76 : ThemePalette.TEXT, true);
        if (task.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        labels.addView(title);
        if (task.dueAt > 0L) {
            labels.addView(text("◷ " + ViewUtils.formatDueTime(task.dueAt), 12,
                    ThemePalette.TEXT_SECONDARY, false));
        }
        row.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        TextView edit = text("编辑  ›", 13, ThemePalette.TEXT_SECONDARY, false);
        row.addView(edit);
        row.setOnClickListener(view -> TaskEditorDialog.show(
                this, store, store.find(task.id), quadrant, this::render));
        row.setOnLongClickListener(view -> {
            ClipData data = ClipData.newPlainText("detail-task-id", String.valueOf(task.id));
            view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, 0);
            return true;
        });
        row.setOnDragListener((view, event) -> handleRowDrag(row, task, event));
        return row;
    }

    private boolean handleRowDrag(View row, Task anchor, DragEvent event) {
        ClipDescription description = event.getClipDescription();
        if (description == null || !"detail-task-id".contentEquals(description.getLabel())) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                row.setAlpha(0.62f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                row.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                long draggedId = Long.parseLong(
                        event.getClipData().getItemAt(0).getText().toString());
                boolean placeAfter = event.getY() > row.getHeight() / 2f;
                store.reorder(draggedId, anchor.id, placeAfter);
                row.setAlpha(1f);
                render();
                return true;
            default:
                return true;
        }
    }

    private boolean handleListDrag(DragEvent event) {
        ClipDescription description = event.getClipDescription();
        if (description == null || !"detail-task-id".contentEquals(description.getLabel())) {
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            long draggedId = Long.parseLong(
                    event.getClipData().getItemAt(0).getText().toString());
            store.moveToEnd(draggedId, quadrant);
            render();
        }
        return true;
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
