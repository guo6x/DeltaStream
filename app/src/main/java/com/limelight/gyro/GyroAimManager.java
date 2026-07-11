package com.limelight.gyro;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * 陀螺仪瞄准管理器：监听设备陀螺仪，按三角洲行动的灵敏度公式计算鼠标相对移动，
 * 通过回调注入到 Moonlight 连接。
 *
 * 坐标系说明（参考 GyroWiki / JoyShockMapper 最佳实践）：
 *
 * Android 传感器坐标系基于设备自然方向（竖屏），不随屏幕旋转改变：
 *   values[0] = 绕设备 X 轴（朝右）的角速度
 *   values[1] = 绕设备 Y 轴（朝上）的角速度
 *   values[2] = 绕设备 Z 轴（出屏）的角速度
 *
 * 横屏手持（玩家像握手柄一样持设备）时，物理轴对应关系：
 *   - 竖直方向（玩家左右转动设备的旋转轴）= 设备 X 轴 → yaw（左右瞄准 / 鼠标 X）
 *   - 水平方向（玩家抬头/低头设备的旋转轴）= 设备 Y 轴 → pitch（上下瞄准 / 鼠标 Y）
 *   - Z 轴（出屏方向）= roll（拧门把手动作），不用于瞄准
 *
 * 三角洲灵敏度公式（参考 JoyShockMapper 标准公式）：
 *   delta = 校准后角速度 × (灵敏度/100) × MDV × SCALE × dt
 *   dt 必须用实测两帧间隔（event.timestamp 差值），不能用固定假设值
 */
public class GyroAimManager implements SensorEventListener {

    private static final String TAG = "GyroAim";
    private static final long CALIBRATION_DURATION_NANOS = 2_000_000_000L;
    private static final int CALIBRATION_PROGRESS_STEP_PERCENT = 5;

    /** 鼠标移动回调（在传感器线程调用，调用方需保证线程安全） */
    public interface MouseMoveCallback {
        void sendMouseMove(short deltaX, short deltaY);
    }

    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor gyroSensor;
    private final WindowManager windowManager;
    private final MouseMoveCallback callback;
    private volatile GyroAimSettings settings;

    // Sensor callbacks must not share the UI looper. A dedicated urgent-display thread
    // prevents view work, dialogs, or frame callbacks from adding aim latency.
    private final Object registrationLock = new Object();
    private HandlerThread sensorThread;

    // 状态
    private volatile boolean registered = false;
    private volatile boolean firing = false;   // 鼠标左键按下（开火中）
    private volatile boolean aiming = false;   // 鼠标右键按下（开镜中）

    // 平滑滤波器状态（指数移动平均 EMA）
    private float smoothX = 0f;
    private float smoothY = 0f;

    // 灵敏度插值状态（ADS/开火切换时平滑过渡）
    private float currentSensH = 125f;
    private float currentSensV = 65f;
    private float currentMdv = 1.33f;
    private float currentVerticalScale = 1f;
    private long lastStateChangeTime = 0;
    private boolean lastFiring = false;
    private boolean lastAiming = false;

    // 后坐力补偿累加量
    private float recoilOffsetY = 0f;

    // 微小移动累积器：避免阈值丢弃小幅度移动，让低灵敏度/慢速转动也能生效
    private float accumDeltaX = 0f;
    private float accumDeltaY = 0f;

    // 时间戳
    private long lastTimestampNanos = 0;

    public GyroAimManager(Context ctx, MouseMoveCallback cb) {
        this.context = ctx.getApplicationContext();
        this.callback = cb;
        this.settings = GyroAimSettings.load(this.context);
        this.sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
        this.gyroSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setSettings(GyroAimSettings s) {
        this.settings = s;
    }

    public GyroAimSettings getSettings() {
        return settings;
    }

    public boolean isGyroAvailable() {
        return gyroSensor != null;
    }

    /** 鼠标按键状态变更（由 Game Activity 通知） */
    public void onMouseButton(int buttonId, boolean down) {
        if (buttonId == MouseButtonPacket.BUTTON_LEFT) {
            firing = down;
        } else if (buttonId == MouseButtonPacket.BUTTON_RIGHT) {
            aiming = down;
        }
    }

    /** 判断当前陀螺仪是否应生效（基于触发方式） */
    private boolean shouldAim() {
        if (!settings.enabled) return false;
        switch (settings.triggerMode) {
            case GyroAimSettings.TRIGGER_ADS:    return aiming;
            case GyroAimSettings.TRIGGER_FIRING: return firing;
            case GyroAimSettings.TRIGGER_ALWAYS:
            default:                              return true;
        }
    }

    /** 注册传感器监听（建议在 onResume 调用） */
    public void start() {
        synchronized (registrationLock) {
            if (registered || gyroSensor == null) return;

            lastTimestampNanos = 0;
            smoothX = smoothY = 0f;
            accumDeltaX = accumDeltaY = 0f;

            HandlerThread thread = new HandlerThread("Gyro Aim", Process.THREAD_PRIORITY_URGENT_DISPLAY);
            thread.start();

            // SENSOR_DELAY_GAME is 20,000 us (about 50 Hz), which can add almost two
            // frames of input age at 90 FPS. Request the hardware maximum with no FIFO
            // batching and deliver it on our dedicated latency-sensitive thread.
            Handler handler = new Handler(thread.getLooper());
            registered = sensorManager.registerListener(
                    this,
                    gyroSensor,
                    SensorManager.SENSOR_DELAY_FASTEST,
                    0,
                    handler);

            if (!registered) {
                thread.quitSafely();
                Log.e(TAG, "Failed to register gyroscope listener");
                return;
            }

            sensorThread = thread;
            int minDelayUs = gyroSensor.getMinDelay();
            int maxRateHz = minDelayUs > 0 ? 1_000_000 / minDelayUs : 0;
            Log.i(TAG, "Gyroscope low-latency pipeline active; hardware max rate=" + maxRateHz + " Hz");
        }
    }

    /** 注销传感器监听（建议在 onPause 调用） */
    public void stop() {
        HandlerThread thread;
        synchronized (registrationLock) {
            if (!registered && sensorThread == null) return;

            if (registered) {
                sensorManager.unregisterListener(this, gyroSensor);
                registered = false;
            }

            thread = sensorThread;
            sensorThread = null;
        }

        if (thread != null) {
            thread.quitSafely();
        }
    }

    // ===== 自动校准 =====
    private volatile boolean calibrating = false;
    private int calibSampleCount = 0;
    private float calibSumX = 0, calibSumY = 0, calibSumZ = 0;
    private long calibrationStartTimestampNanos = 0;
    private int lastCalibrationProgressPercent = -CALIBRATION_PROGRESS_STEP_PERCENT;
    private volatile CalibrateCallback calibCallback = null;

    public interface CalibrateCallback {
        void onCalibrateComplete();
        void onCalibrateProgress(int collected, int total);
    }

    /** 开始自动校准（2 秒静止采样） */
    public void startCalibration(CalibrateCallback callback) {
        calibSampleCount = 0;
        calibSumX = calibSumY = calibSumZ = 0;
        calibrationStartTimestampNanos = 0;
        lastCalibrationProgressPercent = -CALIBRATION_PROGRESS_STEP_PERCENT;
        calibCallback = callback;
        // Publish this last so the sensor thread observes all reset state above.
        calibrating = true;
    }

    public boolean isCalibrating() {
        return calibrating;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_GYROSCOPE) return;

        // 校准模式：严格按传感器时间戳采集 2 秒，而不是按固定样本数。
        // 在当前 400 Hz 设备上约有 800 个样本，能显著降低瞬时抖动对零漂的污染。
        if (calibrating) {
            if (calibrationStartTimestampNanos == 0) {
                calibrationStartTimestampNanos = event.timestamp;
            }

            calibSumX += event.values[0];
            calibSumY += event.values[1];
            calibSumZ += event.values[2];
            calibSampleCount++;

            long elapsedNanos = Math.max(0, event.timestamp - calibrationStartTimestampNanos);
            int progressPercent = (int) Math.min(100,
                    (elapsedNanos * 100) / CALIBRATION_DURATION_NANOS);
            if (calibCallback != null &&
                    (progressPercent >= lastCalibrationProgressPercent + CALIBRATION_PROGRESS_STEP_PERCENT ||
                            progressPercent == 100)) {
                lastCalibrationProgressPercent = progressPercent;
                calibCallback.onCalibrateProgress(progressPercent, 100);
            }

            if (elapsedNanos >= CALIBRATION_DURATION_NANOS) {
                settings.calibOffsetX = calibSumX / calibSampleCount;
                settings.calibOffsetY = calibSumY / calibSampleCount;
                settings.calibOffsetZ = calibSumZ / calibSampleCount;
                settings.calibrated = true;
                settings.save(context);
                calibrating = false;
                if (calibCallback != null) {
                    calibCallback.onCalibrateComplete();
                }
            }
            return;
        }

        if (!shouldAim()) {
            // 不瞄准时重置平滑器与累积器，避免恢复时产生跳变
            smoothX = smoothY = 0f;
            accumDeltaX = accumDeltaY = 0f;
            lastTimestampNanos = 0;
            return;
        }

        // 计算时间步长 dt（秒）
        long now = event.timestamp;
        if (lastTimestampNanos == 0) {
            lastTimestampNanos = now;
            return;
        }
        float dt = (now - lastTimestampNanos) * 1e-9f;
        lastTimestampNanos = now;
        if (dt <= 0 || dt > 0.1f) return;  // 异常 dt 丢弃

        // 陀螺仪原始角速度（rad/s），坐标系以设备自然方向为基准
        // 减去校准时的零漂偏移
        float gyroX = event.values[0] - settings.calibOffsetX;
        float gyroY = event.values[1] - settings.calibOffsetY;
        float gyroZ = event.values[2] - settings.calibOffsetZ;

        // 根据屏幕方向选择轴映射（参考 GyroWiki / JoyShockMapper 最佳实践）
        //
        // 横屏手持时玩家"左右转动设备"绕的是竖直轴（=设备 X 轴），对应 yaw（鼠标左右）；
        // "抬头/低头"绕的是水平轴（=设备 Y 轴），对应 pitch（鼠标上下）。
        // Z 轴（roll，拧门把手动作）不用于 FPS 瞄准。
        //
        // 屏幕方向决定符号：左横屏与右横屏的"左右/上下"物理方向相反，需翻转符号。
        int rotation = windowManager.getDefaultDisplay().getRotation();

        float rawScreenX, rawScreenY;  // 屏幕坐标系角速度（X=左右yaw, Y=上下pitch）
        switch (rotation) {
            case Surface.ROTATION_90:    // 左横屏（设备逆时针旋转 90°）
                rawScreenX = gyroX;      // 设备 X 轴 → yaw（左右瞄准）
                rawScreenY = gyroY;      // 设备 Y 轴 → pitch（上下瞄准）
                break;
            case Surface.ROTATION_270:   // 右横屏（设备顺时针旋转 90°），符号反转
                rawScreenX = -gyroX;
                rawScreenY = -gyroY;
                break;
            case Surface.ROTATION_180:   // 倒置（少见）
                rawScreenX = -gyroX;
                rawScreenY = -gyroY;
                break;
            case Surface.ROTATION_0:     // 竖屏（FPS 少见）
            default:
                // 竖屏时竖直轴 = 设备 Y 轴，水平轴 = 设备 X 轴
                rawScreenX = gyroY;      // 设备 Y 轴 → yaw（左右瞄准）
                rawScreenY = gyroX;      // 设备 X 轴 → pitch（上下瞄准）
                break;
        }

        // 用户手动修正轴映射（如果自动方向仍有偏差可手动微调）
        if (settings.swapAxes) {
            float tmp = rawScreenX;
            rawScreenX = rawScreenY;
            rawScreenY = tmp;
        }
        if (settings.invertX) rawScreenX = -rawScreenX;
        if (settings.invertY) rawScreenY = -rawScreenY;

        // 收紧型阈值（Soft Cutoff，参考 GyroWiki）— 替代硬死区
        // 低于 cutoff 速度时平滑衰减而非直接归零，不丢失真实输入
        rawScreenX = applySoftCutoff(rawScreenX, settings.cutoffSpeed, settings.cutoffRecovery);
        rawScreenY = applySoftCutoff(rawScreenY, settings.cutoffSpeed, settings.cutoffRecovery);

        // 软分层平滑（Soft Tiered Smoothing，参考 GyroWiki）— 替代简单 EMA
        // 小输入走平滑（抑制手抖），大输入直通（保证响应），中间线性过渡
        smoothX = softTieredSmooth(smoothX, rawScreenX, settings.softSmoothThreshold);
        smoothY = softTieredSmooth(smoothY, rawScreenY, settings.softSmoothThreshold);

        // 检测开火/开镜状态变化，记录时间用于过渡
        if (firing != lastFiring || aiming != lastAiming) {
            lastFiring = firing;
            lastAiming = aiming;
            lastStateChangeTime = SystemClock.elapsedRealtime();
        }

        // 目标灵敏度组 — 优先级：开镜ADS > 开火 > 常规
        // 开镜时若启用 ADS 灵敏度组，使用 ADS 参数（远距离更稳）
        float targetH, targetV, targetMdv, targetVScale;
        if (aiming && settings.adsSensEnabled) {
            // 开镜自动降灵敏度：使用专门的 ADS 灵敏度组
            targetH = settings.sensAdsHorizontal;
            targetV = settings.sensAdsVertical;
            targetMdv = settings.mdvAds;
        } else if (firing) {
            targetH = settings.sensFiringHorizontal;
            targetV = settings.sensFiringVertical;
            targetMdv = settings.mdvFiring;
        } else {
            targetH = settings.sensHorizontal;
            targetV = settings.sensVertical;
            targetMdv = settings.mdv;
        }
        targetVScale = aiming ? settings.aimVerticalScale : 1f;

        // 灵敏度插值过渡（避免 ADS/开火切换时突变）
        float transitionTime = Math.max(1f, settings.transitionTimeMs);
        float t = Math.min(1f, (SystemClock.elapsedRealtime() - lastStateChangeTime) / transitionTime);
        // 使用 ease-out 让过渡更自然
        t = t * (2f - t);
        currentSensH = lerp(currentSensH, targetH, t);
        currentSensV = lerp(currentSensV, targetV, t);
        currentMdv = lerp(currentMdv, targetMdv, t);
        currentVerticalScale = lerp(currentVerticalScale, targetVScale, t);

        // 三角洲灵敏度公式
        // 水平: delta = 角速度 × (水平灵敏度/100) × MDV × SCALE × dt
        // 垂直: delta = 角速度 × (垂直灵敏度/100) × MDV × SCALE × dt × 开镜垂直倍率
        float deltaX = smoothX * (currentSensH / 100f) * currentMdv * settings.scale * dt;
        float deltaY = smoothY * (currentSensV / 100f) * currentMdv * settings.scale * dt * currentVerticalScale;

        // 后坐力补偿（一键压枪）：开火时持续给 Y 轴一个向上的鼠标移动（压枪手感）
        // 需同时开启 recoilEnabled 开关且强度 > 0 才生效
        if (firing && settings.recoilEnabled && settings.recoilCompensation > 0f) {
            recoilOffsetY += settings.recoilCompensation * dt;
            if (recoilOffsetY > 0.5f) {
                deltaY -= recoilOffsetY;
                recoilOffsetY *= 0.85f; // 衰减，避免无限累加
            }
        } else {
            recoilOffsetY = 0f;
        }

        // 反转 Y 轴（屏幕 Y 向下，俯仰正方向向上 → 鼠标向上 = Y 负）
        deltaY = -deltaY;

        // 限幅，避免单帧过大跳变
        deltaX = clamp(deltaX, -127, 127);
        deltaY = clamp(deltaY, -127, 127);

        // 累积式发送：保留小数部分到下一帧，确保低灵敏度/慢速转动也能产生有效移动
        // （旧版本 >=1f 阈值会丢弃大量小幅度移动，导致"调灵敏度跟没调一样"）
        accumDeltaX += deltaX;
        accumDeltaY += deltaY;
        int sendX = (int) accumDeltaX;
        int sendY = (int) accumDeltaY;
        if (sendX != 0 || sendY != 0) {
            // 发送整数部分，保留小数部分继续累积
            accumDeltaX -= sendX;
            accumDeltaY -= sendY;
            // 再次限幅保护
            sendX = Math.max(-127, Math.min(127, sendX));
            sendY = Math.max(-127, Math.min(127, sendY));
            callback.sendMouseMove((short) sendX, (short) sendY);
        }
    }

    /**
     * 收紧型阈值（Soft Cutoff，参考 GyroWiki）
     * 速度低于 cutoff 时平滑衰减（非直接归零），[cutoff, cutoff+recovery] 平滑过渡到完全通过。
     * 相比硬死区：不丢真实输入、无跳变，远距离慢速瞄准更准。
     */
    private static float applySoftCutoff(float v, float cutoff, float recovery) {
        float absV = Math.abs(v);
        if (cutoff <= 0.001f) return v;  // cutoff=0 禁用
        float fullPass = cutoff + recovery;
        if (absV >= fullPass) return v;  // 超过恢复区，完全通过
        // smoothstep 在 [0, fullPass] 从 0 平滑过渡到 1
        float t = absV / fullPass;
        float scale = t * t * (3f - 2f * t);
        return v * scale;
    }

    /**
     * 软分层平滑（Soft Tiered Smoothing，参考 GyroWiki）
     * 输入幅度小于 threshold1=threshold/2 时走平滑滤波（抑制手抖），
     * 大于 threshold 时完全直通（保证大动作响应），
     * [threshold1, threshold] 之间线性过渡。
     * 相比简单 EMA：不会平滑掉有意的大幅移动。
     */
    private float softTieredSmooth(float smoothed, float input, float threshold) {
        if (threshold <= 0.001f) return input;  // 阈值=0 禁用平滑
        float threshold1 = threshold * 0.5f;
        float range = threshold - threshold1;
        float absInput = Math.abs(input);
        float directWeight = (absInput - threshold1) / range;
        directWeight = Math.max(0f, Math.min(1f, directWeight));
        // 平滑部分用 EMA（系数 0.75 = 较强平滑）
        float newSmoothed = smoothed * 0.75f + input * 0.25f;
        return input * directWeight + newSmoothed * (1f - directWeight);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static float lerp(float current, float target, float t) {
        return current + (target - current) * t;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不处理
    }
}
