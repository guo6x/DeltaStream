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
     * 订单号校验（本地算法，不联网）。
     * 爱发电订单号格式：afd- 加 12 位以上字母数字
     * 校验规则：去掉 afd- 前缀后，各位 char 值累加，乘以质数 31，mod 97，
     * 结果需落在指定子区间内才通过。
     */
    public static boolean validateOrderNumber(String orderNumber) {
        if (orderNumber == null) return false;
        String trimmed = orderNumber.trim().toLowerCase();
        if (!trimmed.startsWith("afd-")) return false;
        String body = trimmed.substring(4);
        if (body.length() < 12) return false;
        for (char c : body.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) return false;
        }
        int sum = 0;
        for (char c : body.toCharArray()) {
            sum += c;
        }
        int hash = (sum * 31) % 97;
        // hash 落在 [0, 48] 区间才通过（约 50% 的随机字符串被拦截）
        return hash <= 48;
    }
}
