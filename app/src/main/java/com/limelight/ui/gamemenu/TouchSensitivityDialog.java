package com.limelight.ui.gamemenu;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

/**
 * 触控灵敏度调节对话框。
 * 借鉴阿西西 GameTouchFragment，精简为 X/Y 轴灵敏度 + 总开关 + 重置。
 * 灵敏度范围 1%-300%，默认 100%。
 */
public class TouchSensitivityDialog extends DialogFragment implements SeekBar.OnSeekBarChangeListener {

    private PreferenceConfiguration prefConfig;
    private Button btnToggle;
    private SeekBar sbX, sbY;
    private TextView tvXLabel, tvYLabel;

    public void setPrefConfig(PreferenceConfiguration config) {
        this.prefConfig = config;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), R.style.GameMenuDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_touch_sensitivity);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.3f);
        }

        bindViews(dialog);
        return dialog;
    }

    private void bindViews(Dialog dialog) {
        btnToggle = dialog.findViewById(R.id.btn_toggle);
        sbX = dialog.findViewById(R.id.sb_x);
        sbY = dialog.findViewById(R.id.sb_y);
        tvXLabel = dialog.findViewById(R.id.tv_x_label);
        tvYLabel = dialog.findViewById(R.id.tv_y_label);
        Button btnReset = dialog.findViewById(R.id.btn_reset);

        // 初始化值
        sbX.setProgress(prefConfig.touchSensitivityX);
        sbY.setProgress(prefConfig.touchSensitivityY);
        updateLabels();
        updateToggleButton();

        btnToggle.setOnClickListener(v -> {
            prefConfig.touchSensitivityEnabled = !prefConfig.touchSensitivityEnabled;
            saveBoolean("checkbox_touch_sensitivity_enabled", prefConfig.touchSensitivityEnabled);
            updateToggleButton();
        });

        sbX.setOnSeekBarChangeListener(this);
        sbY.setOnSeekBarChangeListener(this);

        btnReset.setOnClickListener(v -> {
            prefConfig.touchSensitivityX = 100;
            prefConfig.touchSensitivityY = 100;
            sbX.setProgress(100);
            sbY.setProgress(100);
            saveInt("seekbar_touch_sensitivity_x", 100);
            saveInt("seekbar_touch_sensitivity_y", 100);
            updateLabels();
        });
    }

    private void updateToggleButton() {
        boolean on = prefConfig.touchSensitivityEnabled;
        btnToggle.setText("触控灵敏度: " + (on ? "开" : "关"));
        btnToggle.setBackgroundResource(on ?
                R.drawable.game_menu_btn_green_selector : R.drawable.game_menu_btn_selector);
    }

    private void updateLabels() {
        tvXLabel.setText("X轴灵敏度: " + prefConfig.touchSensitivityX + "%");
        tvYLabel.setText("Y轴灵敏度: " + prefConfig.touchSensitivityY + "%");
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == sbX) {
            // 最小值 1，避免完全无响应
            prefConfig.touchSensitivityX = Math.max(1, progress);
            saveInt("seekbar_touch_sensitivity_x", prefConfig.touchSensitivityX);
            updateLabels();
        } else if (seekBar == sbY) {
            prefConfig.touchSensitivityY = Math.max(1, progress);
            saveInt("seekbar_touch_sensitivity_y", prefConfig.touchSensitivityY);
            updateLabels();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    private void saveInt(String key, int value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(key, value)
                .apply();
    }

    private void saveBoolean(String key, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(key, value)
                .apply();
    }
}
