package com.limelight.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * 配置分享码页：上半部分生成（所有人可用），下半部分导入（Pro 专属）。
 *
 * 生成：调 ConfigCodec.encode(ConfigBundle.toJson(this))，显示在 TextView 中，长按复制。
 * 导入：先 ConfigBackupHelper.backupBeforeApply 备份，弹 AlertDialog 确认，
 *       再 ConfigCodec.decode + ConfigBundle.fromJson 写入，失败 catch 异常 Toast。
 */
public class ShareCodeActivity extends Activity {

    // 配色，与项目其他 Activity 风格保持一致
    private static final int COLOR_BG = Color.parseColor("#0d1117");
    private static final int COLOR_TEXT = Color.parseColor("#FFFFFF");
    private static final int COLOR_HINT = Color.parseColor("#8b949e");
    private static final int COLOR_SECTION = Color.parseColor("#8b949e");
    private static final int COLOR_BUTTON_BG = Color.parseColor("#4FC3F7");
    private static final int COLOR_BUTTON_TEXT = Color.parseColor("#000000");
    private static final int COLOR_CODE_BG = Color.parseColor("#161b22");

    private TextView codeOutput;
    private EditText codeInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 外层 ScrollView 包裹
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(48));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 上半部分：生成分享码（所有人可用）
        buildGenerateSection(root);
        // 下半部分：导入分享码（Pro 专属）
        buildImportSection(root);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    /** 上半部分：生成配置分享码 */
    private void buildGenerateSection(LinearLayout root) {
        // section 标题
        root.addView(sectionLabel("生成配置分享码"));

        // 说明文字
        TextView desc = new TextView(this);
        desc.setText("将当前按键与陀螺仪配置编码为分享码，可发送给他人");
        desc.setTextColor(COLOR_HINT);
        desc.setTextSize(13);
        desc.setPadding(0, dp(4), 0, dp(12));
        root.addView(desc);

        // "生成"按钮
        Button genBtn = new Button(this);
        genBtn.setText("生成");
        genBtn.setBackgroundColor(COLOR_BUTTON_BG);
        genBtn.setTextColor(COLOR_BUTTON_TEXT);
        genBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        genBtn.setOnClickListener(v -> onGenerate());
        root.addView(genBtn);

        // 显示分享码的 TextView：monospace 字体，maxLines=8，可滚动
        codeOutput = new TextView(this);
        codeOutput.setTypeface(Typeface.MONOSPACE);
        codeOutput.setTextColor(COLOR_TEXT);
        codeOutput.setTextSize(12);
        codeOutput.setMaxLines(8);
        codeOutput.setVerticalScrollBarEnabled(true);
        codeOutput.setMovementMethod(new ScrollingMovementMethod());
        codeOutput.setBackgroundColor(COLOR_CODE_BG);
        codeOutput.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(160));
        outLp.topMargin = dp(12);
        codeOutput.setLayoutParams(outLp);
        // 长按复制到剪贴板
        codeOutput.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String text = codeOutput.getText().toString();
                if (text.isEmpty()) {
                    return false;
                }
                copyToClipboard(text);
                Toast.makeText(ShareCodeActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        root.addView(codeOutput);
    }

    /** 下半部分：导入配置分享码（Pro 专属） */
    private void buildImportSection(LinearLayout root) {
        // section 标题（带顶部间距）
        TextView sec = sectionLabel("导入配置分享码");
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = dp(24);
        sec.setLayoutParams(secLp);
        root.addView(sec);

        if (!ProManager.isProActivated(this)) {
            // 未激活：显示跳转激活按钮
            Button activateBtn = new Button(this);
            activateBtn.setText("导入功能需 Pro，点击激活");
            activateBtn.setBackgroundColor(COLOR_BUTTON_BG);
            activateBtn.setTextColor(COLOR_BUTTON_TEXT);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.topMargin = dp(8);
            activateBtn.setLayoutParams(btnLp);
            activateBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, ProActivationActivity.class)));
            root.addView(activateBtn);
            return;
        }

        // 已激活：输入框 + "导入"按钮
        codeInput = new EditText(this);
        codeInput.setHint("粘贴 DS-CONFIG-v1:...");
        codeInput.setHintTextColor(COLOR_HINT);
        codeInput.setTextColor(COLOR_TEXT);
        codeInput.setBackgroundColor(COLOR_CODE_BG);
        codeInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        // multiLine 输入
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        codeInput.setMinLines(4);
        codeInput.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inLp.topMargin = dp(8);
        codeInput.setLayoutParams(inLp);
        root.addView(codeInput);

        Button importBtn = new Button(this);
        importBtn.setText("导入");
        importBtn.setBackgroundColor(COLOR_BUTTON_BG);
        importBtn.setTextColor(COLOR_BUTTON_TEXT);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(8);
        importBtn.setLayoutParams(btnLp);
        importBtn.setOnClickListener(v -> onImport());
        root.addView(importBtn);
    }

    /** 生成分享码：编码当前配置并显示 */
    private void onGenerate() {
        try {
            String json = ConfigBundle.toJson(this);
            String code = ConfigCodec.encode(json);
            codeOutput.setText(code);
            Toast.makeText(this, "已生成", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "生成失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 导入分享码流程：
     * 1) ConfigBackupHelper.backupBeforeApply 备份当前配置
     * 2) 弹 AlertDialog 确认（提示已自动备份）
     * 3) 用户确认后 ConfigCodec.decode + ConfigBundle.fromJson 写入
     * 4) 成功 Toast "导入成功，请重启串流生效"
     * 5) 解码/写入失败 catch 异常 Toast 错误信息
     */
    private void onImport() {
        final String input = codeInput.getText().toString();
        if (input.trim().isEmpty()) {
            Toast.makeText(this, "请先粘贴分享码", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1) 先备份当前配置
        try {
            ConfigBackupHelper.backupBeforeApply(this);
        } catch (Exception e) {
            Toast.makeText(this, "备份失败，已中止：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // 2) 弹确认框
        new AlertDialog.Builder(this)
                .setTitle("导入确认")
                .setMessage("将覆盖当前配置，已自动备份，是否继续？")
                .setPositiveButton("继续", (d, w) -> {
                    try {
                        // 3) 解码 + 写入
                        String json = ConfigCodec.decode(input);
                        ConfigBundle.fromJson(this, json);
                        // 4) 成功提示
                        Toast.makeText(this, "导入成功，请重启串流生效", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        // 5) 解码/写入失败提示
                        Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 复制文本到系统剪贴板 */
    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DS-CONFIG", text));
        }
    }

    /** section 标题样式：灰色小字 */
    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_SECTION);
        tv.setTextSize(13);
        return tv;
    }

    /** dp → px 辅助方法 */
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
