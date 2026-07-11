# DeltaStream

> PC 到平板的低延迟串流客户端，专为《三角洲行动》优化

DeltaStream 是基于开源项目 [Moonlight Android](https://github.com/moonlight-stream/moonlight-android)（GPLv3）二次开发的串流客户端，针对平板玩 PC 游戏的场景做了深度优化。

## 核心功能

### 免费功能
- **120fps 高帧率串流** — 支持 HEVC/H.265 编码，低延迟画面传输
- **手游风格触控按键** — 可自定义位置的虚拟摇杆 + 按键 overlay
- **陀螺仪瞄准** — 转动平板控制视角，体感瞄准
- **触摸瞄准** — 右半屏滑动控制视角，与陀螺仪独立
- **USB 串流** — 数据线直连，延迟比 WiFi 更低
- **配置编辑器** — 自定义按键位置、大小、透明度

### Pro 功能（赞助者）
- **云配置同步** — 通过 GitHub Gist 在多设备间同步配置
- **配置模板库** — 一键应用推荐配置（三角洲行动默认布局等）
- **配置分享码** — 生成/导入配置分享码，方便分享给朋友

## 下载

- GitHub Releases（即将上线）
- 面包多（购买 Pro 赞助者）

## 与原版 Moonlight 的区别

| 特性 | Moonlight | DeltaStream |
|------|-----------|-------------|
| 触控按键 | 无 | 手游风格 overlay |
| 陀螺仪瞄准 | 无 | 支持（含 ADS 灵敏度、后坐力补偿）|
| 配置管理 | 无 | 云同步 + 模板库 + 分享码 |
| 默认布局 | 无 | 三角洲行动预设按键 |
| 引导页 | 无 | 首次使用引导 |
| 隐私政策 | 无 | 内置隐私政策页 |

## 构建

### 环境要求
- Android Studio + Android SDK (compileSdk 34)
- Android NDK 27.0.12077973
- JDK 21

### 步骤
```bash
git clone https://github.com/guo6x/deltastream-android.git
cd deltastream-android
git submodule update --init --recursive

# 创建 local.properties 指定 SDK 路径
echo "sdk.dir=D:\\Android_SDK" > local.properties

# 构建 performance APK（仅 arm64-v8a，适合实际设备）
./gradlew assembleNonRootPerformance
```

构建产物：`app/build/outputs/apk/nonRoot/performance/app-nonRoot-performance.apk`

### Build Types
- `debug` — 调试版（带 debuggable）
- `performance` — 性能版（arm64-v8a only，R8 优化，适合实际使用）
- `release` — 发布版（需签名）

## 技术细节

### 串流优化
- HEVC 强制编码 + CQP 恒定质量模式（延迟最低）
- 前向纠错 10%（局域网/USB 足够）
- 低延迟帧节奏
- global_input=1（支持反作弊游戏输入）

### 触控按键
- 32 个按键的默认布局（开火/开镜/跳/蹲/趴/换弹/刀/拾取等）
- 按键支持鼠标按键、键盘按键、切换模式、滚轮
- 摇杆支持键盘输出（WASD）和模拟输出

### 陀螺仪瞄准
- X 轴 yaw + Y 轴 pitch（横屏）
- ADS 开镜时自动降低灵敏度
- 后坐力补偿
- 死区/平滑/速度限制
- 累积 delta 计算（确保小动作被检测）

### Pro 激活机制
5 层不留痕设计：
1. FLAG_SECURE 防截图
2. 密码模式输入框
3. 激活后立即清空
4. 仅存布尔值，不存凭证
5. 状态页不显示凭证

## 配置模板库

模板配置存放在 [guo6x/deltastream-templates](https://github.com/guo6x/deltastream-templates) 仓库，APP 通过 raw.githubusercontent.com 拉取，无需认证。

## 开源声明

本项目基于 [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) 修改，遵循 GPLv3 协议开源。

原版 Moonlight 由 Cameron Gutman 等人开发，感谢他们的开源贡献。

## License

GPL-3.0 — 见 [LICENSE.txt](LICENSE.txt)
