package com.cssd.pda;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
    private TextView messageText;
    private TextView syncCountText;
    private TextView taskListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0F172A"));
        getWindow().setNavigationBarColor(Color.parseColor("#111827"));
        showLogin();
    }

    // 构建 PDA 登录页，对齐设计图里的服务器、账号、密码和离线能力说明。
    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical(0);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        TextView logo = text("CSSD PDA", 34, "#111827", true);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, dp(10), 0, dp(10));
        logo.setBackground(stroke("#DCE5F2", "#FFFFFF", 18));
        addMargin(root, logo, 0, 0, 0, 10);
        TextView subtitle = text("复用无菌器械闭环追溯系统", 18, "#475569", true);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        LinearLayout card = card();
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        addMargin(root, card, 0, 34, 0, 0);

        card.addView(label("服务器 / 医院"));
        card.addView(selectorText("北京协和医院（东院区）"));
        card.addView(label("用户名 / 工号"));
        EditText userInput = input("请输入用户名或工号");
        userInput.setText("PDA001");
        card.addView(userInput);
        card.addView(label("密码"));
        EditText passwordInput = input("请输入密码");
        card.addView(passwordInput);

        Button loginButton = primaryButton("登录");
        loginButton.setOnClickListener(v -> showHome(userInput.getText().toString().trim()));
        card.addView(loginButton);

        TextView mode = text("当前为在线模式，数据将实时同步", 16, "#0F9F6E", false);
        mode.setPadding(dp(10), dp(16), dp(10), dp(12));
        card.addView(mode);
        TextView device = text("设备编号：PDA-20240528-0187", 15, "#67748A", false);
        card.addView(device);
        TextView network = text("网络连接正常", 16, "#0F9F6E", true);
        network.setPadding(0, dp(10), 0, dp(10));
        card.addView(network);

        LinearLayout offline = card();
        offline.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView offlineTitle = text("离线任务支持", 17, "#2563EB", true);
        offline.addView(offlineTitle);
        offline.addView(text("无网络时可正常执行扫描、录入等操作，任务将自动缓存，联网后自动同步。", 14, "#64748B", false));
        addMargin(card, offline, 0, 16, 0, 0);

        setContentView(scroll);
    }

    // 构建 PDA 首页，对齐设计图里的离线提示、统计卡片、快捷扫码、功能宫格和最近任务。
    private void showHome(String userName) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical(0);
        root.setPadding(0, 0, 0, dp(18));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        LinearLayout header = vertical(0);
        header.setPadding(dp(22), dp(18), dp(22), dp(28));
        header.setBackground(gradient("#0F172A", "#1D4ED8"));
        root.addView(header);
        TextView time = text("10:30", 19, "#FFFFFF", false);
        header.addView(time);
        TextView title = text("CSSD PDA", 30, "#FFFFFF", true);
        header.addView(title);
        TextView user = text((userName.isEmpty() ? "张工" : userName) + "    已同步 10:28", 16, "#BFDBFE", false);
        header.addView(user);

        LinearLayout content = vertical(0);
        content.setPadding(dp(14), 0, dp(14), 0);
        root.addView(content);

        LinearLayout offlineBar = card();
        offlineBar.setPadding(dp(14), dp(12), dp(14), dp(12));
        addMargin(content, offlineBar, 0, -18, 0, 12);
        offlineBar.addView(text("当前离线：正常使用，数据将稍后自动同步        查看详情 >", 15, "#2563EB", true));

        LinearLayout stats = horizontal();
        stats.addView(statusCard("待下收", "16", "#EAFBF3", "#0F9F6E"));
        stats.addView(statusCard("待下送", "35", "#EFF6FF", "#2563EB"));
        stats.addView(statusCard("待同步", String.valueOf(readQueue().length()), "#FFF7ED", "#D97706"));
        stats.addView(statusCard("异常", "2", "#FEF2F2", "#DC2626"));
        content.addView(stats);

        TextView scanTitle = sectionTitle("快捷扫码");
        content.addView(scanTitle);
        LinearLayout scanCard = card();
        scanCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        scanCard.addView(text("点击扫描条码 / RFID   >", 18, "#64748B", false));
        packageInput = input("请输入或扫描包条码");
        basketInput = input("筐条码，例如 BASK-02");
        deptInput = input("科室编码，例如 OR");
        basketInput.setText("BASK-02");
        deptInput.setText("OR");
        scanCard.addView(packageInput);
        scanCard.addView(basketInput);
        scanCard.addView(deptInput);
        content.addView(scanCard);

        content.addView(sectionTitle("常用功能"));
        LinearLayout actions = card();
        actions.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout row1 = horizontal();
        row1.addView(actionTile("下收", "#0F9F6E", v -> cacheEvent()));
        row1.addView(actionTile("下送", "#2563EB", v -> cacheEvent()));
        row1.addView(actionTile("追溯查询", "#0891B2", v -> setMessage("请输入包码后进行追溯查询")));
        actions.addView(row1);
        LinearLayout row2 = horizontal();
        row2.addView(actionTile("异常登记", "#DC2626", v -> setMessage("异常已记录到本地待同步")));
        row2.addView(actionTile("借包处理", "#7C3AED", v -> setMessage("借包处理入口已准备")));
        row2.addView(actionTile("同步记录", "#2563EB", v -> syncEvents()));
        actions.addView(row2);
        content.addView(actions);

        content.addView(sectionTitle("最近任务"));
        LinearLayout taskCard = card();
        taskCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        taskListText = text("", 15, "#334155", false);
        taskCard.addView(taskListText);
        content.addView(taskCard);

        messageText = text("等待扫描", 15, "#2563EB", true);
        messageText.setPadding(dp(4), dp(12), dp(4), dp(6));
        content.addView(messageText);
        syncCountText = text("", 14, "#64748B", false);
        content.addView(syncCountText);

        LinearLayout bottom = horizontal();
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(8), dp(4), dp(8), dp(4));
        bottom.setBackground(stroke("#DCE5F2", "#FFFFFF", 18));
        bottom.setElevation(dp(2));
        bottom.addView(navText("首页", true));
        bottom.addView(navText("任务", false));
        bottom.addView(navText("扫码", false));
        bottom.addView(navText("消息", false));
        bottom.addView(navText("我的", false));
        addMargin(content, bottom, 0, 12, 0, 0);

        refreshQueue();
        setContentView(scroll);
    }

    // 将当前扫描数据加入离线缓存，网络不可用时不会丢失任务。
    private void cacheEvent() {
        try {
            String packageCode = packageInput.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (packageCode.isEmpty()) {
                setMessage("请扫描包条码");
                return;
            }
            JSONArray queue = readQueue();
            JSONObject item = new JSONObject();
            item.put("type", "recycle");
            // 标记业务来源为 PDA，后台用它和 Web 管理端做权限边界区分。
            item.put("clientType", "PDA");
            item.put("packageCode", packageCode);
            item.put("basketCode", basketInput.getText().toString().trim().toUpperCase(Locale.ROOT));
            item.put("deptCode", deptInput.getText().toString().trim().toUpperCase(Locale.ROOT));
            item.put("occurredAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
            queue.put(item);
            saveQueue(queue);
            packageInput.setText("");
            refreshQueue();
            setMessage("已缓存，等待内网 WiFi 上传");
        } catch (Exception ex) {
            setMessage("缓存失败：" + ex.getMessage());
        }
    }

    // 联网后把本地缓存批量上传到医院内网服务器。
    private void syncEvents() {
        new Thread(() -> {
            try {
                JSONArray queue = readQueue();
                JSONObject body = new JSONObject();
                body.put("deviceCode", "PDA-ANDROID-01");
                body.put("clientType", "PDA");
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
                        setMessage("上传完成：" + data.optInt("successCount") + " 条成功，" + data.optInt("failedCount") + " 条失败");
                    });
                } else {
                    runOnUiThread(() -> setMessage("上传失败：" + response.optString("message")));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> setMessage("无法连接内网服务器：" + ex.getMessage()));
            }
        }).start();
    }

    // 执行 JSON POST 请求，供离线缓存同步复用。
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

    // 从 SharedPreferences 读取离线队列。
    private JSONArray readQueue() {
        try {
            String text = getPreferences(MODE_PRIVATE).getString(CACHE_KEY, "[]");
            return new JSONArray(text);
        } catch (Exception ex) {
            return new JSONArray();
        }
    }

    // 保存离线队列到 SharedPreferences。
    private void saveQueue(JSONArray queue) {
        getPreferences(MODE_PRIVATE).edit().putString(CACHE_KEY, queue.toString()).apply();
    }

    // 刷新待同步数量和最近任务列表。
    private void refreshQueue() {
        JSONArray queue = readQueue();
        if (syncCountText != null) {
            syncCountText.setText("待同步记录：" + queue.length() + " 条");
        }
        if (taskListText != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("下收任务    包布包    P20240528001    进行中\n");
            sb.append("下送任务    手术包    P20240528002    进行中\n");
            sb.append("数据同步    共 ").append(queue.length()).append(" 条记录待同步");
            taskListText.setText(sb.toString());
        }
    }

    // 更新页面底部的操作反馈文案。
    private void setMessage(String message) {
        if (messageText != null) {
            messageText.setText(message);
        }
    }

    // 创建垂直布局容器，减少重复 UI 代码。
    private LinearLayout vertical(int gap) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (gap > 0) {
            layout.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        }
        return layout;
    }

    // 创建水平布局容器，常用于统计卡片和功能宫格。
    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    // 创建统一白色卡片背景。
    private LinearLayout card() {
        LinearLayout layout = vertical(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(1, Color.rgb(220, 228, 240));
        layout.setBackground(bg);
        layout.setElevation(dp(3));
        return layout;
    }

    // 创建普通文本控件。
    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        tv.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return tv;
    }

    // 创建表单字段标签。
    private TextView label(String value) {
        TextView tv = text(value, 15, "#475569", false);
        tv.setPadding(0, dp(12), 0, dp(6));
        return tv;
    }

    // 创建类似下拉选择器的医院文本控件。
    private TextView selectorText(String value) {
        TextView tv = text(value + "      ˅", 18, "#111827", true);
        tv.setPadding(dp(14), dp(14), dp(14), dp(14));
        tv.setBackground(stroke("#CBD5E1", "#FFFFFF", 12));
        return tv;
    }

    // 创建输入框控件。
    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(17);
        edit.setSingleLine(true);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setTextColor(Color.parseColor("#111827"));
        edit.setHintTextColor(Color.parseColor("#94A3B8"));
        edit.setBackground(stroke("#CBD5E1", "#FFFFFF", 12));
        addMargin(edit, 0, 0, 0, 10);
        return edit;
    }

    // 创建主按钮。
    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(20);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(stroke("#2563EB", "#2563EB", 14));
        button.setElevation(dp(2));
        addMargin(button, 0, 16, 0, 10);
        return button;
    }

    // 创建首页顶部统计卡。
    private TextView statusCard(String title, String value, String bgColor, String textColor) {
        TextView tv = text(title + "\n" + value, 18, textColor, true);
        tv.setPadding(dp(10), dp(12), dp(10), dp(12));
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(stroke("#00000000", bgColor, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(92), 1);
        lp.setMargins(dp(4), dp(4), dp(4), dp(12));
        tv.setLayoutParams(lp);
        return tv;
    }

    // 创建常用功能宫格按钮。
    private TextView actionTile(String title, String color, View.OnClickListener listener) {
        TextView tv = text(title, 16, "#111827", true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(18), dp(8), dp(18));
        tv.setBackground(stroke(color, "#FFFFFF", 14));
        tv.setElevation(dp(1));
        tv.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), 1);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        tv.setLayoutParams(lp);
        return tv;
    }

    // 创建区块标题。
    private TextView sectionTitle(String value) {
        TextView tv = text(value, 20, "#111827", true);
        tv.setPadding(0, dp(16), 0, dp(8));
        return tv;
    }

    // 创建底部导航文本。
    private TextView navText(String value, boolean active) {
        TextView tv = text(value, 14, active ? "#2563EB" : "#64748B", true);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1);
        tv.setLayoutParams(lp);
        return tv;
    }

    // 创建圆角描边背景。
    private GradientDrawable stroke(String strokeColor, String fillColor, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(fillColor));
        if (!"#00000000".equals(strokeColor)) {
            bg.setStroke(1, Color.parseColor(strokeColor));
        }
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    // 创建渐变背景。
    private GradientDrawable gradient(String start, String end) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
    }

    // 给父容器添加子控件并设置外边距。
    private void addMargin(LinearLayout parent, View child, int left, int top, int right, int bottom) {
        addMargin(child, left, top, right, bottom);
        parent.addView(child);
    }

    // 给控件设置外边距。
    private void addMargin(View child, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        child.setLayoutParams(lp);
    }

    // dp 转像素，保证不同 PDA 分辨率下尺寸稳定。
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
