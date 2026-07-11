package com.limelight.touch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方风格可视化按键编辑器。
 * - 屏幕中央上方可折叠控制面板
 * - 选中按钮后调节：键码、大小、透明度、隐藏
 * - 单指拖动移动，双指捏合调整大小
 */
public class TouchGamepadEditorActivity extends Activity {

    private TouchGamepadConfig config;
    private EditorPreviewView preview;
    private View panelRoot;
    private LinearLayout panelContent;
    private ScrollView scrollView;
    private ImageButton foldBtn;

    private TextView titleName;
    private SeekBar sizeSeek, alphaSeek;
    private TextView sizeValue, alphaValue;
    private CheckBox hideCheck, wheelCheck, toggleCheck;
    private Spinner keySpinner;
    private EditText customKeyInput;
    private TextView currentKeyLabel;

    private boolean panelExpanded = true;
    private boolean ignoreUiUpdate = false;

    private static final int[] PRESET_COLORS = {
            0xFFE57373, 0xFF81C784, 0xFF64B5F6, 0xFFFFB74D,
            0xFFBA68C8, 0xFF4DB6AC, 0xFFFF8A65, 0xFF90A4AE,
            0xFFFFFFFF, 0xFF424242, 0xFFDCE775, 0xFF7986CB
    };

    private static class KeyOption {
        public final String label;
        public final int keyCode;
        public final boolean isMouse;
        KeyOption(String label, int keyCode, boolean isMouse) {
            this.label = label;
            this.keyCode = keyCode;
            this.isMouse = isMouse;
        }
        @Override public String toString() { return label; }
    }
    private final List<KeyOption> keyOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = TouchGamepadConfig.load(this);
        buildKeyOptions();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 全屏预览
        preview = new EditorPreviewView(this, config);
        preview.setListener(new EditorPreviewView.OnSelectionListener() {
            @Override public void onSelectionChanged(TouchButton btn) { syncPanel(btn); }
            @Override public void onLayoutChanged() { syncPanel(preview.getSelectedButton()); }
        });
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 中间上方折叠面板
        panelRoot = createControlPanel();
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.topMargin = dp(12);
        root.addView(panelRoot, panelParams);

        // 保存/恢复默认/关闭三按钮已移入中间可滚动折叠面板内，避免在底部看不到

        setContentView(root);
        syncPanel(null);
    }

    private View createControlPanel() {
        FrameLayout container = new FrameLayout(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.argb(235, 24, 28, 32));

        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(10), dp(10));

        TextView schemeLabel = new TextView(this);
        schemeLabel.setText("方案");
        schemeLabel.setTextColor(Color.WHITE);
        schemeLabel.setTextSize(16);
        header.addView(schemeLabel);

        titleName = new TextView(this);
        titleName.setText("未选择");
        titleName.setTextColor(Color.argb(200, 255, 255, 255));
        titleName.setTextSize(14);
        titleName.setPadding(dp(8), 0, 0, 0);
        titleName.setOnClickListener(v -> {
            TouchButton btn = preview.getSelectedButton();
            if (btn != null) showRenameDialog(btn);
        });
        header.addView(titleName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton syncBtn = tinyIconButton("同步", v -> Toast.makeText(this, "已同步", Toast.LENGTH_SHORT).show());
        header.addView(syncBtn);

        card.addView(header);

        // 可折叠内容
        panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setPadding(dp(14), 0, dp(14), dp(10));

        // 键码映射
        panelContent.addView(sectionLabel("键码映射"));

        keySpinner = new Spinner(this);
        ArrayAdapter<KeyOption> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, keyOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(adapter);
        keySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (ignoreUiUpdate) return;
                TouchButton btn = preview.getSelectedButton();
                if (btn != null) {
                    KeyOption opt = keyOptions.get(pos);
                    btn.keyCode = opt.keyCode;
                    btn.isMouse = opt.isMouse;
                    preview.invalidate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        panelContent.addView(keySpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        // 当前按键显示（让用户看到实际生效的按键，避免"设置了还是显示旧的"困惑）
        currentKeyLabel = new TextView(this);
        currentKeyLabel.setTextColor(Color.parseColor("#4FC3F7"));
        currentKeyLabel.setTextSize(13);
        currentKeyLabel.setPadding(dp(8), dp(2), dp(8), dp(2));
        panelContent.addView(currentKeyLabel);

        // 自定义键码：直接输入键盘字符，如 R / 5 / SPACE / LMB
        LinearLayout customKeyRow = new LinearLayout(this);
        customKeyRow.setOrientation(LinearLayout.HORIZONTAL);
        customKeyRow.setGravity(Gravity.CENTER_VERTICAL);
        customKeyInput = new EditText(this);
        customKeyInput.setHint("输入键盘字符如 R");
        customKeyInput.setTextColor(Color.WHITE);
        customKeyInput.setHintTextColor(Color.GRAY);
        customKeyInput.setTextSize(14);
        customKeyInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        customKeyInput.setBackgroundColor(Color.argb(80, 255, 255, 255));
        customKeyRow.addView(customKeyInput, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button setKeyBtn = tinyButton("设置", v -> {
            TouchButton btn = preview.getSelectedButton();
            if (btn == null) return;
            String txt = customKeyInput.getText().toString().trim().toUpperCase();
            if (txt.isEmpty()) return;

            int code = -1;
            boolean isMouse = false;
            if (txt.equals("LMB")) { code = TouchGamepadConfig.MOUSE_LEFT; isMouse = true; }
            else if (txt.equals("RMB")) { code = TouchGamepadConfig.MOUSE_RIGHT; isMouse = true; }
            else if (txt.equals("MMB")) { code = TouchGamepadConfig.MOUSE_MIDDLE; isMouse = true; }
            else if (txt.equals("SPACE")) code = TouchGamepadConfig.VK_SPACE;
            else if (txt.equals("SHIFT")) code = TouchGamepadConfig.VK_SHIFT;
            else if (txt.equals("CTRL")) code = 0xA2; // 左 Control
            else if (txt.equals("ALT")) code = 0xA4; // 左 Alt
            else if (txt.equals("TAB")) code = TouchGamepadConfig.VK_TAB;
            else if (txt.equals("ENTER")) code = TouchGamepadConfig.VK_RETURN;
            else if (txt.equals("ESC")) code = TouchGamepadConfig.VK_ESCAPE;
            else if (txt.equals("CAPS")) code = TouchGamepadConfig.VK_CAPITAL;
            else if (txt.startsWith("0X")) {
                try { code = Integer.parseInt(txt.substring(2), 16); }
                catch (NumberFormatException e) {}
            }
            else if (txt.length() == 1) {
                code = txt.charAt(0);
            }

            if (code >= 0) {
                btn.keyCode = code;
                btn.isMouse = isMouse;
                preview.invalidate();
                customKeyInput.setText("");
                syncPanel(btn);
                Toast.makeText(this, "已设为 " + txt, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "无法识别：输入单个字符如 R，或 LMB/RMB/MMB", Toast.LENGTH_SHORT).show();
            }
        });
        customKeyRow.addView(setKeyBtn, dp(56), dp(40));
        panelContent.addView(customKeyRow);

        // 按钮大小
        sizeValue = valueText("100%");
        sizeSeek = sliderWithButtons(panelContent, "按钮大小", sizeValue, 20, 200,
                v -> {
                    TouchButton btn = preview.getSelectedButton();
                    if (btn != null) {
                        btn.sizeDp = v;
                        preview.invalidate();
                    }
                },
                v -> {
                    TouchButton btn = preview.getSelectedButton();
                    if (btn != null) {
                        btn.sizeDp = Math.max(20, btn.sizeDp + v);
                        preview.invalidate();
                        refreshSliderFromButton(btn);
                    }
                });

        // 透明度
        alphaValue = valueText("100%");
        alphaSeek = sliderWithButtons(panelContent, "透明度", alphaValue, 10, 100,
                v -> {
                    TouchButton btn = preview.getSelectedButton();
                    if (btn != null) {
                        btn.alpha = v / 100f;
                        preview.invalidate();
                    }
                },
                v -> {
                    TouchButton btn = preview.getSelectedButton();
                    if (btn != null) {
                        int pct = Math.min(100, Math.max(10, (int)(btn.alpha * 100) + v));
                        btn.alpha = pct / 100f;
                        preview.invalidate();
                        refreshSliderFromButton(btn);
                    }
                });

        // 隐藏此按钮
        hideCheck = new CheckBox(this);
        hideCheck.setText("隐藏此按钮");
        hideCheck.setTextColor(Color.WHITE);
        hideCheck.setPadding(dp(8), dp(8), 0, dp(8));
        hideCheck.setOnCheckedChangeListener((b, checked) -> {
            if (ignoreUiUpdate) return;
            TouchButton btn = preview.getSelectedButton();
            if (btn != null) {
                btn.alpha = checked ? 0f : 1f;
                preview.invalidate();
                refreshSliderFromButton(btn);
            }
        });
        panelContent.addView(hideCheck);

        // 长按轮盘开关
        wheelCheck = new CheckBox(this);
        wheelCheck.setText("长按启用轮盘");
        wheelCheck.setTextColor(Color.WHITE);
        wheelCheck.setPadding(dp(8), dp(8), 0, dp(8));
        wheelCheck.setOnCheckedChangeListener((b, checked) -> {
            if (ignoreUiUpdate) return;
            TouchButton btn = preview.getSelectedButton();
            if (btn != null) {
                btn.supportsWheel = checked;
                preview.invalidate();
            }
        });
        panelContent.addView(wheelCheck);

        // 切换模式开关：点一下保持按下，再点一下释放（用于奔跑键持续按住）
        toggleCheck = new CheckBox(this);
        toggleCheck.setText("切换模式（点一下保持按住，再点一下释放）");
        toggleCheck.setTextColor(Color.WHITE);
        toggleCheck.setPadding(dp(8), dp(8), 0, dp(8));
        toggleCheck.setOnCheckedChangeListener((b, checked) -> {
            if (ignoreUiUpdate) return;
            TouchButton btn = preview.getSelectedButton();
            if (btn != null) {
                btn.isToggle = checked;
                preview.invalidate();
            }
        });
        panelContent.addView(toggleCheck);
        TextView toggleHint = new TextView(this);
        toggleHint.setText("切换模式适合奔跑/自动跑等需要持续按住的键");
        toggleHint.setTextSize(11);
        toggleHint.setTextColor(Color.GRAY);
        toggleHint.setPadding(dp(12), 0, 0, dp(4));
        panelContent.addView(toggleHint);

        // 颜色网格
        panelContent.addView(sectionLabel("按钮颜色"));

        LinearLayout colorRows = new LinearLayout(this);
        colorRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                colorRows.addView(row);
            }
            final int c = PRESET_COLORS[i];
            Button b = new Button(this);
            b.setBackgroundColor(c);
            b.setOnClickListener(v -> {
                TouchButton btn = preview.getSelectedButton();
                if (btn != null) {
                    btn.color = c;
                    preview.invalidate();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(b, p);
        }
        panelContent.addView(colorRows);

        // 新增 / 删除按钮
        LinearLayout manageRow = new LinearLayout(this);
        manageRow.setOrientation(LinearLayout.HORIZONTAL);
        manageRow.setPadding(0, dp(10), 0, 0);

        Button addBtn = tinyButton("+ 新增按钮", v -> addNewButton());
        Button delBtn = tinyButton("删除选中", v -> deleteSelected());
        manageRow.addView(addBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
        manageRow.addView(delBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
        panelContent.addView(manageRow);

        // 保存 / 恢复默认 / 关闭 三按钮放入面板内，随内容一起滚动
        panelContent.addView(sectionLabel("操作"));
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(4), 0, dp(8));
        Button saveBtn = panelButton("保存", v -> saveAndFinish());
        Button resetBtn = panelButton("恢复默认", v -> resetDefaults());
        Button closeBtn = panelButton("关闭", v -> finish());
        actionRow.addView(saveBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
        actionRow.addView(resetBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
        actionRow.addView(closeBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
        panelContent.addView(actionRow);

        // 用 ScrollView 包裹面板内容，内容过长可上下滑动
        scrollView = new ScrollView(this);
        scrollView.addView(panelContent);
        // 限制 ScrollView 最大高度为屏幕高度的 60%，避免遮挡预览区
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
        card.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxH));

        // 折叠把手
        LinearLayout foldBar = new LinearLayout(this);
        foldBar.setOrientation(LinearLayout.VERTICAL);
        foldBar.setGravity(Gravity.CENTER);
        foldBar.setBackgroundColor(Color.argb(220, 40, 44, 48));
        foldBar.setPadding(0, dp(4), 0, dp(4));
        foldBtn = new ImageButton(this);
        foldBtn.setImageResource(android.R.drawable.arrow_up_float);
        foldBtn.setBackgroundColor(Color.TRANSPARENT);
        foldBtn.setOnClickListener(v -> togglePanel());
        foldBar.addView(foldBtn, dp(32), dp(24));
        card.addView(foldBar);

        container.addView(card);
        return container;
    }

    private void togglePanel() {
        panelExpanded = !panelExpanded;
        scrollView.setVisibility(panelExpanded ? View.VISIBLE : View.GONE);
        foldBtn.setImageResource(panelExpanded ? android.R.drawable.arrow_up_float : android.R.drawable.arrow_down_float);
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, dp(4));
        return tv;
    }

    private TextView valueText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        return tv;
    }

    private ImageButton tinyIconButton(String text, View.OnClickListener listener) {
        ImageButton btn = new ImageButton(this);
        btn.setContentDescription(text);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setImageResource(android.R.drawable.ic_menu_rotate);
        btn.setOnClickListener(listener);
        return btn;
    }

    private Button panelButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.argb(180, 48, 56, 64));
        btn.setOnClickListener(listener);
        return btn;
    }

    private interface IntValueSetter { void set(int value); }
    private interface IntValueNudge { void nudge(int delta); }

    private SeekBar sliderWithButtons(LinearLayout parent, String label, TextView valueView,
                                      int min, int max, final IntValueSetter setter,
                                      final IntValueNudge nudge) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(14);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView);
        parent.addView(row);

        LinearLayout sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);

        Button minus = tinyButton("◀", v -> nudge.nudge(-5));
        sliderRow.addView(minus, dp(32), dp(36));

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int value = p + min;
                valueView.setText(value + "%");
                if (fromUser) setter.set(value);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        sliderRow.addView(seek, new LinearLayout.LayoutParams(0, dp(36), 1));

        Button plus = tinyButton("▶", v -> nudge.nudge(5));
        sliderRow.addView(plus, dp(32), dp(36));

        parent.addView(sliderRow);
        return seek;
    }

    private Button tinyButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.argb(180, 48, 56, 64));
        btn.setOnClickListener(listener);
        return btn;
    }

    private void syncPanel(TouchButton btn) {
        ignoreUiUpdate = true;
        if (btn == null) {
            titleName.setText("未选择按钮");
            keySpinner.setEnabled(false);
            customKeyInput.setEnabled(false);
            sizeSeek.setEnabled(false);
            alphaSeek.setEnabled(false);
            hideCheck.setEnabled(false);
            wheelCheck.setEnabled(false);
            toggleCheck.setEnabled(false);
            currentKeyLabel.setText("");
        } else {
            titleName.setText(btn.label + " （点击改名）");
            keySpinner.setEnabled(true);
            customKeyInput.setEnabled(true);
            sizeSeek.setEnabled(true);
            alphaSeek.setEnabled(true);
            hideCheck.setEnabled(true);
            wheelCheck.setEnabled(true);
            toggleCheck.setEnabled(true);

            int pos = findKeyOptionPosition(btn.keyCode, btn.isMouse);
            keySpinner.setSelection(pos >= 0 ? pos : 0);
            // 无论 keyCode 是否在预设列表中，都显示当前实际生效的按键名称
            currentKeyLabel.setText("当前按键: " + formatKeyName(btn.keyCode, btn.isMouse));
            customKeyInput.setText("");
            wheelCheck.setChecked(btn.supportsWheel);
            toggleCheck.setChecked(btn.isToggle);

            refreshSliderFromButton(btn);
        }
        ignoreUiUpdate = false;
    }

    /**
     * 将 keyCode 反向映射为可读名称。
     * 不依赖 keyOptions 预设列表，覆盖所有字母数字键和特殊键，
     * 避免用户输入自定义字符后 Spinner 显示错误（"还是旧按键"问题）。
     */
    private String formatKeyName(int keyCode, boolean isMouse) {
        if (isMouse) {
            if (keyCode == TouchGamepadConfig.MOUSE_LEFT) return "鼠标左键";
            if (keyCode == TouchGamepadConfig.MOUSE_RIGHT) return "鼠标右键";
            if (keyCode == TouchGamepadConfig.MOUSE_MIDDLE) return "鼠标中键";
            return "鼠标键(0x" + Integer.toHexString(keyCode) + ")";
        }
        // 键盘键
        if (keyCode == TouchGamepadConfig.VK_SPACE) return "空格";
        if (keyCode == TouchGamepadConfig.VK_SHIFT) return "左Shift";
        if (keyCode == TouchGamepadConfig.VK_CTRL) return "左Ctrl";
        if (keyCode == TouchGamepadConfig.VK_ALT) return "左Alt";
        if (keyCode == TouchGamepadConfig.VK_TAB) return "Tab";
        if (keyCode == TouchGamepadConfig.VK_RETURN) return "Enter";
        if (keyCode == TouchGamepadConfig.VK_ESCAPE) return "Esc";
        if (keyCode == TouchGamepadConfig.VK_CAPITAL) return "Caps";
        if (keyCode == TouchGamepadConfig.VK_EQUALS) return "= (自动奔跑)";
        if (keyCode == TouchGamepadConfig.VK_WHEEL_UP) return "滚轮上";
        if (keyCode == TouchGamepadConfig.VK_WHEEL_DOWN) return "滚轮下";
        // 字母 A-Z (0x41-0x5A)
        if (keyCode >= 0x41 && keyCode <= 0x5A) return String.valueOf((char) keyCode);
        // 数字 0-9 (0x30-0x39)
        if (keyCode >= 0x30 && keyCode <= 0x39) return String.valueOf((char) keyCode);
        // F1-F12 (0x70-0x7B)
        if (keyCode >= 0x70 && keyCode <= 0x7B) return "F" + (keyCode - 0x6F);
        // 其他：显示十六进制
        return "0x" + Integer.toHexString(keyCode);
    }

    private void refreshSliderFromButton(TouchButton btn) {
        if (btn == null) return;
        sizeSeek.setProgress((int) btn.sizeDp - 20);
        sizeValue.setText((int) btn.sizeDp + "%");

        int alphaPct = Math.min(100, Math.max(10, (int)(btn.alpha * 100)));
        alphaSeek.setProgress(alphaPct - 10);
        alphaValue.setText(alphaPct + "%");
        hideCheck.setChecked(btn.alpha <= 0.05f);
    }

    private int findKeyOptionPosition(int keyCode, boolean isMouse) {
        for (int i = 0; i < keyOptions.size(); i++) {
            KeyOption o = keyOptions.get(i);
            if (o.keyCode == keyCode && o.isMouse == isMouse) return i;
        }
        return -1;
    }

    private void buildKeyOptions() {
        keyOptions.add(new KeyOption("W", TouchGamepadConfig.VK_W, false));
        keyOptions.add(new KeyOption("A", TouchGamepadConfig.VK_A, false));
        keyOptions.add(new KeyOption("S", TouchGamepadConfig.VK_S, false));
        keyOptions.add(new KeyOption("D", TouchGamepadConfig.VK_D, false));
        keyOptions.add(new KeyOption("空格 跳", TouchGamepadConfig.VK_SPACE, false));
        keyOptions.add(new KeyOption("R 换弹", TouchGamepadConfig.VK_R, false));
        keyOptions.add(new KeyOption("F 交互/拾取", TouchGamepadConfig.VK_F, false));
        keyOptions.add(new KeyOption("C 蹲", TouchGamepadConfig.VK_C, false));
        keyOptions.add(new KeyOption("Z 趴", TouchGamepadConfig.VK_Z, false));
        keyOptions.add(new KeyOption("Q 左倾", TouchGamepadConfig.VK_Q, false));
        keyOptions.add(new KeyOption("E 右倾", TouchGamepadConfig.VK_E, false));
        keyOptions.add(new KeyOption("左Shift 奔跑", TouchGamepadConfig.VK_SHIFT, false));
        keyOptions.add(new KeyOption("Caps 静步", TouchGamepadConfig.VK_CAPITAL, false));
        keyOptions.add(new KeyOption("= 自动奔跑", TouchGamepadConfig.VK_EQUALS, false));
        keyOptions.add(new KeyOption("B 换枪", TouchGamepadConfig.VK_B, false));
        keyOptions.add(new KeyOption("N 切瞄具", TouchGamepadConfig.VK_N, false));
        keyOptions.add(new KeyOption("I 检视", TouchGamepadConfig.VK_I, false));
        keyOptions.add(new KeyOption("H 轮盘", TouchGamepadConfig.VK_H, false));
        keyOptions.add(new KeyOption("T 语音", TouchGamepadConfig.VK_T, false));
        keyOptions.add(new KeyOption("M 地图", TouchGamepadConfig.VK_M, false));
        keyOptions.add(new KeyOption("P 标记", TouchGamepadConfig.VK_P, false));
        keyOptions.add(new KeyOption("Tab 背包", TouchGamepadConfig.VK_TAB, false));
        keyOptions.add(new KeyOption("Esc", TouchGamepadConfig.VK_ESCAPE, false));
        keyOptions.add(new KeyOption("Enter", TouchGamepadConfig.VK_RETURN, false));
        keyOptions.add(new KeyOption("V 道具1", TouchGamepadConfig.VK_V, false));
        keyOptions.add(new KeyOption("G 道具2", TouchGamepadConfig.VK_G, false));
        keyOptions.add(new KeyOption("X 特殊", TouchGamepadConfig.VK_X, false));
        keyOptions.add(new KeyOption("1", TouchGamepadConfig.VK_1, false));
        keyOptions.add(new KeyOption("2", TouchGamepadConfig.VK_2, false));
        keyOptions.add(new KeyOption("3", TouchGamepadConfig.VK_3, false));
        keyOptions.add(new KeyOption("4", TouchGamepadConfig.VK_4, false));
        keyOptions.add(new KeyOption("5", TouchGamepadConfig.VK_5, false));
        keyOptions.add(new KeyOption("6", TouchGamepadConfig.VK_6, false));
        keyOptions.add(new KeyOption("鼠标左键 开火", TouchGamepadConfig.MOUSE_LEFT, true));
        keyOptions.add(new KeyOption("鼠标右键 开镜", TouchGamepadConfig.MOUSE_RIGHT, true));
        keyOptions.add(new KeyOption("鼠标中键 刀", TouchGamepadConfig.MOUSE_MIDDLE, true));
    }

    private void resetDefaults() {
        config.resetToDefaults();
        preview.selectButton(null);
        preview.invalidate();
        Toast.makeText(this, "已恢复默认布局", Toast.LENGTH_SHORT).show();
    }

    private void showRenameDialog(TouchButton btn) {
        EditText input = new EditText(this);
        input.setText(btn.label);
        input.setTextColor(Color.BLACK);
        input.setSelection(btn.label.length());
        new AlertDialog.Builder(this)
                .setTitle("修改按钮名称")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        btn.label = name;
                        syncPanel(btn);
                        preview.invalidate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addNewButton() {
        TouchButton btn = new TouchButton();
        btn.id = "btn_new_" + System.currentTimeMillis();
        btn.label = "新按钮";
        btn.keyCode = TouchGamepadConfig.VK_F;
        btn.isMouse = false;
        btn.xRatio = 0.5f;
        btn.yRatio = 0.5f;
        btn.sizeDp = 48;
        btn.alpha = 0.7f;
        btn.color = PRESET_COLORS[0];
        btn.supportsWheel = false;
        config.buttons.add(btn);
        preview.selectButton(btn);
        preview.invalidate();
    }

    private void deleteSelected() {
        TouchButton btn = preview.getSelectedButton();
        if (btn == null) {
            Toast.makeText(this, "先选中一个按钮", Toast.LENGTH_SHORT).show();
            return;
        }
        config.buttons.remove(btn);
        preview.selectButton(null);
        preview.invalidate();
    }

    private void saveAndFinish() {
        config.save(this);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
    }

    /**
     * 编辑器预览视图：渲染当前按钮并处理拖动 / 捏合。
     */
    public static class EditorPreviewView extends View {

        public interface OnSelectionListener {
            void onSelectionChanged(TouchButton btn);
            void onLayoutChanged();
        }

        private final TouchGamepadConfig config;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private int viewW, viewH;
        private TouchButton selectedButton;
        private OnSelectionListener listener;

        private int dragPointerId = -1;
        private float dragOffsetX, dragOffsetY;
        private boolean pinching = false;
        private int pinchId1 = -1, pinchId2 = -1;
        private float pinchStartDist, pinchStartSize;

        public EditorPreviewView(Context ctx, TouchGamepadConfig config) {
            super(ctx);
            this.config = config;
            textPaint.setTextAlign(Paint.Align.CENTER);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setColor(Color.YELLOW);
            outlinePaint.setStrokeWidth(3);
        }

        public void setListener(OnSelectionListener l) { this.listener = l; }
        public TouchButton getSelectedButton() { return selectedButton; }

        public void selectButton(TouchButton btn) {
            selectedButton = btn;
            if (listener != null) listener.onSelectionChanged(btn);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            viewW = w;
            viewH = h;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (viewW == 0 || viewH == 0) return;
            float density = getResources().getDisplayMetrics().density;

            // 摇杆示意
            float sx = config.stickCenterX * viewW;
            float sy = config.stickCenterY * viewH;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.argb(120, 255, 255, 255));
            paint.setStrokeWidth(2 * density);
            canvas.drawCircle(sx, sy, config.stickRadiusDp * density, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawCircle(sx, sy, 6 * density, paint);

            for (TouchButton btn : config.buttons) {
                drawButton(canvas, btn, density);
            }
        }

        private void drawButton(Canvas canvas, TouchButton btn, float density) {
            if (btn.alpha <= 0.01f && btn != selectedButton) return;

            float cx = btn.xRatio * viewW;
            float cy = btn.yRatio * viewH;
            float radius = btn.sizeDp * density * 0.5f;

            int baseColor = btn.color == 0 ? Color.argb(180, 66, 165, 245) : btn.color;
            int alpha = (int) (Color.alpha(baseColor) * btn.alpha);
            int color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, radius, paint);

            if (btn == selectedButton) {
                canvas.drawCircle(cx, cy, radius + 4 * density, outlinePaint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.argb((int)(160 * btn.alpha), 255, 255, 255));
            paint.setStrokeWidth(2 * density);
            canvas.drawCircle(cx, cy, radius, paint);

            if (btn.alpha > 0.1f) {
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(Math.max(10 * density, radius * 0.65f));
                canvas.drawText(btn.label, cx, cy + textPaint.getTextSize() / 3f, textPaint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    pinching = false;
                    TouchButton hit = findButtonAt(event.getX(pointerIndex), event.getY(pointerIndex));
                    if (hit != null) {
                        selectButton(hit);
                        dragPointerId = pointerId;
                        dragOffsetX = event.getX(pointerIndex) - hit.xRatio * viewW;
                        dragOffsetY = event.getY(pointerIndex) - hit.yRatio * viewH;
                    } else {
                        selectButton(null);
                    }
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (selectedButton != null && !pinching && event.getPointerCount() == 2) {
                        int newIdx = pointerIndex;
                        int oldIdx = (newIdx == 0) ? 1 : 0;
                        float x1 = event.getX(oldIdx), y1 = event.getY(oldIdx);
                        float x2 = event.getX(newIdx), y2 = event.getY(newIdx);
                        if (isOnButton(selectedButton, x1, y1) && isOnButton(selectedButton, x2, y2)) {
                            pinching = true;
                            pinchId1 = event.getPointerId(oldIdx);
                            pinchId2 = event.getPointerId(newIdx);
                            pinchStartDist = distance(x1, y1, x2, y2);
                            pinchStartSize = selectedButton.sizeDp;
                            dragPointerId = -1;
                        }
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (pinching && event.getPointerCount() >= 2) {
                        int idx1 = event.findPointerIndex(pinchId1);
                        int idx2 = event.findPointerIndex(pinchId2);
                        if (idx1 >= 0 && idx2 >= 0) {
                            float newDist = distance(event.getX(idx1), event.getY(idx1),
                                    event.getX(idx2), event.getY(idx2));
                            if (pinchStartDist > 0) {
                                selectedButton.sizeDp = Math.max(20, Math.min(200,
                                        pinchStartSize * (newDist / pinchStartDist)));
                                if (listener != null) listener.onLayoutChanged();
                                invalidate();
                            }
                        }
                    } else if (dragPointerId != -1 && selectedButton != null) {
                        int idx = event.findPointerIndex(dragPointerId);
                        if (idx >= 0) {
                            selectedButton.xRatio = (event.getX(idx) - dragOffsetX) / viewW;
                            selectedButton.yRatio = (event.getY(idx) - dragOffsetY) / viewH;
                            selectedButton.xRatio = Math.max(0.02f, Math.min(0.98f, selectedButton.xRatio));
                            selectedButton.yRatio = Math.max(0.02f, Math.min(0.98f, selectedButton.yRatio));
                            if (listener != null) listener.onLayoutChanged();
                            invalidate();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (pinching && (pointerId == pinchId1 || pointerId == pinchId2)) {
                        pinching = false;
                        pinchId1 = pinchId2 = -1;
                    }
                    if (pointerId == dragPointerId) dragPointerId = -1;
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private TouchButton findButtonAt(float x, float y) {
            for (int i = config.buttons.size() - 1; i >= 0; i--) {
                TouchButton btn = config.buttons.get(i);
                if (isOnButton(btn, x, y)) return btn;
            }
            return null;
        }

        private boolean isOnButton(TouchButton btn, float x, float y) {
            float cx = btn.xRatio * viewW;
            float cy = btn.yRatio * viewH;
            float radius = btn.sizeDp * getResources().getDisplayMetrics().density * 0.5f;
            float dx = x - cx;
            float dy = y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }

        private static float distance(float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }
}
