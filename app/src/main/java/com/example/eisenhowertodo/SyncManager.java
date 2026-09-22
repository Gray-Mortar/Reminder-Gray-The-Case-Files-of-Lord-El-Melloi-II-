package com.example.eisenhowertodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class SyncManager {
    private static final String PREFS = "remindergray_sync_settings";
    private static final String URL_KEY = "url";
    private static final String TOKEN_KEY = "token";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback { void completed(String status, boolean success); }

    private SyncManager() {}

    static boolean configured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !prefs.getString(URL_KEY, "").isEmpty() &&
                !prefs.getString(TOKEN_KEY, "").isEmpty();
    }

    static void showSettings(Activity activity, Callback callback) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = ViewUtils.dp(activity, 20);
        fields.setPadding(padding, padding / 2, padding, 0);

        TextView hint = new TextView(activity);
        hint.setText("填写同步服务地址和密钥。模拟器可用 http://10.0.2.2:8787；其他设备请使用 HTTPS 地址。");
        hint.setTextColor(ThemePalette.TEXT_SECONDARY);
        hint.setTextSize(13);
        fields.addView(hint);

        EditText url = new EditText(activity);
        url.setSingleLine(true);
        url.setHint("http://10.0.2.2:8787");
        url.setText(prefs.getString(URL_KEY, ""));
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        fields.addView(url);

        EditText token = new EditText(activity);
        token.setSingleLine(true);
        token.setHint("同步密钥");
        token.setText(prefs.getString(TOKEN_KEY, ""));
        token.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(token);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ReminderGray 同步")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setNeutralButton("立即同步", (ignored, which) -> syncNow(activity, callback))
                .setPositiveButton("保存并同步", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = url.getText().toString().trim().replaceAll("/+$", "");
                    String secret = token.getText().toString().trim();
                    boolean emulatorHttp = value.matches("http://10\\.0\\.2\\.2(:\\d+)?");
                    if (!(value.startsWith("https://") || emulatorHttp)) {
                        url.setError("请输入 HTTPS 地址；模拟器可使用 http://10.0.2.2:端口");
                        return;
                    }
                    if (secret.length() < 12) {
                        token.setError("请输入至少 12 位的同步密钥");
                        return;
                    }
                    prefs.edit().putString(URL_KEY, value).putString(TOKEN_KEY, secret).apply();
                    dialog.dismiss();
                    syncNow(activity, callback);
                }));
        dialog.show();
    }

    static void syncNow(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String baseUrl = prefs.getString(URL_KEY, "");
        String token = prefs.getString(TOKEN_KEY, "");
        if (baseUrl.isEmpty() || token.isEmpty()) {
            if (callback != null) callback.completed("未配置同步", false);
            return;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            String status;
            boolean success = false;
            try {
                TaskStore store = new TaskStore(app);
                store.initializeSync();
                JSONArray pending = store.pending();
                JSONObject request = new JSONObject();
                request.put("operations", pending);

                URL url = new URL(baseUrl + "/api/sync");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(7000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject response = new JSONObject(bytes.toString("UTF-8"));
                connection.disconnect();
                if (code != 200) throw new IllegalStateException(
                        response.optString("error", "HTTP " + code));
                store.applyRemote(response, pending);
                int conflicts = response.optJSONArray("conflicts") == null ? 0 :
                        response.optJSONArray("conflicts").length();
                status = conflicts > 0 ? "已同步，保留 " + conflicts + " 个冲突副本" : "已同步";
                success = true;
            } catch (Exception error) {
                status = "同步失败：" + error.getMessage();
            }
            String result = status;
            boolean completed = success;
            IN_FLIGHT.set(false);
            MAIN.post(() -> { if (callback != null) callback.completed(result, completed); });
        });
    }
}
