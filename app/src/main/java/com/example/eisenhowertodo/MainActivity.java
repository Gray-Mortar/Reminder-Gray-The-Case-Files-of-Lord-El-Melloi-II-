package com.example.eisenhowertodo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    static final String[] TITLES = {"立即做", "安排做", "委托做", "尽量不做"};
    static final String[] DESCRIPTIONS = {
            "紧急且重要", "重要但不紧急", "紧急但不重要", "不紧急不重要"
    };
    static final int[] COLORS = {
            Color.rgb(232, 93, 93), Color.rgb(57, 185, 120),
            Color.rgb(76, 142, 217), Color.rgb(135, 149, 161)
    };

    private final LinearLayout[] taskLists = new LinearLayout[4];
    private final TextView[] countBadges = new TextView[4];
    private TaskStore store;
    private EditText input;
    private Spinner quadrantSpinner;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        setTitle("四象限待办");
        setContentView(createContent());
        renderTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && taskLists[0] != null) renderTasks();
    }

    private View createContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ViewUtils.dp(this, 14), ViewUtils.dp(this, 22),
                ViewUtils.dp(this, 14), ViewUtils.dp(this, 32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("四象限待办", 28, Color.rgb(32, 54, 75), true);
        root.addView(title);

        TextView subtitle = text("先判断重要性，再决定行动方式", 14, Color.rgb(107, 120, 132), false);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.setMargins(0, ViewUtils.dp(this, 2), 0, ViewUtils.dp(this, 16));
        root.addView(subtitle, subtitleParams);

        root.addView(createAddPanel(), matchWrapWithBottom(16));

        GridLayout matrix = new GridLayout(this);
        matrix.setColumnCount(2);
        matrix.setRowCount(2);
        matrix.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        matrix.setUseDefaultMargins(false);
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            View card = createQuadrantCard(quadrant);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(quadrant / 2, 1f), GridLayout.spec(quadrant % 2, 1f));
            params.width = 0;
            params.height = ViewUtils.dp(this, 250);
            int gap = ViewUtils.dp(this, 5);
            params.setMargins(gap, gap, gap, gap);
            matrix.addView(card, params);
        }
        root.addView(matrix, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(ViewUtils.dp(this, 4), ViewUtils.dp(this, 12), ViewUtils.dp(this, 4), 0);
        summary = text("", 13, Color.rgb(107, 120, 132), false);
        footer.addView(summary, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button clear = new Button(this);
        clear.setText("清除已完成");
        clear.setTextSize(12);
        clear.setAllCaps(false);
        clear.setOnClickListener(view -> {
            store.clearCompleted();
            renderTasks();
        });
        footer.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ViewUtils.dp(this, 44)));
        root.addView(footer);

        return scroll;
    }

    private View createAddPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ViewUtils.dp(this, 12), ViewUtils.dp(this, 10),
                ViewUtils.dp(this, 12), ViewUtils.dp(this, 10));
        panel.setBackground(ViewUtils.roundedWithStroke(Color.WHITE,
                Color.rgb(226, 231, 236), 14, this));

        input = new EditText(this);
        input.setHint("输入新的待办事项");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        panel.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 52)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        quadrantSpinner = createQuadrantSpinner();
        actions.addView(quadrantSpinner, new LinearLayout.LayoutParams(
                0, ViewUtils.dp(this, 48), 1));

        Button add = new Button(this);
        add.setText("添加");
        add.setTextColor(Color.WHITE);
        add.setTextSize(15);
        add.setAllCaps(false);
        add.setBackground(ViewUtils.rounded(Color.rgb(32, 54, 75), 10, this));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 88), ViewUtils.dp(this, 44));
        addParams.setMargins(ViewUtils.dp(this, 8), 0, 0, 0);
        actions.addView(add, addParams);
        panel.addView(actions);

        View.OnClickListener submit = view -> addTask();
        add.setOnClickListener(submit);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask();
                return true;
            }
            return false;
        });
        return panel;
    }

    private Spinner createQuadrantSpinner() {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TITLES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private View createQuadrantCard(int quadrant) {
        FrameLayout card = new FrameLayout(this);
        int tint = blendWithWhite(COLORS[quadrant], 0.91f);
        card.setBackground(ViewUtils.roundedWithStroke(tint,
                ViewUtils.withAlpha(COLORS[quadrant], 95), 14, this));
        card.setTag(quadrant);

        TextView watermark = text(TITLES[quadrant], 31,
                ViewUtils.withAlpha(COLORS[quadrant], 35), true);
        watermark.setGravity(Gravity.CENTER);
        watermark.setRotation(-12f);
        card.addView(watermark, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout foreground = new LinearLayout(this);
        foreground.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ViewUtils.dp(this, 10), ViewUtils.dp(this, 9),
                ViewUtils.dp(this, 8), ViewUtils.dp(this, 9));
        header.setBackground(ViewUtils.rounded(COLORS[quadrant], 14, this));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(TITLES[quadrant], 16, Color.WHITE, true);
        labels.addView(heading);
        TextView description = text(DESCRIPTIONS[quadrant], 10, 0xE6FFFFFF, false);
        labels.addView(description);
        header.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = text("0", 12, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ViewUtils.rounded(0x33FFFFFF, 12, this));
        badge.setMinWidth(ViewUtils.dp(this, 28));
        badge.setPadding(ViewUtils.dp(this, 7), ViewUtils.dp(this, 3),
                ViewUtils.dp(this, 7), ViewUtils.dp(this, 3));
        countBadges[quadrant] = badge;
        header.addView(badge);
        foreground.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView taskScroll = new ScrollView(this);
        taskScroll.setFillViewport(true);
        taskScroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ViewUtils.dp(this, 6), ViewUtils.dp(this, 7),
                ViewUtils.dp(this, 6), ViewUtils.dp(this, 6));
        taskLists[quadrant] = list;
        taskScroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));
        foreground.addView(taskScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        card.addView(foreground, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        card.setOnDragListener((view, event) -> handleDrop(card, event));
        return card;
    }

    private boolean handleDrop(FrameLayout card, DragEvent event) {
        if (!event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                card.setAlpha(0.72f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                card.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                long id = Long.parseLong(event.getClipData().getItemAt(0).getText().toString());
                store.move(id, (int) card.getTag());
                card.setAlpha(1f);
                renderTasks();
                return true;
            default:
                return true;
        }
    }

    private void addTask() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            input.setError("请输入待办内容");
            return;
        }
        store.add(text, quadrantSpinner.getSelectedItemPosition());
        input.setText("");
        renderTasks();
        Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show();
    }

    private void renderTasks() {
        for (LinearLayout taskList : taskLists) taskList.removeAllViews();
        List<Task> tasks = store.all();
        int[] counts = new int[4];
        int completed = 0;
        for (Task task : tasks) {
            counts[task.quadrant]++;
            if (task.done) completed++;
            taskLists[task.quadrant].addView(createTaskRow(task));
        }
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            countBadges[quadrant].setText(String.valueOf(counts[quadrant]));
            if (counts[quadrant] == 0) {
                TextView empty = text("暂无事项", 11, ViewUtils.withAlpha(COLORS[quadrant], 140), false);
                empty.setGravity(Gravity.CENTER);
                taskLists[quadrant].addView(empty, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            }
        }
        summary.setText(getString(R.string.task_summary, tasks.size(), completed));
    }

    private View createTaskRow(Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ViewUtils.dp(this, 3), 0, ViewUtils.dp(this, 3));

        CheckBox check = new CheckBox(this);
        check.setButtonTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {COLORS[task.quadrant], Color.rgb(150, 158, 166)}));
        check.setChecked(task.done);
        check.setContentDescription(task.done ? "标记为未完成" : "标记为已完成");
        check.setOnClickListener(view -> {
            store.toggle(task.id);
            renderTasks();
        });
        row.addView(check, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 38), ViewUtils.dp(this, 42)));

        TextView taskText = text(task.text, 13,
                task.done ? Color.rgb(145, 153, 160) : Color.rgb(43, 55, 66), false);
        taskText.setMaxLines(3);
        if (task.done) taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        row.addView(taskText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button more = new Button(this);
        more.setText("⋮");
        more.setTextSize(18);
        more.setContentDescription("更多操作");
        more.setMinWidth(0);
        more.setMinimumWidth(0);
        more.setPadding(0, 0, 0, 0);
        more.setOnClickListener(view -> showTaskMenu(more, task));
        row.addView(more, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 32), ViewUtils.dp(this, 40)));

        row.setOnLongClickListener(view -> {
            ClipData data = ClipData.newPlainText("task-id", String.valueOf(task.id));
            view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, 0);
            return true;
        });
        return row;
    }

    private void showTaskMenu(View anchor, Task task) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int index = 0; index < TITLES.length; index++) {
            menu.getMenu().add(0, index, index, "移至“" + TITLES[index] + "”");
        }
        menu.getMenu().add(1, 100, 5, "删除");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == 1) store.delete(task.id);
            else store.move(task.id, item.getItemId());
            renderTasks();
            return true;
        });
        menu.show();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(color);
        if (bold) textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        return textView;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, ViewUtils.dp(this, bottomDp));
        return params;
    }

    private int blendWithWhite(int color, float whiteRatio) {
        int red = Math.round(Color.red(color) * (1 - whiteRatio) + 255 * whiteRatio);
        int green = Math.round(Color.green(color) * (1 - whiteRatio) + 255 * whiteRatio);
        int blue = Math.round(Color.blue(color) * (1 - whiteRatio) + 255 * whiteRatio);
        return Color.rgb(red, green, blue);
    }
}
