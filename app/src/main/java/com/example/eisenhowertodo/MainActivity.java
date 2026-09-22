package com.example.eisenhowertodo;

import android.app.Activity;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    static final String EXTRA_FOCUS_INPUT = "focus_input_from_widget";
    static final String[] TITLES = {"立即做", "安排做", "委托做", "尽量不做"};
    static final int[] COLORS = ThemePalette.QUADRANT;
    private static final int MAX_VISIBLE_TASKS = 4;

    private final LinearLayout[] taskLists = new LinearLayout[4];
    private final TextView[] countBadges = new TextView[4];
    private TaskStore store;
    private EditText input;
    private SilverToggleSwitch importantSwitch;
    private SilverToggleSwitch urgentSwitch;
    private Button timeButton;
    private Button syncButton;
    private final long[] draftDueAt = {0L};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        store = new TaskStore(this);
        setContentView(createContent());
        renderTasks();
        focusInputIfRequested(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        focusInputIfRequested(intent);
    }

    private void focusInputIfRequested(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_FOCUS_INPUT, false) || input == null) return;
        intent.removeExtra(EXTRA_FOCUS_INPUT);
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && taskLists[0] != null) renderTasks();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(0xFFF4F6F7);
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);

        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.app_background_tall);
        // Keep exactly one full-screen image. Stacking FIT_START over CENTER_CROP
        // made the character appear twice on tall physical devices.
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setAlpha(0.76f);
        root.addView(background, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View softLight = new View(this);
        softLight.setBackgroundColor(0x0FFFFFFF);
        root.addView(softLight, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ViewUtils.dp(this, 12), 0,
                ViewUtils.dp(this, 12), ViewUtils.dp(this, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        View portraitSpace = new View(this);
        int portraitHeight = Math.round(
                getResources().getDisplayMetrics().heightPixels * 0.33f);
        content.addView(portraitSpace, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, portraitHeight));

        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        composerParams.setMargins(0, 0, 0, ViewUtils.dp(this, 6));
        content.addView(createComposer(), composerParams);

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
            params.height = ViewUtils.dp(this, 238);
            int gap = ViewUtils.dp(this, 3);
            params.setMargins(gap, gap, gap, gap);
            matrix.addView(card, params);
        }
        content.addView(matrix, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) scroll.getLayoutParams();
            params.bottomMargin = insets.getSystemWindowInsetBottom();
            scroll.setLayoutParams(params);
            return insets;
        });
        root.requestApplyInsets();
        return root;
    }

    private View createComposer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ViewUtils.dp(this, 10), ViewUtils.dp(this, 9),
                ViewUtils.dp(this, 10), ViewUtils.dp(this, 8));
        panel.setBackground(ViewUtils.roundedWithStroke(
                0xDCF3F7F8, 0xD9FFFFFF, 1f, 17, this));
        panel.setElevation(ViewUtils.dp(this, 4));

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout draftField = new LinearLayout(this);
        draftField.setGravity(Gravity.CENTER_VERTICAL);
        draftField.setPadding(ViewUtils.dp(this, 8), 0, ViewUtils.dp(this, 6), 0);
        draftField.setBackground(ViewUtils.roundedWithStroke(
                0xD9FFFFFF, 0xDFFFFFFF, 1f, 13, this));

        TextView handle = text("⠿", 25, ThemePalette.TEXT_SECONDARY, false);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("拖动待办到象限");
        handle.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return startDraftDrag(view);
            return true;
        });
        draftField.addView(handle, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 34), ViewUtils.dp(this, 50)));

        input = new EditText(this);
        input.setHint("输入新的待办事项");
        input.setHintTextColor(0x92717D8A);
        input.setTextColor(ThemePalette.TEXT);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackgroundColor(Color.TRANSPARENT);
        draftField.addView(input, new LinearLayout.LayoutParams(
                0, ViewUtils.dp(this, 52), 1));

        firstRow.addView(draftField, new LinearLayout.LayoutParams(
                0, ViewUtils.dp(this, 54), 1));

        Button add = new Button(this);
        add.setText("添加");
        add.setTextColor(Color.WHITE);
        add.setTextSize(16);
        add.setAllCaps(false);
        add.setBackground(ViewUtils.rounded(ThemePalette.ACCENT, 13, this));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 76), ViewUtils.dp(this, 52));
        addParams.setMargins(ViewUtils.dp(this, 8), 0, 0, 0);
        firstRow.addView(add, addParams);
        panel.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setGravity(Gravity.CENTER_VERTICAL);
        secondRow.setPadding(0, ViewUtils.dp(this, 5), 0, 0);
        importantSwitch = createSwitch("重要");
        urgentSwitch = createSwitch("紧急");
        secondRow.addView(createSwitchGroup("重要", importantSwitch),
                new LinearLayout.LayoutParams(ViewUtils.dp(this, 100), ViewUtils.dp(this, 48)));
        secondRow.addView(createSwitchGroup("紧急", urgentSwitch),
                new LinearLayout.LayoutParams(ViewUtils.dp(this, 100), ViewUtils.dp(this, 48)));

        timeButton = new Button(this);
        timeButton.setAllCaps(false);
        timeButton.setTextSize(13);
        timeButton.setTextColor(ThemePalette.TEXT_SECONDARY);
        timeButton.setPadding(ViewUtils.dp(this, 6), 0, ViewUtils.dp(this, 6), 0);
        timeButton.setBackground(ViewUtils.roundedWithStroke(
                0xBFFFFFFF, 0xCFFFFFFF, 1f, 12, this));
        updateDraftTimeButton();
        timeButton.setOnClickListener(view -> TaskEditorDialog.pickDateTime(
                this, draftDueAt, this::updateDraftTimeButton));
        timeButton.setOnLongClickListener(view -> {
            draftDueAt[0] = 0L;
            updateDraftTimeButton();
            Toast.makeText(this, "已清除时间", Toast.LENGTH_SHORT).show();
            return true;
        });
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0, ViewUtils.dp(this, 42), 1);
        timeParams.setMargins(ViewUtils.dp(this, 4), 0, 0, 0);
        secondRow.addView(timeButton, timeParams);
        syncButton = new Button(this);
        syncButton.setText("☁");
        syncButton.setTextSize(17);
        syncButton.setTextColor(ThemePalette.TEXT_SECONDARY);
        syncButton.setPadding(0, 0, 0, 0);
        syncButton.setMinWidth(0);
        syncButton.setMinimumWidth(0);
        syncButton.setBackgroundColor(Color.TRANSPARENT);
        syncButton.setContentDescription("点击配置并手动同步");
        syncButton.setOnClickListener(view -> SyncManager.showSettings(this, (status, success) -> {
            syncButton.setContentDescription(status + "，点击配置并手动同步");
            syncButton.setText(success ? "☁" : "☁!");
            if (success) renderTasks();
            else Toast.makeText(this, status, Toast.LENGTH_LONG).show();
        }));
        secondRow.addView(syncButton, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 34), ViewUtils.dp(this, 42)));
        panel.addView(secondRow);

        add.setOnClickListener(view -> addTaskFromControls());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTaskFromControls();
                return true;
            }
            return false;
        });
        return panel;
    }

    private SilverToggleSwitch createSwitch(String description) {
        SilverToggleSwitch toggle = new SilverToggleSwitch(this);
        toggle.setContentDescription(description);
        return toggle;
    }

    private View createSwitchGroup(String label, SilverToggleSwitch toggle) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = text(label, 14, ThemePalette.TEXT, false);
        group.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        group.addView(toggle, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 64), ViewUtils.dp(this, 46)));
        return group;
    }

    private View createQuadrantCard(int quadrant) {
        FrameLayout card = new FrameLayout(this);
        card.setTag(quadrant);
        card.setClipToOutline(true);
        card.setBackground(ViewUtils.roundedWithStroke(
                ViewUtils.withAlpha(COLORS[quadrant], 91),
                0xD9FFFFFF, 1.2f, 16, this));
        card.setElevation(ViewUtils.dp(this, 2));

        TextView watermark = text(TITLES[quadrant], 33,
                ViewUtils.withAlpha(COLORS[quadrant], 118), false);
        watermark.setGravity(Gravity.CENTER);
        Typeface handwriting = getResources().getFont(R.font.lxgw_wenkai_lite_regular);
        watermark.setTypeface(handwriting);
        FrameLayout.LayoutParams watermarkParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 72),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        watermarkParams.setMargins(ViewUtils.dp(this, 8), 0,
                ViewUtils.dp(this, 8), ViewUtils.dp(this, 12));
        card.addView(watermark, watermarkParams);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ViewUtils.dp(this, 7), ViewUtils.dp(this, 34),
                ViewUtils.dp(this, 7), ViewUtils.dp(this, 7));
        taskLists[quadrant] = list;
        card.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView count = text("0", 15, ThemePalette.TEXT, true);
        count.setGravity(Gravity.CENTER);
        count.setContentDescription("查看全部待办");
        count.setBackground(ViewUtils.rounded(0x52FFFFFF, 11, this));
        count.setMinWidth(ViewUtils.dp(this, 34));
        count.setPadding(ViewUtils.dp(this, 8), ViewUtils.dp(this, 3),
                ViewUtils.dp(this, 8), ViewUtils.dp(this, 3));
        count.setOnClickListener(view -> openDetails(quadrant));
        countBadges[quadrant] = count;
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, ViewUtils.dp(this, 30),
                Gravity.TOP | Gravity.END);
        countParams.setMargins(0, ViewUtils.dp(this, 4), ViewUtils.dp(this, 5), 0);
        card.addView(count, countParams);

        card.setOnDragListener((view, event) -> handleDrop(card, event));
        return card;
    }

    private boolean handleDrop(FrameLayout card, DragEvent event) {
        ClipDescription description = event.getClipDescription();
        if (description == null || !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                card.setAlpha(0.72f);
                card.setScaleX(1.02f);
                card.setScaleY(1.02f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                resetCardState(card);
                return true;
            case DragEvent.ACTION_DROP:
                int quadrant = (int) card.getTag();
                String label = String.valueOf(description.getLabel());
                String value = event.getClipData().getItemAt(0).getText().toString();
                if ("draft".equals(label)) {
                    store.add(value, quadrant, draftDueAt[0]);
                    input.setText("");
                    draftDueAt[0] = 0L;
                    updateDraftTimeButton();
                } else if ("task-id".equals(label)) {
                    store.move(Long.parseLong(value), quadrant);
                }
                resetCardState(card);
                renderTasks();
                return true;
            default:
                return true;
        }
    }

    private void resetCardState(FrameLayout card) {
        card.setAlpha(1f);
        card.setScaleX(1f);
        card.setScaleY(1f);
    }

    private boolean startDraftDrag(View source) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            input.requestFocus();
            input.setError("先输入待办内容");
            return true;
        }
        ClipData data = ClipData.newPlainText("draft", value);
        source.startDragAndDrop(data, new View.DragShadowBuilder(source), null, 0);
        return true;
    }

    private void addTaskFromControls() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            input.setError("请输入待办内容");
            return;
        }
        int quadrant = ThemePalette.quadrantFor(
                importantSwitch.isChecked(), urgentSwitch.isChecked());
        store.add(value, quadrant, draftDueAt[0]);
        input.setText("");
        draftDueAt[0] = 0L;
        updateDraftTimeButton();
        renderTasks();
        Toast.makeText(this, "已添加到“" + TITLES[quadrant] + "”", Toast.LENGTH_SHORT).show();
    }

    private void updateDraftTimeButton() {
        timeButton.setText(draftDueAt[0] > 0L
                ? "◷ " + ViewUtils.formatDueTime(draftDueAt[0])
                : "◷ 时间（可选）");
    }

    private void renderTasks() {
        for (LinearLayout taskList : taskLists) taskList.removeAllViews();
        List<Task> tasks = store.all();
        int[] counts = new int[4];
        int[] visible = new int[4];
        for (Task task : tasks) {
            counts[task.quadrant]++;
            if (visible[task.quadrant] < MAX_VISIBLE_TASKS) {
                taskLists[task.quadrant].addView(createTaskRow(task));
                visible[task.quadrant]++;
            }
        }
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            countBadges[quadrant].setText(String.valueOf(counts[quadrant]));
        }
    }

    private View createTaskRow(Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ViewUtils.dp(this, 3), ViewUtils.dp(this, 2),
                ViewUtils.dp(this, 2), ViewUtils.dp(this, 2));
        row.setBackground(ViewUtils.roundedWithStroke(
                0xCFFFFFFF, 0xA8FFFFFF, 1f, 12, this));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                task.dueAt > 0L ? ViewUtils.dp(this, 50) : ViewUtils.dp(this, 43));
        rowParams.setMargins(0, 0, 0, ViewUtils.dp(this, 5));
        row.setLayoutParams(rowParams);

        CheckBox check = new CheckBox(this);
        check.setButtonTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ThemePalette.ACCENT, Color.rgb(122, 135, 146)}));
        check.setChecked(task.done);
        check.setContentDescription(task.done ? "标记为未完成" : "标记为已完成");
        check.setOnClickListener(view -> {
            store.toggle(task.id);
            renderTasks();
        });
        row.addView(check, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 38), ViewUtils.dp(this, 40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView taskText = text(task.text, 13, task.done ? 0x96616C76 : ThemePalette.TEXT, true);
        taskText.setMaxLines(2);
        if (task.done) taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        labels.addView(taskText);
        if (task.dueAt > 0L) {
            TextView time = text("◷ " + ViewUtils.formatDueTime(task.dueAt), 10,
                    ThemePalette.TEXT_SECONDARY, false);
            labels.addView(time);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        Button more = new Button(this);
        more.setText("⋯");
        more.setTextSize(18);
        more.setTextColor(ThemePalette.TEXT_SECONDARY);
        more.setContentDescription("更多操作");
        more.setMinWidth(0);
        more.setMinimumWidth(0);
        more.setPadding(0, 0, 0, 0);
        more.setBackgroundColor(Color.TRANSPARENT);
        more.setOnClickListener(view -> showTaskMenu(more, task));
        row.addView(more, new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 32), ViewUtils.dp(this, 40)));

        row.setOnClickListener(view -> TaskEditorDialog.show(
                this, store, store.find(task.id), task.quadrant, this::renderTasks));
        row.setOnLongClickListener(view -> {
            ClipData data = ClipData.newPlainText("task-id", String.valueOf(task.id));
            view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, 0);
            return true;
        });
        return row;
    }

    private void showTaskMenu(View anchor, Task task) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(1, 100, 0, "编辑");
        for (int index = 0; index < TITLES.length; index++) {
            menu.getMenu().add(0, index, index + 1, "移至“" + TITLES[index] + "”");
        }
        menu.getMenu().add(1, 101, 6, "删除");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == 0) {
                store.move(task.id, item.getItemId());
                renderTasks();
            } else if (item.getItemId() == 100) {
                TaskEditorDialog.show(this, store, store.find(task.id), task.quadrant,
                        this::renderTasks);
            } else {
                store.delete(task.id);
                renderTasks();
            }
            return true;
        });
        menu.show();
    }

    private void openDetails(int quadrant) {
        Intent intent = new Intent(this, QuadrantDetailActivity.class);
        intent.putExtra(QuadrantDetailActivity.EXTRA_QUADRANT, quadrant);
        startActivity(intent);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(color);
        if (bold) textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        return textView;
    }
}
