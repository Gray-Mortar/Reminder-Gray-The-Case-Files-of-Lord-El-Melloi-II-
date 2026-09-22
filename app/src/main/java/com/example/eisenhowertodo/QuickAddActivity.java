package com.example.eisenhowertodo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class QuickAddActivity extends Activity {
    private EditText input;
    private Spinner quadrant;
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
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private LinearLayout createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ViewUtils.dp(this, 22), ViewUtils.dp(this, 20),
                ViewUtils.dp(this, 22), ViewUtils.dp(this, 24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("快速添加待办");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(32, 54, 75));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        input = new EditText(this);
        input.setHint("现在要记下什么？");
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 58));
        inputParams.setMargins(0, ViewUtils.dp(this, 10), 0, ViewUtils.dp(this, 8));
        root.addView(input, inputParams);

        quadrant = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MainActivity.TITLES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quadrant.setAdapter(adapter);
        root.addView(quadrant, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 52)));

        Button save = new Button(this);
        save.setText("保存并返回桌面");
        save.setTextColor(Color.WHITE);
        save.setTextSize(16);
        save.setAllCaps(false);
        save.setBackground(ViewUtils.rounded(Color.rgb(32, 54, 75), 11, this));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewUtils.dp(this, 50));
        saveParams.setMargins(0, ViewUtils.dp(this, 12), 0, 0);
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

    private void save() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            input.setError("请输入待办内容");
            return;
        }
        store.add(text, quadrant.getSelectedItemPosition());
        Toast.makeText(this, "已添加到“" + MainActivity.TITLES[quadrant.getSelectedItemPosition()] + "”",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
