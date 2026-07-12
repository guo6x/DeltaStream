package com.limelight.ui.gamemenu;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
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
import com.limelight.ui.floating.FloatingMenuButton;

/**
 * 游戏内悬浮菜单 DialogFragment。
 * 借鉴阿西西 GameMenuFragment 设计，精简为 DeltaStream 所需核心功能。
 * 使用原生 DialogFragment（非 androidx），与项目现有 API 一致。
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
        Button btnPerformance = dialog.findViewById(R.id.btn_performance);
        Button btnGamePad = dialog.findViewById(R.id.btn_game_pad);
        Button btnTouchSens = dialog.findViewById(R.id.btn_touch_sensitivity);
        Button btnSoftKeyboard = dialog.findViewById(R.id.btn_soft_keyboard);
        Button btnQuickKeys = dialog.findViewById(R.id.btn_quick_keys);
        Button btnMouseCursor = dialog.findViewById(R.id.btn_mouse_cursor);
        Button btnUnlink = dialog.findViewById(R.id.btn_unlink);
        Button btnExit = dialog.findViewById(R.id.btn_exit);

        // 初始化按钮状态
        updateButtonStates(btnPerformance, btnGamePad, btnMouseCursor);

        // 性能信息切换
        btnPerformance.setOnClickListener(v -> {
            if (game != null) {
                game.togglePerformanceOverlay();
                updateButtonStates(btnPerformance, btnGamePad, btnMouseCursor);
            }
        });

        // 虚拟手柄切换
        btnGamePad.setOnClickListener(v -> {
            if (game != null) {
                game.toggleOnscreenController();
                updateButtonStates(btnPerformance, btnGamePad, btnMouseCursor);
            }
        });

        // 触控灵敏度
        btnTouchSens.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.showTouchSensitivityDialog();
            }
        });

        // 软键盘
        btnSoftKeyboard.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.toggleKeyboard();
            }
        });

        // 快捷键
        btnQuickKeys.setOnClickListener(v -> {
            dismiss();
            if (game != null) {
                game.showQuickKeysDialog();
            }
        });

        // 鼠标光标切换
        btnMouseCursor.setOnClickListener(v -> {
            if (game != null) {
                game.toggleMouseCursor();
                updateButtonStates(btnPerformance, btnGamePad, btnMouseCursor);
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

    private void updateButtonStates(Button btnPerf, Button btnPad, Button btnMouse) {
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
        // 菜单关闭后恢复悬浮按钮可见
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

        // 25ms 后释放
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
