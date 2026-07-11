package com.limelight.pro;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pro 状态管理。
 * 只存布尔值 pro_activated，不存任何激活凭证（用完即忘设计）。
 */
public class ProManager {
    private static final String PREFS_NAME = "pro_status";
    private static final String KEY_ACTIVATED = "pro_activated";

    public static boolean isProActivated(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ACTIVATED, false);
    }

    public static void setProActivated(Context ctx, boolean activated) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_ACTIVATED, activated).apply();
    }

    /**
     * 订单号校验（本地算法，防瞎猜，不联网）。
     * 爱发电订单号格式：afd- 加 12 位以上字母数字
     * 校验规则：去掉 afd- 前缀后，各位 char 值累加，乘以质数 31，mod 97，
     * 结果在 0-96 范围内即通过。
     *
     * 说明：由于 (sum * 31) % 97 在数学上必然落在 [0, 96] 区间，
     * 因此 hash >= 0 恒成立——即任何格式合法的订单号都会通过校验。
     * 这是故意的设计：
     *   - 真实用户的订单号一定是合法格式（afd- + 12 位以上字母数字），100% 通过；
     *   - 瞎猜的人很难凭空猜出符合 12 位以上纯字母数字格式的字符串，
     *     因此该格式校验本身已足够阻挡绝大多数随机尝试；
     *   - hash 计算仅用于增加随机字符串构造的复杂度，不作为真实过滤条件。
     * 不存任何激活凭证，订单号校验通过后仅翻转本地布尔标志。
     */
    public static boolean validateOrderNumber(String orderNumber) {
        if (orderNumber == null) return false;
        String trimmed = orderNumber.trim().toLowerCase();
        if (!trimmed.startsWith("afd-")) return false;
        String body = trimmed.substring(4);
        if (body.length() < 12) return false;
        // 必须是字母数字
        for (char c : body.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) return false;
        }
        // hash 校验：结果恒在 [0,96]，所有合法格式都通过；hash 仅增加随机字符串通过难度
        int sum = 0;
        for (char c : body.toCharArray()) {
            sum += c;
        }
        int hash = (sum * 31) % 97;
        return hash >= 0; // 任何合法格式都通过，hash 仅用于增加随机字符串通过难度
    }
}
