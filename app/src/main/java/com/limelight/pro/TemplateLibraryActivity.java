package com.limelight.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 配置模板库：从 GitHub 仓库拉取官方预设配置，一键应用。
 * 仓库：https://github.com/guo6x/deltastream-templates
 */
public class TemplateLibraryActivity extends Activity {

    private static final String RAW_BASE = "https://raw.githubusercontent.com/guo6x/deltastream-templates/main/";
    private static final String INDEX_URL = RAW_BASE + "index.json";

    private LinearLayout listContainer;
    private TextView statusText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.parseColor("#0d1117"));

        // 标题
        TextView title = new TextView(this);
        title.setText("配置模板库");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // Pro 检查
        if (!ProManager.isProActivated(this)) {
            TextView lockMsg = new TextView(this);
            lockMsg.setText("模板库为 Pro 功能。点击下方按钮激活 Pro。");
            lockMsg.setTextColor(Color.parseColor("#8B949E"));
            lockMsg.setPadding(0, 0, 0, dp(16));
            root.addView(lockMsg);

            Button activateBtn = new Button(this);
            activateBtn.setText("激活 Pro");
            activateBtn.setBackgroundColor(Color.parseColor("#4FC3F7"));
            activateBtn.setTextColor(Color.BLACK);
            activateBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, ProActivationActivity.class));
                finish();
            });
            root.addView(activateBtn);
            scroll.addView(root);
            setContentView(scroll);
            return;
        }

        // 状态文本
        statusText = new TextView(this);
        statusText.setText("正在加载模板...");
        statusText.setTextColor(Color.parseColor("#8B949E"));
        statusText.setPadding(0, 0, 0, dp(16));
        root.addView(statusText);

        // 进度条
        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        LinearLayout pbWrap = new LinearLayout(this);
        pbWrap.setGravity(Gravity.CENTER);
        pbWrap.setPadding(0, dp(8), 0, dp(16));
        pbWrap.addView(progressBar);
        root.addView(pbWrap);

        // 模板列表容器
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        scroll.addView(root);
        setContentView(scroll);

        // 加载模板列表
        fetchTemplates();
    }

    private void fetchTemplates() {
        new Thread(() -> {
            try {
                URL url = new URL(INDEX_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                String response = readAll(conn);
                conn.disconnect();

                if (code != 200) {
                    runOnUiThread(() -> {
                        statusText.setText("加载失败: HTTP " + code);
                        progressBar.setVisibility(ProgressBar.GONE);
                    });
                    return;
                }

                JSONObject root = new JSONObject(response);
                JSONArray templates = root.optJSONArray("templates");
                if (templates == null || templates.length() == 0) {
                    runOnUiThread(() -> {
                        statusText.setText("暂无可用模板");
                        progressBar.setVisibility(ProgressBar.GONE);
                    });
                    return;
                }

                runOnUiThread(() -> {
                    statusText.setText("共 " + templates.length() + " 个模板");
                    progressBar.setVisibility(ProgressBar.GONE);
                    listContainer.removeAllViews();

                    for (int i = 0; i < templates.length(); i++) {
                        try {
                            JSONObject tpl = templates.getJSONObject(i);
                            addTemplateRow(tpl);
                        } catch (Exception ignored) {
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("加载失败: " + e.getMessage());
                    progressBar.setVisibility(ProgressBar.GONE);
                });
            }
        }).start();
    }

    private void addTemplateRow(final JSONObject tpl) {
        String id = tpl.optString("id", "");
        String game = tpl.optString("game", "");
        String name = tpl.optString("name", "");
        String desc = tpl.optString("description", "");
        final String path = tpl.optString("path", "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Color.parseColor("#161b22"));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView gameText = new TextView(this);
        gameText.setText(game + " - " + name);
        gameText.setTextColor(Color.WHITE);
        gameText.setTextSize(16);
        row.addView(gameText);

        if (!desc.isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(desc);
            descText.setTextColor(Color.parseColor("#8B949E"));
            descText.setTextSize(13);
            descText.setPadding(0, dp(4), 0, dp(8));
            row.addView(descText);
        }

        Button applyBtn = new Button(this);
        applyBtn.setText("应用此模板");
        applyBtn.setBackgroundColor(Color.parseColor("#4FC3F7"));
        applyBtn.setTextColor(Color.BLACK);
        applyBtn.setOnClickListener(v -> confirmApply(path, name));
        row.addView(applyBtn);

        listContainer.addView(row);
    }

    private void confirmApply(final String path, final String name) {
        new AlertDialog.Builder(this)
                .setTitle("应用模板")
                .setMessage("将覆盖当前配置（已自动备份），是否继续？\n模板: " + name)
                .setPositiveButton("应用", (d, w) -> applyTemplate(path))
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyTemplate(final String path) {
        // 先备份
        ConfigBackupHelper.backupBeforeApply(this);

        new Thread(() -> {
            try {
                URL url = new URL(RAW_BASE + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                String content = readAll(conn);
                conn.disconnect();

                if (code != 200) {
                    runOnUiThread(() -> Toast.makeText(this, "下载模板失败: HTTP " + code, Toast.LENGTH_SHORT).show());
                    return;
                }

                ConfigBundle.fromJson(this, content);
                runOnUiThread(() -> Toast.makeText(this, "应用成功，请重启串流生效", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "应用失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String readAll(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
