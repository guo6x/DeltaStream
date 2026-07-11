package com.limelight.touch;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 虚拟触控按钮定义。
 */
public class TouchButton {
    public String id;        // 唯一标识
    public String label;     // 显示文字
    public int keyCode;      // Windows 虚拟键码或鼠标按钮
    public boolean isMouse;  // true=鼠标按钮, false=键盘按键
    public float xRatio;     // 屏幕宽度比例 [0,1]
    public float yRatio;     // 屏幕高度比例 [0,1]
    public float sizeDp = 48f;     // 按钮直径 dp
    public float alpha = 1.0f;     // 按钮独立透明度 [0,1]
    public int color;              // 按钮颜色 ARGB，默认 0=自动
    public boolean supportsWheel;  // 长按是否进入轮盘模式（如换弹选弹种）
    public boolean isToggle;       // 切换模式：点一下保持按下，再点一下释放（用于奔跑键持续按住）

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("label", label);
            o.put("keyCode", keyCode);
            o.put("isMouse", isMouse);
            o.put("xRatio", xRatio);
            o.put("yRatio", yRatio);
            o.put("sizeDp", sizeDp);
            o.put("alpha", alpha);
            o.put("color", color);
            o.put("supportsWheel", supportsWheel);
            o.put("isToggle", isToggle);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public void fromJson(JSONObject o) throws JSONException {
        id = o.optString("id", id);
        label = o.optString("label", label);
        keyCode = o.optInt("keyCode", keyCode);
        isMouse = o.optBoolean("isMouse", isMouse);
        xRatio = (float) o.optDouble("xRatio", xRatio);
        yRatio = (float) o.optDouble("yRatio", yRatio);
        sizeDp = (float) o.optDouble("sizeDp", sizeDp);
        alpha = (float) o.optDouble("alpha", alpha);
        color = o.optInt("color", color);
        supportsWheel = o.optBoolean("supportsWheel", supportsWheel);
        isToggle = o.optBoolean("isToggle", isToggle);
    }
}
