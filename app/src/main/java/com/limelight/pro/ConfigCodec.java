package com.limelight.pro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import android.util.Base64;

/**
 * JSON ↔ 分享码互转编解码。
 * 分享码格式：DS-CONFIG-v1: + GZIP(JSON) 的 Base64 编码
 * 云同步、模板库、分享码三个功能共用。
 */
public class ConfigCodec {
    private static final String PREFIX = "DS-CONFIG-v1:";

    /** JSON 字符串 → 分享码 */
    public static String encode(String json) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(json.getBytes("UTF-8"));
            gzip.close();
            String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return PREFIX + base64;
        } catch (IOException e) {
            throw new RuntimeException("编码失败", e);
        }
    }

    /** 分享码 → JSON 字符串（前缀校验，失败抛 IllegalArgumentException） */
    public static String decode(String code) {
        if (code == null) throw new IllegalArgumentException("分享码为空");
        String trimmed = code.trim();
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("分享码格式错误，应以 " + PREFIX + " 开头");
        }
        String base64 = trimmed.substring(PREFIX.length());
        try {
            byte[] compressed = Base64.decode(base64, Base64.DEFAULT);
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            GZIPInputStream gzip = new GZIPInputStream(bis);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = gzip.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            gzip.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException("分享码解码失败，可能已损坏", e);
        }
    }
}
