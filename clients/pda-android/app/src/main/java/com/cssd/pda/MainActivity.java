package com.cssd.pda;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String SERVER_BASE = "http://10.0.2.2:8080/cssd-trace/api";
    private static final String CACHE_KEY = "offline_events";

    private EditText packageInput;
    private EditText basketInput;
    private EditText deptInput;
    private TextView queueText;
    private TextView messageText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshQueue();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CSSD PDA下收/下送");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("无网络时先缓存，回到医院内网WiFi后上传");
        subtitle.setTextSize(14);
        root.addView(subtitle);

        packageInput = input("包条码，例如 PKG-0002");
        basketInput = input("筐条码，例如 BASK-02");
        deptInput = input("科室编码，例如 OR");
        basketInput.setText("BASK-02");
        deptInput.setText("OR");
        root.addView(packageInput);
        root.addView(basketInput);
        root.addView(deptInput);

        Button cacheButton = button("加入离线缓存");
        cacheButton.setOnClickListener(v -> cacheEvent());
        root.addView(cacheButton);

        Button syncButton = button("回院上传");
        syncButton.setOnClickListener(v -> syncEvents());
        root.addView(syncButton);

        Button clearButton = button("清空缓存");
        clearButton.setOnClickListener(v -> {
            saveQueue(new JSONArray());
            refreshQueue();
            messageText.setText("缓存已清空");
        });
        root.addView(clearButton);

        messageText = new TextView(this);
        messageText.setTextSize(16);
        messageText.setPadding(0, 20, 0, 12);
        root.addView(messageText);

        queueText = new TextView(this);
        queueText.setTextSize(14);
        root.addView(queueText);

        setContentView(scroll);
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(20);
        edit.setSingleLine(true);
        edit.setPadding(0, 18, 0, 18);
        return edit;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        return button;
    }

    private void cacheEvent() {
        try {
            String packageCode = packageInput.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (packageCode.isEmpty()) {
                messageText.setText("请扫描包条码");
                return;
            }
            JSONArray queue = readQueue();
            JSONObject item = new JSONObject();
            item.put("type", "recycle");
            item.put("packageCode", packageCode);
            item.put("basketCode", basketInput.getText().toString().trim().toUpperCase(Locale.ROOT));
            item.put("deptCode", deptInput.getText().toString().trim().toUpperCase(Locale.ROOT));
            item.put("occurredAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
            queue.put(item);
            saveQueue(queue);
            packageInput.setText("");
            refreshQueue();
            messageText.setText("已缓存，等待内网WiFi上传");
        } catch (Exception ex) {
            messageText.setText("缓存失败：" + ex.getMessage());
        }
    }

    private void syncEvents() {
        new Thread(() -> {
            try {
                JSONArray queue = readQueue();
                JSONObject body = new JSONObject();
                body.put("deviceCode", "PDA-ANDROID-01");
                body.put("events", queue);
                String result = postJson(SERVER_BASE + "/pda/sync", body.toString());
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("success")) {
                    JSONObject data = response.getJSONObject("data");
                    if (data.optInt("failedCount") == 0) {
                        saveQueue(new JSONArray());
                    }
                    runOnUiThread(() -> {
                        refreshQueue();
                        messageText.setText("上传完成：" + data.optInt("successCount") + "条成功，" + data.optInt("failedCount") + "条失败");
                    });
                } else {
                    runOnUiThread(() -> messageText.setText("上传失败：" + response.optString("message")));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> messageText.setText("无法连接内网服务器：" + ex.getMessage()));
            }
        }).start();
    }

    private String postJson(String urlText, String json) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private JSONArray readQueue() {
        try {
            String text = getPreferences(MODE_PRIVATE).getString(CACHE_KEY, "[]");
            return new JSONArray(text);
        } catch (Exception ex) {
            return new JSONArray();
        }
    }

    private void saveQueue(JSONArray queue) {
        getPreferences(MODE_PRIVATE).edit().putString(CACHE_KEY, queue.toString()).apply();
    }

    private void refreshQueue() {
        JSONArray queue = readQueue();
        StringBuilder sb = new StringBuilder();
        sb.append("待上传记录：").append(queue.length()).append("条\n\n");
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item != null) {
                sb.append(i + 1)
                        .append(". ")
                        .append(item.optString("packageCode"))
                        .append(" / ")
                        .append(item.optString("basketCode"))
                        .append(" / ")
                        .append(item.optString("occurredAt"))
                        .append("\n");
            }
        }
        if (queueText != null) {
            queueText.setText(sb.toString());
        }
    }
}
