package com.limelight.gyro;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 陀螺仪瞄准设置数据类 — 1:1 复刻三角洲行动的陀螺仪设置项。
 *
 * 用户移动端参数：
 *   全局灵敏度 110, 垂直灵敏度 100, 水平灵敏度 160,
 *   开火灵敏度 150, 开火垂直灵敏度 120, 开火水平灵敏度 90,
 *   MDV 1.6, 开火 MDV 1.6
 *
 * 触发方式：始终 / 开镜 / 开火
 *
 * 自定义扩展项（三角洲没有，但实用）：
 *   死区、平滑系数、全局缩放、总开关
 */
public class GyroAimSettings {

    public static final String PREFS_NAME = "gyro_aim_settings";

    // 触发方式常量
    public static final int TRIGGER_ALWAYS  = 0;
    public static final int TRIGGER_ADS     = 1;  // 开镜（右键按下时）
    public static final int TRIGGER_FIRING  = 2;  // 开火（左键按下时）

    // ===== 三角洲官方设置项 =====
    public int sensGlobal;            // 全局灵敏度（参考值，目前未直接用于公式）
    public int sensVertical;          // 垂直灵敏度
    public int sensHorizontal;        // 水平灵敏度
    public int sensFiring;            // 开火全局灵敏度
    public int sensFiringVertical;    // 开火垂直灵敏度
    public int sensFiringHorizontal;  // 开火水平灵敏度
    public float mdv;                 // MDV 灵敏度倍率
    public float mdvFiring;           // 开火 MDV
    public int triggerMode;           // 触发方式

    // 自定义扩展项
    public boolean enabled;           // 总开关
    public float deadzone;            // 死区（rad/s，兼容旧配置，新逻辑用 cutoffSpeed/cutoffRecovery）
    public float smoothing;           // 平滑系数（兼容旧配置，新逻辑用 softSmoothThreshold）
    public float cutoffSpeed;         // 收紧型阈值速度（rad/s）：低于此速度平滑衰减，不丢真实输入
    public float cutoffRecovery;      // 收紧型恢复宽度（rad/s）：从衰减到完全通过的过渡区间
    public float softSmoothThreshold; // 软分层平滑阈值（rad/s）：小输入平滑，大输入直通（参考 GyroWiki）
    public float scale;               // 全局缩放（决定整体移动速度）
    public int transitionTimeMs;      // ADS/开火灵敏度切换过渡时间（ms）
    public float recoilCompensation;  // 开火垂直后坐力补偿力度（0=关闭）
    public float aimVerticalScale;    // 开镜时垂直灵敏度额外倍率（默认1.0）

    // ===== 开镜(ADS)自动降灵敏度组 =====
    // 开镜时自动切换到更低灵敏度，远距离瞄准更稳；可独立开关
    public boolean adsSensEnabled;    // 开镜自动降灵敏度开关
    public int sensAdsHorizontal;     // 开镜水平灵敏度
    public int sensAdsVertical;       // 开镜垂直灵敏度
    public float mdvAds;              // 开镜 MDV 倍率
    // ===== 一键压枪（后坐力补偿）=====
    public boolean recoilEnabled;     // 压枪开关（独立于 recoilCompensation 强度值）

    // 轴映射修正（部分平板自然方向为 landscape，需交换轴）
    public boolean swapAxes;          // 交换陀螺仪 X/Y 轴（上下↔左右）
    public boolean invertX;           // 反转水平方向
    public boolean invertY;           // 反转垂直方向

    // 自动校准偏移量
    public float calibOffsetX;        // 陀螺仪 X 轴零漂偏移
    public float calibOffsetY;        // 陀螺仪 Y 轴零漂偏移
    public float calibOffsetZ;        // 陀螺仪 Z 轴零漂偏移
    public boolean calibrated;        // 是否已完成校准

    /** 构造并填充默认值 */
    public GyroAimSettings() {
        resetToDefaults();
    }

    /** 重置为默认值（用户移动端参数 + 自定义推荐值） */
    public void resetToDefaults() {
        sensGlobal = 110;
        sensVertical = 100;
        sensHorizontal = 160;
        sensFiring = 150;
        sensFiringVertical = 120;
        sensFiringHorizontal = 90;
        mdv = 1.6f;
        mdvFiring = 1.6f;
        triggerMode = TRIGGER_ALWAYS;
        // 自定义推荐值
        enabled = true;
        deadzone = 0.02f;
        smoothing = 0.2f;
        cutoffSpeed = 0.05f;         // 低于此角速度（rad/s）开始平滑衰减
        cutoffRecovery = 0.04f;      // 从衰减到完全通过的过渡区间
        softSmoothThreshold = 0.15f; // 软分层平滑阈值，小输入平滑、大输入直通
        scale = 300f;
        transitionTimeMs = 120;
        recoilCompensation = 0f;
        aimVerticalScale = 1.0f;
        // ADS 灵敏度默认值：比常规低约 40%，远距离更稳
        adsSensEnabled = true;
        sensAdsHorizontal = 100;  // 比常规 160 低
        sensAdsVertical = 60;     // 比常规 100 低
        mdvAds = 1.0f;            // 比常规 1.6 低
        // 压枪默认关闭，避免新用户突兀
        recoilEnabled = false;
        swapAxes = false;
        invertX = false;
        invertY = false;
        calibOffsetX = 0f;
        calibOffsetY = 0f;
        calibOffsetZ = 0f;
        calibrated = false;
    }

    /** 从 SharedPreferences 读取，若不存在则返回默认值 */
    public static GyroAimSettings load(Context ctx) {
        GyroAimSettings s = new GyroAimSettings();
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        s.enabled              = sp.getBoolean("enabled", s.enabled);
        s.sensGlobal           = sp.getInt("sensGlobal", s.sensGlobal);
        s.sensVertical         = sp.getInt("sensVertical", s.sensVertical);
        s.sensHorizontal       = sp.getInt("sensHorizontal", s.sensHorizontal);
        s.sensFiring           = sp.getInt("sensFiring", s.sensFiring);
        s.sensFiringVertical   = sp.getInt("sensFiringVertical", s.sensFiringVertical);
        s.sensFiringHorizontal = sp.getInt("sensFiringHorizontal", s.sensFiringHorizontal);
        s.mdv                  = sp.getFloat("mdv", s.mdv);
        s.mdvFiring            = sp.getFloat("mdvFiring", s.mdvFiring);
        s.triggerMode          = sp.getInt("triggerMode", s.triggerMode);
        s.deadzone             = sp.getFloat("deadzone", s.deadzone);
        s.smoothing            = sp.getFloat("smoothing", s.smoothing);
        s.cutoffSpeed          = sp.getFloat("cutoffSpeed", s.cutoffSpeed);
        s.cutoffRecovery       = sp.getFloat("cutoffRecovery", s.cutoffRecovery);
        s.softSmoothThreshold  = sp.getFloat("softSmoothThreshold", s.softSmoothThreshold);
        s.scale                = sp.getFloat("scale", s.scale);
        s.transitionTimeMs     = sp.getInt("transitionTimeMs", s.transitionTimeMs);
        s.recoilCompensation   = sp.getFloat("recoilCompensation", s.recoilCompensation);
        s.aimVerticalScale     = sp.getFloat("aimVerticalScale", s.aimVerticalScale);
        s.adsSensEnabled       = sp.getBoolean("adsSensEnabled", s.adsSensEnabled);
        s.sensAdsHorizontal    = sp.getInt("sensAdsHorizontal", s.sensAdsHorizontal);
        s.sensAdsVertical      = sp.getInt("sensAdsVertical", s.sensAdsVertical);
        s.mdvAds               = sp.getFloat("mdvAds", s.mdvAds);
        s.recoilEnabled        = sp.getBoolean("recoilEnabled", s.recoilEnabled);
        s.swapAxes             = sp.getBoolean("swapAxes", s.swapAxes);
        s.invertX              = sp.getBoolean("invertX", s.invertX);
        s.invertY              = sp.getBoolean("invertY", s.invertY);
        s.calibOffsetX         = sp.getFloat("calibOffsetX", s.calibOffsetX);
        s.calibOffsetY         = sp.getFloat("calibOffsetY", s.calibOffsetY);
        s.calibOffsetZ         = sp.getFloat("calibOffsetZ", s.calibOffsetZ);
        s.calibrated           = sp.getBoolean("calibrated", s.calibrated);
        return s;
    }

    /** 保存到 SharedPreferences */
    public void save(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("enabled", enabled);
        ed.putInt("sensGlobal", sensGlobal);
        ed.putInt("sensVertical", sensVertical);
        ed.putInt("sensHorizontal", sensHorizontal);
        ed.putInt("sensFiring", sensFiring);
        ed.putInt("sensFiringVertical", sensFiringVertical);
        ed.putInt("sensFiringHorizontal", sensFiringHorizontal);
        ed.putFloat("mdv", mdv);
        ed.putFloat("mdvFiring", mdvFiring);
        ed.putInt("triggerMode", triggerMode);
        ed.putFloat("deadzone", deadzone);
        ed.putFloat("smoothing", smoothing);
        ed.putFloat("cutoffSpeed", cutoffSpeed);
        ed.putFloat("cutoffRecovery", cutoffRecovery);
        ed.putFloat("softSmoothThreshold", softSmoothThreshold);
        ed.putFloat("scale", scale);
        ed.putInt("transitionTimeMs", transitionTimeMs);
        ed.putFloat("recoilCompensation", recoilCompensation);
        ed.putFloat("aimVerticalScale", aimVerticalScale);
        ed.putBoolean("adsSensEnabled", adsSensEnabled);
        ed.putInt("sensAdsHorizontal", sensAdsHorizontal);
        ed.putInt("sensAdsVertical", sensAdsVertical);
        ed.putFloat("mdvAds", mdvAds);
        ed.putBoolean("recoilEnabled", recoilEnabled);
        ed.putBoolean("swapAxes", swapAxes);
        ed.putBoolean("invertX", invertX);
        ed.putBoolean("invertY", invertY);
        ed.putFloat("calibOffsetX", calibOffsetX);
        ed.putFloat("calibOffsetY", calibOffsetY);
        ed.putFloat("calibOffsetZ", calibOffsetZ);
        ed.putBoolean("calibrated", calibrated);
        ed.apply();
    }
}