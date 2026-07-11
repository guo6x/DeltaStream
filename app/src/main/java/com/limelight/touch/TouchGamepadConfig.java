package com.limelight.touch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟触控手柄配置：摇杆、按钮、触摸瞄准区的布局与行为参数。
 * 所有参数持久化到 SharedPreferences，支持用户自定义布局。
 */
public class TouchGamepadConfig {

    public static final String PREFS_NAME = "touch_gamepad_config";

    // 默认 Windows 虚拟键码
    public static final int VK_W = 0x57;
    public static final int VK_A = 0x41;
    public static final int VK_S = 0x53;
    public static final int VK_D = 0x44;
    public static final int VK_SPACE = 0x20;
    public static final int VK_R = 0x52;
    public static final int VK_F = 0x46;
    public static final int VK_C = 0x43;
    public static final int VK_Z = 0x5A;
    public static final int VK_Q = 0x51;
    public static final int VK_E = 0x45;
    public static final int VK_B = 0x42;
    public static final int VK_N = 0x4E;
    public static final int VK_I = 0x49;
    public static final int VK_X = 0x58;
    public static final int VK_V = 0x56;
    public static final int VK_T = 0x54;
    public static final int VK_H = 0x48;
    public static final int VK_Y = 0x59;
    public static final int VK_U = 0x55;
    public static final int VK_1 = 0x31;
    public static final int VK_2 = 0x32;
    public static final int VK_3 = 0x33;
    public static final int VK_4 = 0x34;
    public static final int VK_5 = 0x35;
    public static final int VK_6 = 0x36;
    public static final int VK_TAB = 0x09;
    public static final int VK_M = 0x4D;
    public static final int VK_P = 0x50;    // 标记/轮盘标记
    public static final int VK_G = 0x47;
    public static final int VK_SHIFT = 0xA0; // 左 Shift
    public static final int VK_CTRL = 0xA2;  // 左 Ctrl
    public static final int VK_ALT = 0xA4;   // 左 Alt
    public static final int VK_CAPITAL = 0x14;
    public static final int VK_ESCAPE = 0x1B;
    public static final int VK_RETURN = 0x0D;
    public static final int VK_EQUALS = 0xBB; // = 自动奔跑
    public static final int VK_WHEEL_UP = 0x10001;   // 鼠标滚轮上
    public static final int VK_WHEEL_DOWN = 0x10002; // 鼠标滚轮下

    // 鼠标按钮常量（与 MouseButtonPacket 一致）
    public static final int MOUSE_LEFT = 0x01;
    public static final int MOUSE_RIGHT = 0x03;
    public static final int MOUSE_MIDDLE = 0x02;

    // 摇杆参数
    public float stickCenterX = 0.18f;      // 屏幕宽度比例
    public float stickCenterY = 0.72f;      // 屏幕高度比例
    public float stickRadiusDp = 72f;
    public boolean stickOutputKeyboard = true; // true=WASD, false=手柄左摇杆

    // 触摸瞄准区（右半屏）
    public boolean touchAimEnabled = true;
    public float touchAimSensitivity = 2.2f;
    public float touchAimVerticalScale = 0.85f;
    public float touchAimAreaLeftRatio = 0.35f; // 右侧区域起始比例

    // 全局
    public float buttonAlpha = 0.55f;
    public float globalScale = 1.0f;

    // 按钮列表
    public List<TouchButton> buttons = new ArrayList<>();

    public TouchGamepadConfig() {
        resetToDefaults();
    }

    /** 重置为三角洲手游风格默认布局 */
    public void resetToDefaults() {
        stickCenterX = 0.18f;
        stickCenterY = 0.72f;
        stickRadiusDp = 72f;
        stickOutputKeyboard = true;

        touchAimEnabled = true;
        touchAimSensitivity = 2.2f;
        touchAimVerticalScale = 0.85f;
        touchAimAreaLeftRatio = 0.35f;

        // 已改为每个按钮独立设置 alpha/size，保留字段仅兼容旧配置
        buttonAlpha = 0.55f;
        globalScale = 1.0f;

        buttons.clear();
        // 右侧主操作区
        addDefaultButton("开火", MOUSE_LEFT, 0.84f, 0.72f, 64);
        addDefaultButton("开镜", MOUSE_RIGHT, 0.72f, 0.58f, 52);
        addDefaultButton("跳", VK_SPACE, 0.76f, 0.84f, 44);
        addDefaultButton("蹲", VK_C, 0.65f, 0.84f, 42);
        addDefaultButton("趴", VK_Z, 0.55f, 0.84f, 42);
        addDefaultButton("换弹", VK_R, 0.88f, 0.48f, 42, true);
        addDefaultButton("刀", MOUSE_MIDDLE, 0.78f, 0.44f, 38);
        addDefaultButton("拾取", VK_F, 0.90f, 0.36f, 40);
        addDefaultButton("交互", VK_F, 0.90f, 0.26f, 40);
        // 左侧身法
        addDefaultButton("左倾", VK_Q, 0.14f, 0.52f, 38);
        addDefaultButton("右倾", VK_E, 0.26f, 0.52f, 38);
        // 奔跑/自动跑设为切换模式：点一下保持按住，再点一下释放（端游奔跑键持续按住）
        addDefaultToggleButton("奔跑", VK_SHIFT, 0.08f, 0.62f, 40);
        addDefaultButton("静步", VK_CAPITAL, 0.08f, 0.44f, 38);
        addDefaultToggleButton("自动跑", VK_EQUALS, 0.06f, 0.32f, 36);
        // 功能键
        addDefaultButton("地图", VK_M, 0.92f, 0.16f, 38);
        addDefaultButton("背包", VK_TAB, 0.82f, 0.16f, 38);
        addDefaultButton("检视", VK_I, 0.72f, 0.16f, 36);
        addDefaultButton("换枪", VK_B, 0.62f, 0.16f, 36);
        addDefaultButton("切瞄具", VK_N, 0.52f, 0.16f, 36);
        addDefaultButton("交互轮盘", VK_H, 0.42f, 0.16f, 36, true);
        addDefaultButton("语音", VK_T, 0.32f, 0.16f, 34);
        // 武器切换
        addDefaultButton("1", VK_1, 0.20f, 0.18f, 36);
        addDefaultButton("2", VK_2, 0.28f, 0.18f, 36);
        addDefaultButton("3", VK_3, 0.36f, 0.18f, 36);
        addDefaultButton("4", VK_4, 0.44f, 0.18f, 36);
        // 战术道具/治疗
        addDefaultButton("道具1", VK_V, 0.36f, 0.42f, 38);
        addDefaultButton("道具2", VK_G, 0.46f, 0.42f, 38);
        addDefaultButton("特殊", VK_X, 0.56f, 0.42f, 38);
        addDefaultButton("治疗轮盘", VK_5, 0.66f, 0.42f, 38, true);
        addDefaultButton("治疗2", VK_6, 0.76f, 0.42f, 36);
        addDefaultButton("标记", VK_P, 0.30f, 0.30f, 34);
        // 系统键
        addDefaultButton("Esc", VK_ESCAPE, 0.08f, 0.16f, 34);
        addDefaultButton("菜单", VK_RETURN, 0.18f, 0.16f, 34);
    }

    private void addDefaultButton(String label, int keyOrMouse, float xRatio, float yRatio, float sizeDp) {
        addDefaultButton(label, keyOrMouse, xRatio, yRatio, sizeDp, false);
    }

    private void addDefaultButton(String label, int keyOrMouse, float xRatio, float yRatio, float sizeDp, boolean wheel) {
        addDefaultButton(label, keyOrMouse, xRatio, yRatio, sizeDp, wheel, 0.55f);
    }

    private void addDefaultButton(String label, int keyOrMouse, float xRatio, float yRatio, float sizeDp, boolean wheel, float alpha) {
        TouchButton btn = new TouchButton();
        btn.id = "btn_" + label;
        btn.label = label;
        btn.keyCode = keyOrMouse;
        btn.isMouse = (keyOrMouse == MOUSE_LEFT || keyOrMouse == MOUSE_RIGHT || keyOrMouse == MOUSE_MIDDLE);
        btn.xRatio = xRatio;
        btn.yRatio = yRatio;
        btn.sizeDp = sizeDp;
        btn.alpha = alpha;
        btn.supportsWheel = wheel;
        buttons.add(btn);
    }

    /** 添加默认切换型按钮（点一下保持按下，再点一下释放） */
    private void addDefaultToggleButton(String label, int keyOrMouse, float xRatio, float yRatio, float sizeDp) {
        TouchButton btn = new TouchButton();
        btn.id = "btn_" + label;
        btn.label = label;
        btn.keyCode = keyOrMouse;
        btn.isMouse = (keyOrMouse == MOUSE_LEFT || keyOrMouse == MOUSE_RIGHT || keyOrMouse == MOUSE_MIDDLE);
        btn.xRatio = xRatio;
        btn.yRatio = yRatio;
        btn.sizeDp = sizeDp;
        btn.alpha = 0.55f;
        btn.supportsWheel = false;
        btn.isToggle = true;
        buttons.add(btn);
    }

    public static TouchGamepadConfig load(Context ctx) {
        TouchGamepadConfig cfg = new TouchGamepadConfig();
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            String json = sp.getString("config_json", null);
            if (json != null) {
                cfg.fromJson(new JSONObject(json));
                return cfg;
            }
        } catch (JSONException ignored) {
        }
        return cfg;
    }

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString("config_json", toJson().toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("stickCenterX", stickCenterX);
            root.put("stickCenterY", stickCenterY);
            root.put("stickRadiusDp", stickRadiusDp);
            root.put("stickOutputKeyboard", stickOutputKeyboard);
            root.put("touchAimEnabled", touchAimEnabled);
            root.put("touchAimSensitivity", touchAimSensitivity);
            root.put("touchAimVerticalScale", touchAimVerticalScale);
            root.put("touchAimAreaLeftRatio", touchAimAreaLeftRatio);
            root.put("buttonAlpha", buttonAlpha);
            root.put("globalScale", globalScale);
            JSONArray arr = new JSONArray();
            for (TouchButton b : buttons) {
                arr.put(b.toJson());
            }
            root.put("buttons", arr);
        } catch (JSONException ignored) {
        }
        return root;
    }

    public void fromJson(JSONObject root) throws JSONException {
        stickCenterX = (float) root.optDouble("stickCenterX", stickCenterX);
        stickCenterY = (float) root.optDouble("stickCenterY", stickCenterY);
        stickRadiusDp = (float) root.optDouble("stickRadiusDp", stickRadiusDp);
        stickOutputKeyboard = root.optBoolean("stickOutputKeyboard", stickOutputKeyboard);
        touchAimEnabled = root.optBoolean("touchAimEnabled", touchAimEnabled);
        touchAimSensitivity = (float) root.optDouble("touchAimSensitivity", touchAimSensitivity);
        touchAimVerticalScale = (float) root.optDouble("touchAimVerticalScale", touchAimVerticalScale);
        touchAimAreaLeftRatio = (float) root.optDouble("touchAimAreaLeftRatio", touchAimAreaLeftRatio);
        buttonAlpha = (float) root.optDouble("buttonAlpha", buttonAlpha);
        globalScale = (float) root.optDouble("globalScale", globalScale);
        JSONArray arr = root.optJSONArray("buttons");
        if (arr != null) {
            buttons.clear();
            for (int i = 0; i < arr.length(); i++) {
                TouchButton b = new TouchButton();
                b.fromJson(arr.getJSONObject(i));
                buttons.add(b);
            }
        }
    }
}
