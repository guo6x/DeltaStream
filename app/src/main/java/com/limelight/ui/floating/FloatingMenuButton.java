package com.limelight.ui.floating;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 轻量级悬浮菜单按钮。
 * 借鉴阿西西 AXFloatingView 的设计，但大幅简化：
 * - 拖拽 + 点击双模式
 * - 自动吸附左/右边缘
 * - 点击时触发回调
 */
public class FloatingMenuButton extends FrameLayout {

    private final TextView label;
    private OnMenuClickListener clickListener;

    // 拖拽状态
    private float downX, downY;
    private float startX, startY;
    private boolean isDragging = false;
    private static final int CLICK_THRESHOLD = 10; // dp
    private static final int EDGE_MARGIN = 4; // dp

    private int screenWidth;
    private int screenHeight;
    private int buttonSize;

    public interface OnMenuClickListener {
        void onMenuClick();
    }

    public FloatingMenuButton(Context context) {
        super(context);
        buttonSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 42, getResources().getDisplayMetrics());
        int textSizeSp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, getResources().getDisplayMetrics());

        LayoutParams lp = new LayoutParams(buttonSize, buttonSize);
        setLayoutParams(lp);

        setBackgroundResource(com.limelight.R.drawable.game_menu_floating_btn);

        label = new TextView(context);
        label.setText("≡");
        label.setTextColor(Color.WHITE);
        label.setTextSize(textSizeSp);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        textLp.gravity = Gravity.CENTER;
        label.setLayoutParams(textLp);
        addView(label);

        // 获取屏幕尺寸
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point size = new android.graphics.Point();
        wm.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
    }

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = getX();
                startY = getY();
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (!isDragging && Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                    isDragging = true;
                }
                if (isDragging) {
                    float newX = startX + dx;
                    float newY = startY + dy;
                    // 限制在屏幕范围内
                    int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EDGE_MARGIN, getResources().getDisplayMetrics());
                    newX = Math.max(margin, Math.min(newX, screenWidth - getWidth() - margin));
                    newY = Math.max(margin, Math.min(newY, screenHeight - getHeight() - margin));
                    setX(newX);
                    setY(newY);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    // 自动吸附到最近的边缘
                    snapToEdge();
                } else {
                    // 点击事件
                    if (clickListener != null) {
                        clickListener.onMenuClick();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** 吸附到最近的边缘 */
    private void snapToEdge() {
        float centerX = getX() + getWidth() / 2f;
        float targetX;
        if (centerX < screenWidth / 2f) {
            // 吸附到左边
            int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EDGE_MARGIN, getResources().getDisplayMetrics());
            targetX = margin;
        } else {
            // 吸附到右边
            targetX = screenWidth - getWidth() - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EDGE_MARGIN, getResources().getDisplayMetrics());
        }
        animate().x(targetX).setDuration(200).start();
    }

    /** 设置初始位置（屏幕右侧偏上，避免遮挡左侧游戏按键） */
    public void setDefaultPosition() {
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EDGE_MARGIN, getResources().getDisplayMetrics());
        int yOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, getResources().getDisplayMetrics());
        int width = getWidth() > 0 ? getWidth() : buttonSize;
        setX(screenWidth - width - margin);
        setY(yOffset);
    }
}
