package com.limelight.touch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 虚拟触控手柄覆盖层：手游 FPS 风格。
 * - 左下角摇杆：输出 WASD 或手柄左摇杆
 * - 右侧触摸区：滑动控制视角（鼠标移动）
 * - 可配置按钮：开火/开镜/跳/蹲/换弹/趴等
 * - 长按"设置"按钮进入编辑模式，可拖动按钮位置
 *
 * 输入通过 {@link InputInjector} 回调注入到串流连接。
 */
@SuppressLint("ClickableViewAccessibility")
public class TouchGamepadOverlay extends View {

    public interface InputInjector {
        void sendKeyDown(short keyCode);
        void sendKeyUp(short keyCode);
        void sendMouseButtonDown(byte button);
        void sendMouseButtonUp(byte button);
        void sendMouseMove(short deltaX, short deltaY);
        boolean isConnected();
    }

    public interface OnGestureListener {
        void onOpenGyroSettings();
    }

    private static final float STICK_DEADZONE = 0.18f;
    private static final float STICK_MAX = 1.0f;
    private static final long REPEAT_INTERVAL_MS = 8; // ~120Hz，移动更顺滑

    private TouchGamepadConfig config;
    private InputInjector injector;
    private OnGestureListener gestureListener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int viewW, viewH;
    private float density;

    // 摇杆状态
    private final PointF stickCenter = new PointF();
    private final PointF stickThumb = new PointF();
    private int stickPointerId = -1;
    private float stickRadiusPx;
    private float stickX, stickY; // -1..1
    private boolean stickActive = false;

    // 右侧触摸瞄准状态
    private final Map<Integer, PointF> aimLastPos = new HashMap<>();
    private final Set<Integer> aimPointers = new HashSet<>();

    // 按钮状态
    private final Map<String, Boolean> buttonPressed = new HashMap<>();
    private final Map<String, Integer> buttonPointerMap = new HashMap<>();
    private TouchButton draggingButton = null;
    private TouchButton wheelButton = null;
    private float wheelLastX, wheelLastY;
    private int wheelLastSector = -1;
    private float dragOffsetX, dragOffsetY;
    private boolean editMode = false;
    private long editModeStartTime;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable currentLongPressRunnable = null;

    // 设置按钮（右下角）：短按打开按键编辑器，长按打开陀螺仪设置
    private float settingsBtnX, settingsBtnY, settingsBtnRadius;
    private int settingsPointerId = -1;
    private boolean settingsLongPressTriggered = false;

    // 摇杆 W/A/S/D 状态机
    private boolean wDown, aDown, sDown, dDown;

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable stickRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            applyStickMovement();
            repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };

    public TouchGamepadOverlay(Context context) {
        super(context);
        init(context);
    }

    public TouchGamepadOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TouchGamepadOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context ctx) {
        config = TouchGamepadConfig.load(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        density = dm.density;

        paint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        stickBasePaint.setColor(Color.argb((int) (80 * config.buttonAlpha), 255, 255, 255));
        stickBasePaint.setStyle(Paint.Style.STROKE);
        stickBasePaint.setStrokeWidth(4 * density);
        stickThumbPaint.setColor(Color.argb((int) (180 * config.buttonAlpha), 88, 166, 255));
        stickThumbPaint.setStyle(Paint.Style.FILL);

        setClickable(true);
        setOnTouchListener((v, event) -> handleTouch(event));
    }

    public void setInjector(InputInjector injector) {
        this.injector = injector;
    }

    public void setOnGestureListener(OnGestureListener listener) {
        this.gestureListener = listener;
    }

    public void setConfig(TouchGamepadConfig config) {
        this.config = config;
        invalidate();
    }

    public TouchGamepadConfig getConfig() {
        return config;
    }

    private void openButtonEditor() {
        Context ctx = getContext();
        Intent intent = new Intent(ctx, TouchGamepadEditorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        stickRadiusPx = config.stickRadiusDp * density;
        stickCenter.set(config.stickCenterX * w, config.stickCenterY * h);
        stickThumb.set(stickCenter.x, stickCenter.y);
        stickActive = false;
        settingsBtnRadius = 22 * density;
        settingsBtnX = w - settingsBtnRadius - 8 * density;
        settingsBtnY = h - settingsBtnRadius - 8 * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewW == 0 || viewH == 0) return;

        // 动态摇杆：只在激活时绘制，跟随手指位置
        if (stickActive) {
            stickBasePaint.setColor(Color.argb((int) (60 * config.buttonAlpha), 255, 255, 255));
            canvas.drawCircle(stickCenter.x, stickCenter.y, stickRadiusPx, stickBasePaint);
            canvas.drawCircle(stickThumb.x, stickThumb.y, stickRadiusPx * 0.45f, stickThumbPaint);
        }

        // 按钮
        for (TouchButton btn : config.buttons) {
            drawButton(canvas, btn);
        }

        // 轮盘模式 UI（长按支持轮盘的按钮时显示）
        if (wheelButton != null) {
            drawWheelOverlay(canvas);
        }

        // 设置按钮：短按打开按键编辑器，长按打开陀螺仪设置
        paint.setColor(Color.argb(120, 255, 255, 255));
        canvas.drawCircle(settingsBtnX, settingsBtnY, settingsBtnRadius, paint);
        textPaint.setTextSize(12 * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("设置", settingsBtnX, settingsBtnY + textPaint.getTextSize() / 3f, textPaint);
    }

    private void drawButton(Canvas canvas, TouchButton btn) {
        if (btn.alpha <= 0.01f) return;

        float cx = btn.xRatio * viewW;
        float cy = btn.yRatio * viewH;
        float radius = btn.sizeDp * density / 2f;
        boolean pressed = Boolean.TRUE.equals(buttonPressed.get(btn.id));

        // 按下时缩放到 85%，松开恢复 100%
        float scale = pressed ? 0.85f : 1.0f;
        float drawRadius = radius * scale;

        int baseColor = btn.color != 0 ? btn.color : getDefaultButtonColor(btn);
        if (pressed) {
            baseColor = adjustBrightness(baseColor, 0.7f);
        }
        // 应用按钮独立透明度
        int alpha = (int) (Color.alpha(baseColor) * btn.alpha);
        int color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        paint.setColor(color);
        canvas.drawCircle(cx, cy, drawRadius, paint);

        // 边框：toggle 按钮在按下状态用绿色描边表示"锁定"
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        if (btn.isToggle && pressed) {
            paint.setColor(Color.argb(255, 76, 175, 80));
            paint.setStrokeWidth(3 * density);
        } else {
            paint.setColor(Color.argb((int)(160 * btn.alpha), 255, 255, 255));
        }
        canvas.drawCircle(cx, cy, drawRadius, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextSize(Math.max(10 * density, drawRadius * 0.45f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(btn.label, cx, cy + textPaint.getTextSize() / 3f, textPaint);

        // toggle 按钮在按下状态显示 "ON" 标记，提示当前处于锁定按下
        if (btn.isToggle && pressed) {
            textPaint.setTextSize(Math.max(8 * density, drawRadius * 0.3f));
            textPaint.setColor(Color.parseColor("#4CAF50"));
            canvas.drawText("ON", cx, cy - drawRadius - 4 * density, textPaint);
        }
    }

    private void drawWheelOverlay(Canvas canvas) {
        float cx = wheelButton.xRatio * viewW;
        float cy = wheelButton.yRatio * viewH;
        float radius = wheelButton.sizeDp * density * 1.6f;

        // 计算手指方向（8 向）
        Integer wheelPid = buttonPointerMap.get(wheelButton.id);
        int selectedSector = -1;
        if (wheelPid != null) {
            float dx = wheelLastX - cx;
            float dy = wheelLastY - cy;
            if (dx * dx + dy * dy > radius * radius * 0.04f) {
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;
                // 0=右，每 45° 一个扇区，+22.5° 对齐中心
                selectedSector = (int) ((angle + 22.5f) / 45f) % 8;
            }
        }

        // 方向改变时提供震动反馈
        if (selectedSector != -1 && selectedSector != wheelLastSector) {
            wheelLastSector = selectedSector;
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }

        // 轮盘底色与边框
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * density);
        paint.setColor(Color.argb(180, 255, 255, 255));
        canvas.drawCircle(cx, cy, radius, paint);

        // 绘制 8 条方向分隔线
        paint.setStrokeWidth(1 * density);
        paint.setColor(Color.argb(100, 255, 255, 255));
        for (int i = 0; i < 8; i++) {
            float rad = (float) Math.toRadians(i * 45 + 22.5f);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(rad) * radius,
                    cy + (float) Math.sin(rad) * radius, paint);
        }

        // 绘制 8 个方向扇区
        String[] labels = {"右", "右下", "下", "左下", "左", "左上", "上", "右上"};
        for (int i = 0; i < 8; i++) {
            float rad = (float) Math.toRadians(i * 45);
            float sx = cx + (float) Math.cos(rad) * radius;
            float sy = cy + (float) Math.sin(rad) * radius;
            boolean selected = (i == selectedSector);
            if (selected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(220, 76, 175, 80));
                canvas.drawCircle(sx, sy, 20 * density, paint);
                // 绘制从中心到选中方向的指示线
                paint.setStrokeWidth(4 * density);
                paint.setColor(Color.argb(200, 76, 175, 80));
                canvas.drawLine(cx, cy, sx, sy, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2 * density);
            canvas.drawCircle(sx, sy, selected ? 20 * density : 16 * density, paint);
            textPaint.setTextSize(selected ? 14 * density : 12 * density);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(labels[i], sx, sy + textPaint.getTextSize() / 3f, textPaint);
        }

        // 中心提示文字
        if (selectedSector >= 0) {
            textPaint.setTextSize(12 * density);
            textPaint.setColor(Color.argb(220, 76, 175, 80));
            canvas.drawText("已选择: " + labels[selectedSector], cx, cy - 6 * density, textPaint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private int getDefaultButtonColor(TouchButton btn) {
        if (btn.keyCode == TouchGamepadConfig.MOUSE_LEFT) return Color.parseColor("#D32F2F"); // 开火红色
        if (btn.keyCode == TouchGamepadConfig.MOUSE_RIGHT) return Color.parseColor("#1976D2"); // 开镜蓝色
        if (btn.keyCode == TouchGamepadConfig.VK_SPACE) return Color.parseColor("#388E3C");   // 跳绿色
        if (btn.keyCode == TouchGamepadConfig.VK_C || btn.keyCode == TouchGamepadConfig.VK_F) return Color.parseColor("#FBC02D"); // 蹲/趴黄色
        return Color.parseColor("#616161");
    }

    private int adjustBrightness(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.min(255, (int) (Color.red(color) * factor));
        int g = Math.min(255, (int) (Color.green(color) * factor));
        int b = Math.min(255, (int) (Color.blue(color) * factor));
        return Color.argb(a, r, g, b);
    }

    private boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (tryStartSettingsDrag(x, y, pointerId)) {
                    return true;
                }
                if (!editMode) {
                    // 按钮优先处理，避免被摇杆/触摸瞄准区拦截导致"点了没反应"
                    if (tryPressButton(x, y, pointerId)) return true;
                    if (tryStartStick(x, y, pointerId)) return true;
                    if (tryStartAim(x, y, pointerId)) return true;
                } else {
                    if (tryGrabButton(x, y, pointerId)) return true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pointerId == settingsPointerId) {
                    long dt = SystemClock.elapsedRealtime() - editModeStartTime;
                    settingsPointerId = -1;
                    if (!settingsLongPressTriggered && dt < 600) {
                        // 短按打开按键编辑器
                        openButtonEditor();
                    }
                    settingsLongPressTriggered = false;
                    return true;
                }
                if (pointerId == stickPointerId) {
                    releaseStick();
                    return true;
                }
                if (aimPointers.contains(pointerId)) {
                    aimPointers.remove(pointerId);
                    aimLastPos.remove(pointerId);
                    return true;
                }

                // 取消可能等待中的按钮长按任务
                if (currentLongPressRunnable != null) {
                    longPressHandler.removeCallbacks(currentLongPressRunnable);
                    currentLongPressRunnable = null;
                }

                // 轮盘模式释放
                if (wheelButton != null && buttonPointerMap.get(wheelButton.id) != null
                        && buttonPointerMap.get(wheelButton.id) == pointerId) {
                    pressButton(wheelButton, false);
                    buttonPressed.put(wheelButton.id, false);
                    buttonPointerMap.remove(wheelButton.id);
                    wheelButton = null;
                    wheelLastSector = -1;
                    invalidate();
                    config.save(getContext());
                    return true;
                }

                // 拖动模式释放
                if (draggingButton != null && buttonPointerMap.get(draggingButton.id) != null
                        && buttonPointerMap.get(draggingButton.id) == pointerId) {
                    buttonPointerMap.remove(draggingButton.id);
                    draggingButton = null;
                    config.save(getContext());
                    invalidate();
                    return true;
                }

                releaseButtonByPointer(pointerId);
                return true;

            case MotionEvent.ACTION_CANCEL:
                resetAll();
                return true;
        }
        return true;
    }

    private boolean tryStartSettingsDrag(float x, float y, int pointerId) {
        float dx = x - settingsBtnX;
        float dy = y - settingsBtnY;
        // 触发区域限制为 1.3 倍半径，避免右下角操作按钮被误识别为设置按钮
        if (dx * dx + dy * dy <= settingsBtnRadius * settingsBtnRadius * 1.69f) {
            settingsPointerId = pointerId;
            editModeStartTime = SystemClock.elapsedRealtime();
            settingsLongPressTriggered = false;
            // 长按 600ms 打开游戏菜单（避免与6指操作冲突）
            postDelayed(() -> {
                if (settingsPointerId == pointerId && !settingsLongPressTriggered) {
                    settingsLongPressTriggered = true;
                    if (gestureListener != null) {
                        gestureListener.onOpenGyroSettings();
                    }
                }
            }, 600);
            return true;
        }
        return false;
    }

    private boolean tryStartStick(float x, float y, int pointerId) {
        // 只在左半边屏幕启动动态摇杆；若该位置有按钮已被 tryPressButton 处理，则不会走到这里
        if (x > viewW * 0.5f) return false;
        stickCenter.set(x, y);
        stickThumb.set(x, y);
        stickPointerId = pointerId;
        stickActive = true;
        updateStick(x, y);
        repeatHandler.post(stickRepeatRunnable);
        return true;
    }

    private void updateStick(float x, float y) {
        float dx = x - stickCenter.x;
        float dy = y - stickCenter.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxDist = stickRadiusPx;
        if (dist > maxDist) {
            dx = dx / dist * maxDist;
            dy = dy / dist * maxDist;
            dist = maxDist;
        }
        stickThumb.set(stickCenter.x + dx, stickCenter.y + dy);
        stickX = dist > STICK_DEADZONE * maxDist ? dx / maxDist : 0;
        stickY = dist > STICK_DEADZONE * maxDist ? dy / maxDist : 0;
        invalidate();
    }

    private void releaseStick() {
        stickPointerId = -1;
        stickActive = false;
        stickX = 0;
        stickY = 0;
        // 回到默认中心，等待下次手指按下重新定位
        stickCenter.set(config.stickCenterX * viewW, config.stickCenterY * viewH);
        stickThumb.set(stickCenter.x, stickCenter.y);
        repeatHandler.removeCallbacks(stickRepeatRunnable);
        applyStickMovement(); // 释放所有方向键
        invalidate();
    }

    private void applyStickMovement() {
        if (injector == null || !injector.isConnected()) return;

        boolean w = stickY < -STICK_DEADZONE;
        boolean s = stickY > STICK_DEADZONE;
        boolean a = stickX < -STICK_DEADZONE;
        boolean d = stickX > STICK_DEADZONE;

        if (config.stickOutputKeyboard) {
            if (w != wDown) { wDown = w; sendKey(TouchGamepadConfig.VK_W, wDown); }
            if (s != sDown) { sDown = s; sendKey(TouchGamepadConfig.VK_S, sDown); }
            if (a != aDown) { aDown = a; sendKey(TouchGamepadConfig.VK_A, aDown); }
            if (d != dDown) { dDown = d; sendKey(TouchGamepadConfig.VK_D, dDown); }
        } else {
            // 手柄左摇杆模式：通过 MoonBridge 注入手柄模拟（简化版：仍用键盘）
            if (w != wDown) { wDown = w; sendKey(TouchGamepadConfig.VK_W, wDown); }
            if (s != sDown) { sDown = s; sendKey(TouchGamepadConfig.VK_S, sDown); }
            if (a != aDown) { aDown = a; sendKey(TouchGamepadConfig.VK_A, aDown); }
            if (d != dDown) { dDown = d; sendKey(TouchGamepadConfig.VK_D, dDown); }
        }
    }

    private void sendKey(int vk, boolean down) {
        if (injector == null) return;
        if (down) injector.sendKeyDown((short) vk);
        else injector.sendKeyUp((short) vk);
    }

    private boolean tryStartAim(float x, float y, int pointerId) {
        if (!config.touchAimEnabled) return false;
        if (x >= viewW * config.touchAimAreaLeftRatio) {
            aimPointers.add(pointerId);
            aimLastPos.put(pointerId, new PointF(x, y));
            return true;
        }
        return false;
    }

    private void handleMove(MotionEvent event) {
        // 摇杆
        if (stickPointerId != -1) {
            int idx = event.findPointerIndex(stickPointerId);
            if (idx >= 0) {
                updateStick(event.getX(idx), event.getY(idx));
            }
        }

        // 轮盘模式：手指移动转为鼠标移动（用于 PC 端轮盘选择）
        if (wheelButton != null) {
            Integer wheelPid = buttonPointerMap.get(wheelButton.id);
            if (wheelPid != null) {
                int idx = event.findPointerIndex(wheelPid);
                if (idx >= 0) {
                    float x = event.getX(idx);
                    float y = event.getY(idx);
                    float dx = x - wheelLastX;
                    float dy = y - wheelLastY;
                    wheelLastX = x;
                    wheelLastY = y;
                    if (injector != null && injector.isConnected()) {
                        // 轮盘移动灵敏度比普通触摸瞄准低一些，便于精确选择
                        float sens = config.touchAimSensitivity * 0.35f;
                        injector.sendMouseMove((short) (dx * sens), (short) (dy * sens * config.touchAimVerticalScale));
                    }
                }
            }
        }

        // 触摸瞄准（轮盘手指不参与瞄准）
        for (int pid : aimPointers) {
            if (wheelButton != null && buttonPointerMap.get(wheelButton.id) != null
                    && buttonPointerMap.get(wheelButton.id) == pid) {
                continue;
            }
            int idx = event.findPointerIndex(pid);
            if (idx < 0) continue;
            float x = event.getX(idx);
            float y = event.getY(idx);
            PointF last = aimLastPos.get(pid);
            if (last != null) {
                float dx = x - last.x;
                float dy = y - last.y;
                if (injector != null && injector.isConnected()) {
                    float sens = config.touchAimSensitivity;
                    injector.sendMouseMove((short) (dx * sens), (short) (dy * sens * config.touchAimVerticalScale));
                }
            }
            last.set(x, y);
        }

        // 拖动按钮（编辑模式或普通模式的长按拖动）
        if (draggingButton != null) {
            Integer dragPid = buttonPointerMap.get(draggingButton.id);
            if (dragPid != null) {
                int idx = event.findPointerIndex(dragPid);
                if (idx >= 0) {
                    draggingButton.xRatio = (event.getX(idx) - dragOffsetX) / viewW;
                    draggingButton.yRatio = (event.getY(idx) - dragOffsetY) / viewH;
                    draggingButton.xRatio = Math.max(0.02f, Math.min(0.98f, draggingButton.xRatio));
                    draggingButton.yRatio = Math.max(0.02f, Math.min(0.98f, draggingButton.yRatio));
                    invalidate();
                }
            }
        }
    }

    private boolean tryPressButton(float x, float y, int pointerId) {
        for (TouchButton btn : config.buttons) {
            float cx = btn.xRatio * viewW;
            float cy = btn.yRatio * viewH;
            if (btn.alpha <= 0.01f) continue;
            float radius = btn.sizeDp * density / 2f;
            float dx = x - cx;
            float dy = y - cy;
            if (dx * dx + dy * dy <= radius * radius) {
                // 切换型按钮：点一下翻转按下状态，手指抬起不释放（保持）
                if (btn.isToggle) {
                    boolean wasPressed = Boolean.TRUE.equals(buttonPressed.get(btn.id));
                    boolean nowPressed = !wasPressed;
                    buttonPressed.put(btn.id, nowPressed);
                    // toggle 按钮不绑定 pointer（手指抬起不触发释放）
                    pressButton(btn, nowPressed);
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                    return true;
                }
                buttonPressed.put(btn.id, true);
                buttonPointerMap.put(btn.id, pointerId);
                pressButton(btn, true);
                invalidate();

                // 300ms 后若仍按住，支持轮盘的按钮进入轮盘模式
                currentLongPressRunnable = () -> {
                    if (buttonPointerMap.get(btn.id) != null && buttonPointerMap.get(btn.id) == pointerId
                            && Boolean.TRUE.equals(buttonPressed.get(btn.id)) && btn.supportsWheel) {
                        // 轮盘模式：保持 keyDown，手指移动转为鼠标移动
                        wheelButton = btn;
                        wheelLastX = x;
                        wheelLastY = y;
                        wheelLastSector = -1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        invalidate();
                    }
                    currentLongPressRunnable = null;
                };
                longPressHandler.postDelayed(currentLongPressRunnable, 300);
                return true;
            }
        }
        return false;
    }

    private boolean tryGrabButton(float x, float y, int pointerId) {
        for (TouchButton btn : config.buttons) {
            float cx = btn.xRatio * viewW;
            float cy = btn.yRatio * viewH;
            float radius = btn.sizeDp * density / 2f;
            float dx = x - cx;
            float dy = y - cy;
            if (dx * dx + dy * dy <= radius * radius * 1.5f) {
                draggingButton = btn;
                dragOffsetX = x - cx;
                dragOffsetY = y - cy;
                buttonPointerMap.put(btn.id, pointerId);
                invalidate();
                return true;
            }
        }
        return false;
    }

    private void releaseButtonByPointer(int pointerId) {
        for (Map.Entry<String, Integer> entry : new HashMap<>(buttonPointerMap).entrySet()) {
            if (entry.getValue() == pointerId) {
                buttonPressed.put(entry.getKey(), false);
                TouchButton btn = findButton(entry.getKey());
                if (btn != null) pressButton(btn, false);
                buttonPointerMap.remove(entry.getKey());
                invalidate();
            }
        }
    }

    private TouchButton findButton(String id) {
        for (TouchButton btn : config.buttons) {
            if (btn.id.equals(id)) return btn;
        }
        return null;
    }

    private void pressButton(TouchButton btn, boolean down) {
        if (injector == null || !injector.isConnected()) return;
        if (btn.isMouse) {
            byte b = (byte) btn.keyCode;
            if (down) injector.sendMouseButtonDown(b);
            else injector.sendMouseButtonUp(b);
        } else {
            short vk = (short) btn.keyCode;
            if (down) injector.sendKeyDown(vk);
            else injector.sendKeyUp(vk);
        }
    }

    private void resetAll() {
        releaseStick();
        for (String id : new HashSet<>(buttonPressed.keySet())) {
            buttonPressed.put(id, false);
            TouchButton btn = findButton(id);
            if (btn != null) pressButton(btn, false);
        }
        buttonPointerMap.clear();
        aimPointers.clear();
        aimLastPos.clear();
        settingsPointerId = -1;
        draggingButton = null;
        wheelButton = null;
        wheelLastSector = -1;
        if (currentLongPressRunnable != null) {
            longPressHandler.removeCallbacks(currentLongPressRunnable);
            currentLongPressRunnable = null;
        }
        invalidate();
    }

    /** 设置编辑模式开关 */
    public void setEditMode(boolean edit) {
        this.editMode = edit;
        if (!edit) config.save(getContext());
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }
}
