package com.limelight.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 云配置同步：通过 GitHub Gist 备份/恢复配置。
 * 
 * 用户需提供 GitHub Personal Access Token（仅 gist 权限）。
 * Token 存本地 SharedPreferences，不上传任何第三方服务器。
 */
public class CloudSyncManager {
    private static final String PREFS_NAME = "cloud_sync";
    private static final String KEY_TOKEN = "github_token";
    private static final String KEY_GIST_ID = "gist_id";
    private static final String GIST_FILENAME = "deltastream-config.json";
    private static final String API_BASE = "https://api.github.com/gists";

    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    // ===== Token / Gist ID 存储 =====

    public static String getToken(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "");
    }

    public static void setToken(Context ctx, String token) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getGistId(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_GIST_ID, "");
    }

    public static void setGistId(Context ctx, String gistId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_GIST_ID, gistId).apply();
    }

    // ===== 上传（备份）=====

    /**
     * 把当前配置上传到 Gist。
     * 如果已有 Gist ID 则更新，否则创建新 Gist 并保存 ID。
     * 必须在后台线程调用。
     */
    public static void upload(final Context ctx, final SyncCallback callback) {
        new Thread(() -> {
            try {
                String token = getToken(ctx);
                if (token == null || token.isEmpty()) {
                    callback.onError("请先设置 GitHub Token");
                    return;
                }
                String json = ConfigBundle.toJson(ctx);
                String gistId = getGistId(ctx);

                JSONObject body = buildGistBody(json);
                HttpURLConnection conn;
                URL url;
                if (gistId != null && !gistId.isEmpty()) {
                    // 更新已有 Gist
                    url = new URL(API_BASE + "/" + gistId);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PATCH");
                } else {
                    // 创建新 Gist
                    url = new URL(API_BASE);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                }
                conn.setRequestProperty("Authorization", "token " + token);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                String response = readResponse(conn);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    JSONObject resp = new JSONObject(response);
                    String newGistId = resp.optString("id", "");
                    if (!newGistId.isEmpty()) {
                        setGistId(ctx, newGistId);
                    }
                    callback.onSuccess("已备份到云端，Gist ID: " + getGistId(ctx));
                } else {
                    callback.onError("上传失败 (" + code + "): " + response);
                }
            } catch (Exception e) {
                callback.onError("上传异常: " + e.getMessage());
            }
        }).start();
    }

    // ===== 下载（恢复）=====

    /**
     * 从 Gist 下载配置并应用。
     * 如果 gistId 参数为空，使用已保存的 Gist ID。
     * 必须在后台线程调用。
     * 注意：调用方负责在调用前做 ConfigBackupHelper.backupBeforeApply()。
     */
    public static void download(final Context ctx, String gistIdOverride, final SyncCallback callback) {
        new Thread(() -> {
            try {
                String token = getToken(ctx);
                String gistId = gistIdOverride != null && !gistIdOverride.isEmpty()
                        ? gistIdOverride : getGistId(ctx);
                if (gistId == null || gistId.isEmpty()) {
                    callback.onError("没有 Gist ID，请先备份或输入 Gist ID");
                    return;
                }

                URL url = new URL(API_BASE + "/" + gistId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "token " + token);
                }

                int code = conn.getResponseCode();
                String response = readResponse(conn);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    JSONObject resp = new JSONObject(response);
                    JSONObject files = resp.optJSONObject("files");
                    if (files == null) {
                        callback.onError("Gist 中没有文件");
                        return;
                    }
                    JSONObject configFile = files.optJSONObject(GIST_FILENAME);
                    if (configFile == null) {
                        // 尝试取第一个文件
                        configFile = files.optJSONObject(files.keys().next());
                    }
                    if (configFile == null) {
                        callback.onError("Gist 中没有配置文件");
                        return;
                    }
                    String content = configFile.optString("content", "");
                    if (content.isEmpty()) {
                        callback.onError("配置文件内容为空");
                        return;
                    }
                    // 保存 Gist ID
                    setGistId(ctx, gistId);
                    // 应用配置
                    ConfigBundle.fromJson(ctx, content);
                    callback.onSuccess("已从云端恢复配置，请重启串流生效");
                } else {
                    callback.onError("下载失败 (" + code + "): " + response);
                }
            } catch (Exception e) {
                callback.onError("下载异常: " + e.getMessage());
            }
        }).start();
    }

    // ===== 辅助方法 =====

    private static JSONObject buildGistBody(String jsonContent) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("description", "DeltaStream 配置备份");
        root.put("public", false); // secret Gist
        JSONObject files = new JSONObject();
        JSONObject fileContent = new JSONObject();
        fileContent.put("content", jsonContent);
        files.put(GIST_FILENAME, fileContent);
        root.put("files", files);
        return root;
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        BufferedReader reader;
        if (conn.getResponseCode() >= 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
