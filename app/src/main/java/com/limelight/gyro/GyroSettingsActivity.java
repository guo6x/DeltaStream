package com.limelight.gyro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.touch.TouchGamepadConfig;

/**
 * 陀螺仪瞄准设置界面 — 复刻三角洲行动的陀螺仪设置项。
 *
 * 设置项：
 *   1. 总开关
 *   2. 触发方式（始终/开镜/开火）
 *   3. 全局灵敏度 (10-200)
 *   4. 垂直灵敏度 (10-200)
 *   5. 水平灵敏度 (10-200)
 *   6. 开火灵敏度 (10-200)
 *   7. 开火垂直灵敏度 (10-200)
 *   8. MDV (0.1-3.0)
 *   9. 开火 MDV (0.1-3.0)
 *  10. 死区 (0-0.2)
 *  11. 平滑 (0-0.9)
 *  12. 缩放系数 (50-800)
 *  13. 灵敏度过渡时间 (0-500ms)
 *  14. 开火后坐力补偿 (0-5.0)
 *  15. 开镜垂直倍率 (0.2-2.0)
 *  16. 恢复默认 / 保存
 */
public class GyroSettingsActivity extends Activity {

    private GyroAimSettings settings;
    private TouchGamepadConfig touchConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = GyroAimSettings.load(this);
        touchConfig = TouchGamepadConfig.load(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        // 标题
        root.addView(titleView("灵敏度设置"));
        root.addView(subtitleView("长按设置按钮进入 | 含陀螺仪灵敏度 + 手搓（触摸瞄准）灵敏度"));

        // 1. 总开关
        final Switch enableSwitch = new Switch(this);
        enableSwitch.setText("启用陀螺仪瞄准");
        enableSwitch.setChecked(settings.enabled);
        enableSwitch.setOnCheckedChangeListener((b, checked) -> settings.enabled = checked);
        root.addView(enableSwitch, paddingParams());

        // 2. 触发方式
        root.addView(sectionLabel("陀螺仪触发方式"));
        final TextView triggerText = new TextView(this);
        String[] triggerNames = {"始终开启", "开镜时开启", "开火时开启"};
        Runnable updateTriggerText = () ->
                triggerText.setText("当前: " + triggerNames[settings.triggerMode]);
        updateTriggerText.run();
        triggerText.setPadding(dp(16), dp(8), 0, dp(8));
        triggerText.setOnClickListener(v -> {
            settings.triggerMode = (settings.triggerMode + 1) % 3;
            updateTriggerText.run();
        });
        root.addView(triggerText);
        root.addView(hintView("点击切换（始终/开镜/开火）"));

        // 灵敏度组
        root.addView(sectionLabel("陀螺仪灵敏度"));

        // 3. 全局灵敏度
        root.addView(sliderRow("陀螺仪全局灵敏度", settings.sensGlobal, 10, 2000, 1,
                v -> settings.sensGlobal = (int) v, "110"));

        // 4. 垂直灵敏度
        root.addView(sliderRow("陀螺仪垂直灵敏度", settings.sensVertical, 10, 2000, 1,
                v -> settings.sensVertical = (int) v, "100"));

        // 5. 水平灵敏度
        root.addView(sliderRow("陀螺仪水平灵敏度", settings.sensHorizontal, 10, 2000, 1,
                v -> settings.sensHorizontal = (int) v, "160"));

        // 6. 开火灵敏度
        root.addView(sliderRow("陀螺仪开火灵敏度", settings.sensFiring, 10, 2000, 1,
                v -> settings.sensFiring = (int) v, "150"));

        // 7. 开火垂直灵敏度
        root.addView(sliderRow("陀螺仪开火垂直灵敏度", settings.sensFiringVertical, 10, 2000, 1,
                v -> settings.sensFiringVertical = (int) v, "120"));

        // 8. 开火水平灵敏度
        root.addView(sliderRow("陀螺仪开火水平灵敏度", settings.sensFiringHorizontal, 10, 2000, 1,
                v -> settings.sensFiringHorizontal = (int) v, "90"));

        // MDV 组
        root.addView(sectionLabel("陀螺仪 MDV 灵敏度倍率"));

        // 9. MDV
        root.addView(sliderRow("陀螺仪 MDV", settings.mdv, 0.1f, 10.0f, 0.01f,
                v -> settings.mdv = v, "1.6"));

        // 10. 开火 MDV
        root.addView(sliderRow("陀螺仪开火 MDV", settings.mdvFiring, 0.1f, 10.0f, 0.01f,
                v -> settings.mdvFiring = v, "1.6"));

        // 高级组
        root.addView(sectionLabel("陀螺仪高级设置"));

        // 10. 旧死区（兼容，新逻辑用下面的收紧型阈值）
        root.addView(sliderRow("陀螺仪死区 (rad/s, 旧)", settings.deadzone, 0f, 0.2f, 0.005f,
                v -> settings.deadzone = v, "0.02"));

        // 11. 旧平滑系数（兼容，新逻辑用下面的软分层平滑）
        root.addView(sliderRow("陀螺仪平滑系数 (旧)", settings.smoothing, 0f, 0.9f, 0.05f,
                v -> settings.smoothing = v, "0.2"));

        // ===== 新增：收紧型阈值 + 软分层平滑（参考 GyroWiki） =====
        root.addView(sectionLabel("陀螺仪收紧阈值与软平滑（推荐调整）"));
        root.addView(hintView("收紧型阈值：低于此速度平滑衰减而非直接归零，不丢真实输入。"));
        root.addView(hintView("软分层平滑：小输入平滑（压手抖），大输入直通（保响应），优于 EMA。"));

        // 收紧型阈值速度
        root.addView(sliderRow("陀螺仪收紧阈值速度", settings.cutoffSpeed, 0f, 0.5f, 0.005f,
                v -> settings.cutoffSpeed = v, "0.05"));

        // 收紧型恢复宽度
        root.addView(sliderRow("陀螺仪收紧恢复宽度", settings.cutoffRecovery, 0f, 0.5f, 0.005f,
                v -> settings.cutoffRecovery = v, "0.04"));

        // 软分层平滑阈值
        root.addView(sliderRow("陀螺仪软平滑阈值", settings.softSmoothThreshold, 0f, 1.0f, 0.01f,
                v -> settings.softSmoothThreshold = v, "0.15"));
        root.addView(hintView("阈值越小=越早直通（响应快但手抖多），越大=平滑范围越大（稳但响应慢）。"));

        // 12. 缩放系数
        root.addView(sliderRow("陀螺仪全局缩放", settings.scale, 50f, 5000f, 10f,
                v -> settings.scale = v, "300"));

        // 13. 灵敏度过渡时间
        root.addView(sliderRow("陀螺仪 ADS/开火过渡时间 (ms)", settings.transitionTimeMs, 0f, 500f, 10f,
                v -> settings.transitionTimeMs = (int) v, "120"));

        // 14. 开火后坐力补偿强度（已移至下方"一键压枪"分组，此处保留兼容旧版用户）
        // 实际生效需同时开启下方"启用一键压枪"开关
        root.addView(sliderRow("陀螺仪开火后坐力补偿强度", settings.recoilCompensation, 0f, 5.0f, 0.1f,
                v -> settings.recoilCompensation = v, "0"));

        // 15. 开镜垂直倍率
        root.addView(sliderRow("陀螺仪开镜垂直倍率", settings.aimVerticalScale, 0.2f, 2.0f, 0.05f,
                v -> settings.aimVerticalScale = v, "1.0"));

        // ===== 开镜(ADS)自动降灵敏度组 =====
        root.addView(sectionLabel("开镜自动降灵敏度（推荐开启）"));
        root.addView(hintView("开镜时自动切换到更低的灵敏度组，远距离瞄准更稳。"));
        root.addView(hintView("优先级：开镜ADS > 开火 > 常规。开镜+开火时用 ADS 组。"));

        final Switch adsSwitch = new Switch(this);
        adsSwitch.setText("启用开镜自动降灵敏度");
        adsSwitch.setChecked(settings.adsSensEnabled);
        adsSwitch.setOnCheckedChangeListener((b, checked) -> settings.adsSensEnabled = checked);
        root.addView(adsSwitch, paddingParams());

        root.addView(sliderRow("陀螺仪开镜水平灵敏度", settings.sensAdsHorizontal, 10, 2000, 1,
                v -> settings.sensAdsHorizontal = (int) v, "100"));
        root.addView(sliderRow("陀螺仪开镜垂直灵敏度", settings.sensAdsVertical, 10, 2000, 1,
                v -> settings.sensAdsVertical = (int) v, "60"));
        root.addView(sliderRow("陀螺仪开镜 MDV", settings.mdvAds, 0.1f, 10.0f, 0.01f,
                v -> settings.mdvAds = v, "1.0"));

        // ===== 一键压枪（后坐力补偿）=====
        root.addView(sectionLabel("一键压枪（后坐力补偿）"));
        root.addView(hintView("开火时自动给鼠标向下的移动，补偿枪械后坐力。"));
        root.addView(hintView("需同时开启此开关且强度>0 才生效。不同枪强度不同，建议实战微调。"));

        final Switch recoilSwitch = new Switch(this);
        recoilSwitch.setText("启用一键压枪");
        recoilSwitch.setChecked(settings.recoilEnabled);
        recoilSwitch.setOnCheckedChangeListener((b, checked) -> settings.recoilEnabled = checked);
        root.addView(recoilSwitch, paddingParams());

        root.addView(sliderRow("压枪强度", settings.recoilCompensation, 0f, 5.0f, 0.1f,
                v -> settings.recoilCompensation = v, "0"));
        root.addView(hintView("强度建议：步枪 1.0-2.0，冲锋枪 0.5-1.0，狙击 0。实战中微调。"));

        // ===== 性能叠加层引导 =====
        root.addView(sectionLabel("性能叠加层（查看延迟/帧率）"));
        root.addView(hintView("DeltaStream 设置页已有'显示性能叠加层'开关，开启后串流画面上显示实际延迟/帧率/丢帧/RTT。"));
        root.addView(hintView("路径：DeltaStream 主界面 → 右上角齿轮 → 视频帧节奏分类 → 显示性能叠加层。"));
        root.addView(hintView("建议开启以验证当前延迟，优化效果看得见。"));

        // ===== 手搓（触摸瞄准）灵敏度 =====
        // 手搓与陀螺仪完全独立：手搓是右半屏滑动控制视角，陀螺仪是转动设备控制视角
        root.addView(sectionLabel("手搓（触摸瞄准）灵敏度"));
        root.addView(hintView("以下为右半屏滑动瞄准的参数，与陀螺仪完全独立。"));

        final Switch touchAimSwitch = new Switch(this);
        touchAimSwitch.setText("启用手搓（触摸瞄准）");
        touchAimSwitch.setChecked(touchConfig.touchAimEnabled);
        touchAimSwitch.setOnCheckedChangeListener((b, checked) -> touchConfig.touchAimEnabled = checked);
        root.addView(touchAimSwitch, paddingParams());

        // 手搓灵敏度（上限尽量高）
        root.addView(sliderRow("手搓灵敏度", touchConfig.touchAimSensitivity, 0.1f, 30.0f, 0.1f,
                v -> touchConfig.touchAimSensitivity = v, "2.2"));

        // 手搓垂直缩放
        root.addView(sliderRow("手搓垂直缩放", touchConfig.touchAimVerticalScale, 0.1f, 3.0f, 0.05f,
                v -> touchConfig.touchAimVerticalScale = v, "0.85"));

        // 触摸瞄准区起始比例（右半屏区域大小）
        root.addView(sliderRow("手搓区域起始比例", touchConfig.touchAimAreaLeftRatio, 0.1f, 0.8f, 0.05f,
                v -> touchConfig.touchAimAreaLeftRatio = v, "0.35"));

        root.addView(hintView("手搓灵敏度：右半屏滑动的鼠标移动倍率，值越大视角转得越快。"));
        root.addView(hintView("手搓垂直缩放：上下滑动相对于左右的缩放（小于1=上下更慢，适合压枪）。"));
        root.addView(hintView("手搓区域起始比例：从屏幕宽度多少比例开始算触摸瞄准区（0.35=右侧65%区域）。"));

        // 轴映射修正
        root.addView(sectionLabel("轴映射修正"));
        final Switch swapAxesSwitch = new Switch(this);
        swapAxesSwitch.setText("交换 X/Y 轴（上下↔左右）");
        swapAxesSwitch.setChecked(settings.swapAxes);
        swapAxesSwitch.setOnCheckedChangeListener((b, checked) -> settings.swapAxes = checked);
        root.addView(swapAxesSwitch, paddingParams());

        final Switch invertXSwitch = new Switch(this);
        invertXSwitch.setText("反转水平方向");
        invertXSwitch.setChecked(settings.invertX);
        invertXSwitch.setOnCheckedChangeListener((b, checked) -> settings.invertX = checked);
        root.addView(invertXSwitch, paddingParams());

        final Switch invertYSwitch = new Switch(this);
        invertYSwitch.setText("反转垂直方向");
        invertYSwitch.setChecked(settings.invertY);
        invertYSwitch.setOnCheckedChangeListener((b, checked) -> settings.invertY = checked);
        root.addView(invertYSwitch, paddingParams());

        root.addView(hintView("缩放系数决定整体移动速度。值越大，同灵敏度下鼠标移动越快。"));
        root.addView(hintView("如果游戏内鼠标移动太慢，增大缩放；太快则减小。"));
        root.addView(hintView("过渡时间让开镜/开火时灵敏度切换更平滑，避免突变。"));
        root.addView(hintView("后坐力补偿会在开火时自动下压准星（0=关闭）。"));

        // 自动校准按钮
        root.addView(hintView("校准：将平板静止放置 2 秒，自动消除传感器零漂。"));
        Button calibBtn = new Button(this);
        calibBtn.setText(settings.calibrated ? "重新校准陀螺仪" : "校准陀螺仪（首次使用必做）");
        calibBtn.setOnClickListener(v -> {
            // 进入校准模式，2 秒后自动完成
            calibBtn.setText("校准中... 请保持平板静止");
            calibBtn.setEnabled(false);
            com.limelight.gyro.GyroAimManager mgr = new com.limelight.gyro.GyroAimManager(this, (dx, dy) -> {});
            mgr.setSettings(settings);
            mgr.start();
            mgr.startCalibration(new com.limelight.gyro.GyroAimManager.CalibrateCallback() {
                @Override
                public void onCalibrateProgress(int collected, int total) {
                    runOnUiThread(() -> calibBtn.setText("校准中... " + collected + "/" + total));
                }

                @Override
                public void onCalibrateComplete() {
                    mgr.stop();
                    runOnUiThread(() -> {
                        calibBtn.setText("校准完成");
                        calibBtn.setEnabled(true);
                        Toast.makeText(GyroSettingsActivity.this, "陀螺仪校准完成", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        root.addView(calibBtn, paddingParams());

        // ===== Pro 赞助者区块 =====
        root.addView(sectionLabel("Pro 赞助者"));
        root.addView(hintView("Pro 功能：云配置同步 / 配置模板库 / 配置分享码导入。"));
        root.addView(hintView("所有核心玩法功能永久免费，Pro 只增加便利服务，不锁功能。"));

        final boolean proActivated = com.limelight.pro.ProManager.isProActivated(this);
        TextView proStatus = new TextView(this);
        proStatus.setText(proActivated ? "✓ Pro 已激活" : "未激活 Pro");
        proStatus.setTextColor(proActivated ? Color.parseColor("#3FB950") : Color.parseColor("#8B949E"));
        proStatus.setTextSize(14);
        proStatus.setPadding(dp(16), dp(8), 0, dp(8));
        root.addView(proStatus);

        Button activateBtn = new Button(this);
        activateBtn.setText(proActivated ? "管理 Pro" : "输入订单号激活 Pro");
        activateBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.limelight.pro.ProActivationActivity.class);
            startActivity(intent);
        });
        root.addView(activateBtn, paddingParams());

        Button shareCodeBtn = new Button(this);
        shareCodeBtn.setText("配置分享码（生成/导入）");
        shareCodeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.limelight.pro.ShareCodeActivity.class);
            startActivity(intent);
        });
        root.addView(shareCodeBtn, paddingParams());

        Button templateBtn = new Button(this);
        templateBtn.setText("配置模板库（一键应用）");
        templateBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.limelight.pro.TemplateLibraryActivity.class);
            startActivity(intent);
        });
        root.addView(templateBtn, paddingParams());

        Button cloudSyncBtn = new Button(this);
        cloudSyncBtn.setText("云配置同步（备份/恢复）");
        cloudSyncBtn.setOnClickListener(v -> showCloudSyncDialog());
        root.addView(cloudSyncBtn, paddingParams());

        Button donateBtn = new Button(this);
        donateBtn.setText("前往爱发电捐赠");
        donateBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://afdian.net"));
            startActivity(intent);
        });
        root.addView(donateBtn, paddingParams());

        // 按钮组
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(16), 0, dp(16));

        Button resetBtn = new Button(this);
        resetBtn.setText("恢复默认");
        resetBtn.setOnClickListener(v -> {
            settings.resetToDefaults();
            settings.save(this);
            touchConfig.resetToDefaults();
            touchConfig.save(this);
            Toast.makeText(this, "已恢复默认设置（含手搓），请重新打开设置页", Toast.LENGTH_SHORT).show();
            recreate();
        });
        btnRow.addView(resetBtn, buttonParams());

        Button saveBtn = new Button(this);
        saveBtn.setText("保存并返回");
        saveBtn.setOnClickListener(v -> {
            settings.save(this);
            touchConfig.save(this);
            Toast.makeText(this, "设置已保存（含手搓）", Toast.LENGTH_SHORT).show();
            finish();
        });
        btnRow.addView(saveBtn, buttonParams());

        root.addView(btnRow);

        scroll.addView(root);
        setContentView(scroll);
    }

    /** 云同步对话框：输入 Token 和 Gist ID，备份/恢复 */
    private void showCloudSyncDialog() {
        if (!com.limelight.pro.ProManager.isProActivated(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("云配置同步")
                    .setMessage("云同步为 Pro 功能，请先激活 Pro。")
                    .setPositiveButton("激活 Pro", (d, w) ->
                            startActivity(new Intent(this, com.limelight.pro.ProActivationActivity.class)))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("GitHub Token（仅 gist 权限）");
        tokenLabel.setTextColor(Color.WHITE);
        layout.addView(tokenLabel);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("ghp_xxxxxxxx");
        tokenInput.setText(com.limelight.pro.CloudSyncManager.getToken(this));
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(tokenInput);

        TextView gistLabel = new TextView(this);
        gistLabel.setText("Gist ID（恢复时填，备份时留空自动创建）");
        gistLabel.setTextColor(Color.WHITE);
        gistLabel.setPadding(0, dp(10), 0, 0);
        layout.addView(gistLabel);

        EditText gistInput = new EditText(this);
        gistInput.setHint("留空 = 新建 Gist");
        String savedGistId = com.limelight.pro.CloudSyncManager.getGistId(this);
        if (!savedGistId.isEmpty()) gistInput.setText(savedGistId);
        gistInput.setSingleLine(true);
        layout.addView(gistInput);

        TextView hint = new TextView(this);
        hint.setText("Token 存本地不上传。Gist ID 是云端配置的标识，换设备恢复时填入。");
        hint.setTextSize(11);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(8), 0, 0);
        layout.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("云配置同步")
                .setView(layout)
                .setPositiveButton("备份到云端", (d, w) -> {
                    String token = tokenInput.getText().toString().trim();
                    String gistId = gistInput.getText().toString().trim();
                    if (token.isEmpty()) {
                        Toast.makeText(this, "请输入 Token", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    com.limelight.pro.CloudSyncManager.setToken(this, token);
                    com.limelight.pro.CloudSyncManager.setGistId(this, gistId);
                    com.limelight.pro.CloudSyncManager.upload(this,
                            new com.limelight.pro.CloudSyncManager.SyncCallback() {
                                @Override
                                public void onSuccess(String msg) {
                                    runOnUiThread(() -> Toast.makeText(GyroSettingsActivity.this, msg, Toast.LENGTH_LONG).show());
                                }
                                @Override
                                public void onError(String err) {
                                    runOnUiThread(() -> Toast.makeText(GyroSettingsActivity.this, "备份失败: " + err, Toast.LENGTH_LONG).show());
                                }
                            });
                })
                .setNegativeButton("从云端恢复", (d, w) -> {
                    String token = tokenInput.getText().toString().trim();
                    String gistId = gistInput.getText().toString().trim();
                    if (gistId.isEmpty() && com.limelight.pro.CloudSyncManager.getGistId(this).isEmpty()) {
                        Toast.makeText(this, "请输入 Gist ID", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    com.limelight.pro.CloudSyncManager.setToken(this, token);
                    if (!gistId.isEmpty()) com.limelight.pro.CloudSyncManager.setGistId(this, gistId);
                    // 恢复前先备份当前配置
                    com.limelight.pro.ConfigBackupHelper.backupBeforeApply(this);
                    com.limelight.pro.CloudSyncManager.download(this, gistId,
                            new com.limelight.pro.CloudSyncManager.SyncCallback() {
                                @Override
                                public void onSuccess(String msg) {
                                    runOnUiThread(() -> Toast.makeText(GyroSettingsActivity.this, msg, Toast.LENGTH_LONG).show());
                                }
                                @Override
                                public void onError(String err) {
                                    runOnUiThread(() -> Toast.makeText(GyroSettingsActivity.this, "恢复失败: " + err, Toast.LENGTH_LONG).show());
                                }
                            });
                })
                .setNeutralButton("关闭", null)
                .show();
    }

    // ===== UI 辅助方法 =====

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView titleView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private TextView subtitleView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.LTGRAY);
        tv.setPadding(0, 0, 0, dp(16));
        return tv;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Color.parseColor("#4FC3F7"));
        tv.setPadding(0, dp(16), 0, dp(8));
        return tv;
    }

    private TextView hintView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.GRAY);
        tv.setPadding(dp(16), dp(4), 0, dp(4));
        return tv;
    }

    private LinearLayout.LayoutParams paddingParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(8), 0, dp(8));
        return p;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        p.setMargins(dp(8), 0, dp(8), 0);
        return p;
    }

    /**
     * 创建一个滑块行：标签 + 数值 + SeekBar
     * @param initial  初始值
     * @param min      最小值
     * @param max      最大值
     * @param step     步长
     * @param onChange 值变更回调
     * @param defStr   默认值显示（提示）
     */
    private View sliderRow(String label, float initial, float min, float max, float step,
                           final OnFloatChanged onChange, String defStr) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        // 标签行
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView nameTv = new TextView(this);
        nameTv.setText(label + (defStr != null ? " (默认 " + defStr + ")" : ""));
        nameTv.setTextColor(Color.WHITE);
        nameTv.setTextSize(14);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final TextView valueTv = new TextView(this);
        valueTv.setTextColor(Color.YELLOW);
        valueTv.setTextSize(14);
        valueTv.setGravity(Gravity.RIGHT);
        valueTv.setWidth(dp(80));

        labelRow.addView(nameTv);
        labelRow.addView(valueTv);
        row.addView(labelRow);

        // SeekBar
        final int maxProgress = (int) ((max - min) / step);
        final float[] current = {initial};
        SeekBar seek = new SeekBar(this);
        seek.setMax(maxProgress);
        seek.setProgress((int) ((initial - min) / step));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                current[0] = min + progress * step;
                valueTv.setText(formatValue(current[0], step));
                onChange.onChanged(current[0]);
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        valueTv.setText(formatValue(initial, step));
        row.addView(seek);

        return row;
    }

    private String formatValue(float v, float step) {
        if (step >= 1) return String.valueOf((int) v);
        if (step >= 0.1f) return String.format("%.1f", v);
        if (step >= 0.01f) return String.format("%.2f", v);
        return String.format("%.3f", v);
    }

    private interface OnFloatChanged {
        void onChanged(float value);
    }
}
