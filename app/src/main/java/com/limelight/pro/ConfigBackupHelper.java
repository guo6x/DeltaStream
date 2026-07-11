package com.limelight.pro;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 配置安全：任何覆盖操作前自动备份，防丢配置。
 *
 * 三道防线：
 * 1. backupBeforeApply() 存当前配置到 config-backup-{timestamp}.json
 * 2. 调用方弹确认框
 * 3. listBackups() / restoreBackup() 可随时还原
 *
 * 备份目录：app 私有目录 files/config-backups/
 */
public class ConfigBackupHelper {

    private static File getBackupDir(Context ctx) {
        File dir = new File(ctx.getApplicationContext().getFilesDir(), "config-backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * 应用配置覆盖前调用：把当前配置存一份到本地文件。
     * @return 备份文件
     */
    public static File backupBeforeApply(Context ctx) {
        String json = ConfigBundle.toJson(ctx);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
                .format(new Date());
        File backupFile = new File(getBackupDir(ctx), "config-backup-" + timestamp + ".json");
        try {
            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException("备份失败", e);
        }
        return backupFile;
    }

    /** 列出所有备份文件，按时间倒序（最新在前） */
    public static List<File> listBackups(Context ctx) {
        File dir = getBackupDir(ctx);
        File[] files = dir.listFiles((d, name) -> name.startsWith("config-backup-") && name.endsWith(".json"));
        List<File> result = new ArrayList<>();
        if (files != null) {
            Collections.addAll(result, files);
            Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return result;
    }

    /**
     * 从备份文件恢复配置。
     * 注意：恢复本身也是覆盖操作，调用方应先做一次 backupBeforeApply()。
     */
    public static void restoreBackup(Context ctx, File backup) throws Exception {
        FileInputStream fis = new FileInputStream(backup);
        byte[] data = new byte[(int) backup.length()];
        fis.read(data);
        fis.close();
        String json = new String(data, "UTF-8");
        ConfigBundle.fromJson(ctx, json);
    }

    /** 删除超过 10 个的旧备份（保留最新 10 个） */
    public static void cleanupOldBackups(Context ctx) {
        List<File> backups = listBackups(ctx);
        if (backups.size() > 10) {
            for (int i = 10; i < backups.size(); i++) {
                backups.get(i).delete();
            }
        }
    }
}
