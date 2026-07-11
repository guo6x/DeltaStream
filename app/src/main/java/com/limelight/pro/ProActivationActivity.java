package com.limelight.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pro 赞助者激活页。
 *
 * 5 层"不留痕"设计：
 * 1. onCreate 设置 FLAG_SECURE，禁止系统截图与最近任务缩略图
 * 2. 订单号输入框使用密码模式，显示圆点，防止肩窥
 * 3. 激活成功后立即 setText("") 清空输入框并 finish() 返回
 * 4. 不存订单号本身，只调 ProManager.setProActivated(this, true) 翻转布尔标志
 * 5. 已激活状态页只显示"✓ Pro 已激活"，不显示任何凭证
 */
public class ProActivationActivity extends Activity {

    // 深色背景与配色，与项目其他 Activity 风格保持一致
    private static final int COLOR_BG = Color.parseColor("#0d1117");
    private static final int COLOR_TEXT = Color.parseColor("#FFFFFF");
    private static final int COLOR_HINT = Color.parseColor("#8b949e");
    private static final int COLOR_BUTTON_BG = Color.parseColor("#4FC3F7");
    private static final int COLOR_BUTTON_TEXT = Color.parseColor("#000000");

    private EditText orderInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 第 1 层：FLAG_SECURE 防截图
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        // 外层 ScrollView 包裹
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题
        TextView title = new TextView(this);
        title.setText("Pro 赞助者激活");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // 说明文字
        TextView desc = new TextView(this);
        desc.setText("请在爱发电捐赠后，将订单号输入下方框中激活 Pro。本页无法截图，请直接输入。");
        desc.setTextColor(COLOR_HINT);
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, dp(16));
        root.addView(desc);

        if (ProManager.isProActivated(this)) {
            // 第 5 层：已激活只显示状态文字，不显示输入框/按钮/凭证
            TextView status = new TextView(this);
            status.setText("✓ Pro 已激活");
            status.setTextColor(COLOR_TEXT);
            status.setTextSize(16);
            status.setGravity(Gravity.CENTER);
            status.setPadding(0, dp(24), 0, dp(24));
            root.addView(status);
        } else {
            // 未激活：显示输入框与两个按钮
            buildActivationControls(root);
        }

        scrollView.addView(root);
        setContentView(scrollView);
    }

    /** 构建未激活态下的输入框 + 激活按钮 + 捐赠按钮 */
    private void buildActivationControls(LinearLayout root) {
        // 第 2 层：订单号输入框密码模式，显示圆点
        orderInput = new EditText(this);
        orderInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        orderInput.setHint("afd-xxxxxxxxxxxxxxxx");
        orderInput.setHintTextColor(COLOR_HINT);
        orderInput.setTextColor(COLOR_TEXT);
        orderInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        orderInput.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(orderInput);

        // "激活"按钮
        Button activateBtn = new Button(this);
        activateBtn.setText("激活");
        activateBtn.setBackgroundColor(COLOR_BUTTON_BG);
        activateBtn.setTextColor(COLOR_BUTTON_TEXT);
        LinearLayout.LayoutParams activateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        activateLp.topMargin = dp(12);
        activateBtn.setLayoutParams(activateLp);
        activateBtn.setOnClickListener(v -> onActivate());
        root.addView(activateBtn);

        // "前往爱发电捐赠"按钮
        Button donateBtn = new Button(this);
        donateBtn.setText("前往爱发电捐赠");
        donateBtn.setBackgroundColor(COLOR_BUTTON_BG);
        donateBtn.setTextColor(COLOR_BUTTON_TEXT);
        LinearLayout.LayoutParams donateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        donateLp.topMargin = dp(12);
        donateBtn.setLayoutParams(donateLp);
        donateBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://afdian.net")));
            } catch (Exception e) {
                Toast.makeText(this, "未找到浏览器", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(donateBtn);
    }

    /** 激活按钮回调：校验订单号，通过则翻转标志 + 清空 + finish */
    private void onActivate() {
        String order = orderInput.getText().toString();
        if (ProManager.validateOrderNumber(order)) {
            // 第 4 层：只翻转布尔标志，不存订单号本身
            ProManager.setProActivated(this, true);
            Toast.makeText(this, "Pro 已激活", Toast.LENGTH_SHORT).show();
            // 第 3 层：成功后立即清空输入框并 finish
            orderInput.setText("");
            finish();
        } else {
            Toast.makeText(this, "订单号格式不正确", Toast.LENGTH_SHORT).show();
        }
    }

    /** dp → px 辅助方法 */
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
