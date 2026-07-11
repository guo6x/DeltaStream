package com.limelight.pro;

import android.content.Context;
import com.limelight.touch.TouchGamepadConfig;
import com.limelight.gyro.GyroAimSettings;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * 配置打包：把 TouchGamepadConfig + GyroAimSettings 序列化为标准 JSON。
 * 云同步、模板库、分享码三个功能共用此格式。
 *
 * toJson 只读不写，安全。
 * fromJson 会写回配置，调用前必须先走 ConfigBackupHelper.backupBeforeApply()。
 */
public class ConfigBundle {
    private static final int VERSION = 1;

    public static String toJson(Context ctx) {
        TouchGamepadConfig touchCfg = TouchGamepadConfig.load(ctx);
        GyroAimSettings gyroCfg = GyroAimSettings.load(ctx);

        JSONObject root = new JSONObject();
        try {
            root.put("version", VERSION);
            root.put("exportedFrom", "DeltaStream");
            root.put("exportedAt", System.currentTimeMillis());
            root.put("touchGamepad", touchCfg.toJson());
            root.put("gyroAim", gyroToJson(gyroCfg));
        } catch (JSONException ignored) {
        }
        return root.toString();
    }

    public static void fromJson(Context ctx, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        // 版本检查（未来版本升级时在此处理兼容）
        int version = root.optInt("version", 1);

        // 恢复触控配置
        JSONObject touchJson = root.optJSONObject("touchGamepad");
        if (touchJson != null) {
            TouchGamepadConfig touchCfg = TouchGamepadConfig.load(ctx);
            touchCfg.fromJson(touchJson);
            touchCfg.save(ctx);
        }

        // 恢复陀螺仪配置
        JSONObject gyroJson = root.optJSONObject("gyroAim");
        if (gyroJson != null) {
            GyroAimSettings gyroCfg = gyroFromJson(gyroJson);
            gyroCfg.save(ctx);
        }
    }

    /** GyroAimSettings 没有 toJson，这里手动序列化所有字段 */
    private static JSONObject gyroToJson(GyroAimSettings s) {
        JSONObject o = new JSONObject();
        try {
            o.put("enabled", s.enabled);
            o.put("sensGlobal", s.sensGlobal);
            o.put("sensVertical", s.sensVertical);
            o.put("sensHorizontal", s.sensHorizontal);
            o.put("sensFiring", s.sensFiring);
            o.put("sensFiringVertical", s.sensFiringVertical);
            o.put("sensFiringHorizontal", s.sensFiringHorizontal);
            o.put("mdv", s.mdv);
            o.put("mdvFiring", s.mdvFiring);
            o.put("triggerMode", s.triggerMode);
            o.put("deadzone", s.deadzone);
            o.put("smoothing", s.smoothing);
            o.put("cutoffSpeed", s.cutoffSpeed);
            o.put("cutoffRecovery", s.cutoffRecovery);
            o.put("softSmoothThreshold", s.softSmoothThreshold);
            o.put("scale", s.scale);
            o.put("transitionTimeMs", s.transitionTimeMs);
            o.put("recoilCompensation", s.recoilCompensation);
            o.put("aimVerticalScale", s.aimVerticalScale);
            o.put("adsSensEnabled", s.adsSensEnabled);
            o.put("sensAdsHorizontal", s.sensAdsHorizontal);
            o.put("sensAdsVertical", s.sensAdsVertical);
            o.put("mdvAds", s.mdvAds);
            o.put("recoilEnabled", s.recoilEnabled);
            o.put("swapAxes", s.swapAxes);
            o.put("invertX", s.invertX);
            o.put("invertY", s.invertY);
            o.put("calibOffsetX", s.calibOffsetX);
            o.put("calibOffsetY", s.calibOffsetY);
            o.put("calibOffsetZ", s.calibOffsetZ);
            o.put("calibrated", s.calibrated);
        } catch (JSONException ignored) {
        }
        return o;
    }

    /** GyroAimSettings 没有 fromJson，这里手动反序列化所有字段 */
    private static GyroAimSettings gyroFromJson(JSONObject o) {
        GyroAimSettings s = new GyroAimSettings();
        s.enabled = o.optBoolean("enabled", s.enabled);
        s.sensGlobal = o.optInt("sensGlobal", s.sensGlobal);
        s.sensVertical = o.optInt("sensVertical", s.sensVertical);
        s.sensHorizontal = o.optInt("sensHorizontal", s.sensHorizontal);
        s.sensFiring = o.optInt("sensFiring", s.sensFiring);
        s.sensFiringVertical = o.optInt("sensFiringVertical", s.sensFiringVertical);
        s.sensFiringHorizontal = o.optInt("sensFiringHorizontal", s.sensFiringHorizontal);
        s.mdv = (float) o.optDouble("mdv", s.mdv);
        s.mdvFiring = (float) o.optDouble("mdvFiring", s.mdvFiring);
        s.triggerMode = o.optInt("triggerMode", s.triggerMode);
        s.deadzone = (float) o.optDouble("deadzone", s.deadzone);
        s.smoothing = (float) o.optDouble("smoothing", s.smoothing);
        s.cutoffSpeed = (float) o.optDouble("cutoffSpeed", s.cutoffSpeed);
        s.cutoffRecovery = (float) o.optDouble("cutoffRecovery", s.cutoffRecovery);
        s.softSmoothThreshold = (float) o.optDouble("softSmoothThreshold", s.softSmoothThreshold);
        s.scale = (float) o.optDouble("scale", s.scale);
        s.transitionTimeMs = o.optInt("transitionTimeMs", s.transitionTimeMs);
        s.recoilCompensation = (float) o.optDouble("recoilCompensation", s.recoilCompensation);
        s.aimVerticalScale = (float) o.optDouble("aimVerticalScale", s.aimVerticalScale);
        s.adsSensEnabled = o.optBoolean("adsSensEnabled", s.adsSensEnabled);
        s.sensAdsHorizontal = o.optInt("sensAdsHorizontal", s.sensAdsHorizontal);
        s.sensAdsVertical = o.optInt("sensAdsVertical", s.sensAdsVertical);
        s.mdvAds = (float) o.optDouble("mdvAds", s.mdvAds);
        s.recoilEnabled = o.optBoolean("recoilEnabled", s.recoilEnabled);
        s.swapAxes = o.optBoolean("swapAxes", s.swapAxes);
        s.invertX = o.optBoolean("invertX", s.invertX);
        s.invertY = o.optBoolean("invertY", s.invertY);
        s.calibOffsetX = (float) o.optDouble("calibOffsetX", s.calibOffsetX);
        s.calibOffsetY = (float) o.optDouble("calibOffsetY", s.calibOffsetY);
        s.calibOffsetZ = (float) o.optDouble("calibOffsetZ", s.calibOffsetZ);
        s.calibrated = o.optBoolean("calibrated", s.calibrated);
        return s;
    }
}
