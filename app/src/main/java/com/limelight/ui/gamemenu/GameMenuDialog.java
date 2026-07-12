package com.limelight.ui.gamemenu;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.touch.TouchGamepadEditorActivity;
import com.limelight.ui.floating.FloatingMenuButton;

/**
 * 游戏内统一设置菜单。
 * 整合了按键编辑器、陀螺仪设置、虚拟手柄、显示设置、快捷键等功能。
 * 作为串流中唯一的设置入口，替代之前的四指点击和长按设置按钮。
 */
public class GameMenuDialog extends DialogFragment {

    private Game game;
    private NvConnection conn;
    private PreferenceConfiguration prefConfig;
    private FloatingMenuButton floatingButton;

    public void setGame(Game game) { this.game = game; }
    public void setConn(NvConnection conn) { this.conn = conn; }
    public void setPrefConfig(PreferenceConfiguration config) { this.prefConfig = config; }
    public void setFloatingButton(FloatingMenuButton btn) { this.floatingButton = btn; }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), R.style.GameMenuDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_game_menu);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            window.setDimAmount(0.3f);
        }

        bindViews(dialog);
        return dialog;
    }

    private void bindViews(Dialog dialog) {
        Button btnKeyEditor = dialog.findViewById(R.id.btn_key_editor);
        Button btnGyroSettings = dialog.findViewById(R.id.btn_gyro_settings);
        Button btnGamePad = dialog.findViewById(R.id.btn_game_pad);
        Button btnSoftKeyboard = dialog.findViewById(R.id.btn_soft_keyboard);
        Button btnPerformance = dialog.findViewById(R.id.btn_performance);
        Button btnMouseCursor = dialog.findViewById(R.id.btn_mouse_cursor);
        Button btnQuickKeys = dialog.findViewById(R.id.btn_quick_keys);
        Button btnUnlink = dialog.findViewById(R.id.btn_unlink);
        Button btnExit = dialog.findViewById(R.id.btn_exit);

        updateToggleButtons(btnPerformance, btnGamePad, btnMouseCursor);

        // 按键编辑器
        btnKeyEditor.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                Intent intent = new Intent(game, TouchGamepadEditorActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                game.startActivity(intent);
            }
        });

        // 陀螺仪 & 手搓设置
        btnGyroSettings.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.startActivity(new Intent(game, com.limelight.gyro.GyroSettingsActivity.class));
            }
        });

        // 虚拟手柄切换
        btnGamePad.setOnClickListener(v -> {
            if (game != null) {
                game.toggleOnscreenController();
                updateToggleButtons(btnPerformance, btnGamePad, btnMouseCursor);
            }
        });

        // 软键盘
        btnSoftKeyboard.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.toggleKeyboard();
            }
        });

        // 性能信息切换
        btnPerformance.setOnClickListener(v -> {
            if (game != null) {
                game.togglePerformanceOverlay();
                updateToggleButtons(btnPerformance, btnGamePad, btnMouseCursor);
            }
        });

        // 鼠标光标切换
        btnMouseCursor.setOnClickListener(v -> {
            if (game != null) {
                game.toggleMouseCursor();
                updateToggleButtons(btnPerformance, btnGamePad, btnMouseCursor);
            }
        });

        // 快捷键
        btnQuickKeys.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.showQuickKeysDialog();
            }
        });

        // 断开连接
        btnUnlink.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.disconnect();
            }
        });

        // 退出串流
        btnExit.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.quitStreaming();
            }
        });
    }

    private void updateToggleButtons(Button btnPerf, Button btnPad, Button btnMouse) {
        if (prefConfig == null) return;

        boolean perfOn = prefConfig.enablePerfOverlay;
        btnPerf.setText("性能信息: " + (perfOn ? "开" : "关"));
        btnPerf.setBackgroundResource(perfOn ?
                R.drawable.game_menu_btn_green_selector : R.drawable.game_menu_btn_selector);

        boolean padOn = prefConfig.onscreenController;
        btnPad.setText("虚拟手柄: " + (padOn ? "开" : "关"));
        btnPad.setBackgroundResource(padOn ?
                R.drawable.game_menu_btn_green_selector : R.drawable.game_menu_btn_selector);

        boolean mouseVisible = (game != null && game.isCursorVisible());
        btnMouse.setText("鼠标光标: " + (mouseVisible ? "显示" : "隐藏"));
        btnMouse.setBackgroundResource(mouseVisible ?
                R.drawable.game_menu_btn_green_selector : R.drawable.game_menu_btn_selector);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (floatingButton != null) {
            floatingButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 发送键盘组合键到 PC
     */
    public static void sendKeys(NvConnection conn, short[] keys) {
        if (conn == null) return;

        final byte[] modifier = {0};
        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
            modifier[0] |= getModifier(key);
        }

        new android.os.Handler().postDelayed(() -> {
            for (int i = keys.length - 1; i >= 0; i--) {
                short key = keys[i];
                modifier[0] &= ~getModifier(keys[i]);
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }, 25);
    }

    private static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT: return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL: return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN: return KeyboardPacket.MODIFIER_META;
            case KeyboardTranslator.VK_LMENU: return KeyboardPacket.MODIFIER_ALT;
            default: return 0;
        }
    }
}
