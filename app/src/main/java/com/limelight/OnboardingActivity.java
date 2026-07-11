package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 首次启动引导页
 * 用户第一次打开 APP 时显示，介绍基本使用流程
 */
public class OnboardingActivity extends Activity {

    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_COMPLETED = "onboarding_completed";

    private int currentStep = 0;
    private final String[] titles = {
            "欢迎使用 DeltaStream",
            "第 1 步：电脑端安装 Sunshine",
            "第 2 步：连接电脑",
            "第 3 步：游戏内操作",
            "陀螺仪瞄准设置"
    };

    private final String[] contents = {
            "本 APP 可以让你用平板串流玩电脑上的三角洲行动，\n" +
            "支持 120fps 高帧率 + 陀螺仪瞄准 + 手游风格触控按键。\n\n" +
            "只需要 3 步即可开始游戏。",

            "在电脑上运行「一键安装.bat」\n" +
            "脚本会自动安装并配置 Sunshine 串流服务。\n\n" +
            "安装完成后，电脑会显示 IP 地址，\n" +
            "记住这个 IP（如 192.168.1.100）。\n\n" +
            "首次使用需要在电脑浏览器打开\n" +
            "https://localhost:47990 完成配对。",

            "1. 确保平板和电脑在同一个 WiFi 下\n" +
            "   （或用 USB 网络共享降低延迟）\n\n" +
            "2. 点击右上角「+」添加电脑\n\n" +
            "3. 输入电脑的 IP 地址\n\n" +
            "4. 首次连接需要在电脑端输入配对 PIN 码\n\n" +
            "5. 配对成功后，点击电脑图标即可开始串流",

            "进入游戏后，屏幕上会显示触控按键：\n\n" +
            "左下角圆圈 = 摇杆（移动）\n" +
            "右侧大区域 = 滑动控制视角\n" +
            "红色按钮 = 开火\n" +
            "蓝色按钮 = 开镜\n\n" +
            "短按右下角「设置」= 编辑按键位置\n" +
            "长按「设置」= 陀螺仪设置\n" +
            "长按任意按钮 = 拖动调整位置",

            "长按右下角「设置」按钮打开陀螺仪设置：\n\n" +
            "1. 开启陀螺仪瞄准开关\n\n" +
            "2. 转动平板，如果方向反了：\n" +
            "   - 上下变左右 → 打开「交换 X/Y 轴」\n" +
            "   - 方向相反 → 打开「反转水平/垂直」\n\n" +
            "3. 调整灵敏度到顺手为止\n\n" +
            "4. 游戏内鼠标灵敏度建议固定\n" +
            "   用 APP 内的陀螺仪灵敏度做微调"
    };

    private TextView titleText;
    private TextView contentText;
    private TextView stepIndicator;
    private Button prevBtn;
    private Button nextBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(createLayout());

        showStep();
    }

    private View createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 64, 48, 32);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        // 标题
        titleText = new TextView(this);
        titleText.setTextSize(22);
        titleText.setTextColor(Color.parseColor("#00d4ff"));
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 24);
        root.addView(titleText);

        // 步骤指示器
        stepIndicator = new TextView(this);
        stepIndicator.setTextSize(12);
        stepIndicator.setTextColor(Color.parseColor("#888888"));
        stepIndicator.setGravity(Gravity.CENTER);
        stepIndicator.setPadding(0, 0, 0, 24);
        root.addView(stepIndicator);

        // 内容（可滚动）
        ScrollView scroll = new ScrollView(this);
        contentText = new TextView(this);
        contentText.setTextSize(15);
        contentText.setTextColor(Color.parseColor("#e0e0e0"));
        contentText.setLineSpacing(4, 1.2f);
        contentText.setPadding(16, 16, 16, 16);
        contentText.setMovementMethod(ScrollingMovementMethod.getInstance());
        scroll.addView(contentText);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 24, 0, 0);

        prevBtn = new Button(this);
        prevBtn.setText("上一步");
        prevBtn.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                showStep();
            }
        });
        btnRow.addView(prevBtn);

        nextBtn = new Button(this);
        nextBtn.setText("下一步");
        nextBtn.setOnClickListener(v -> {
            if (currentStep < titles.length - 1) {
                currentStep++;
                showStep();
            } else {
                completeOnboarding();
            }
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nextParams.leftMargin = 32;
        btnRow.addView(nextBtn, nextParams);

        root.addView(btnRow);

        return root;
    }

    private void showStep() {
        titleText.setText(titles[currentStep]);
        contentText.setText(contents[currentStep]);
        stepIndicator.setText((currentStep + 1) + " / " + titles.length);

        prevBtn.setVisibility(currentStep == 0 ? View.INVISIBLE : View.VISIBLE);
        nextBtn.setText(currentStep == titles.length - 1 ? "开始使用" : "下一步");
    }

    private void completeOnboarding() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_COMPLETED, true).apply();

        Intent intent = new Intent(this, PcView.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    public static boolean isOnboardingCompleted(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_COMPLETED, false);
    }
}
