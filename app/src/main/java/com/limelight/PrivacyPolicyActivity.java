package com.limelight;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 隐私政策页面
 * 发布到应用商店/分发平台时使用
 */
public class PrivacyPolicyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));
        root.setPadding(48, 48, 48, 48);

        // 标题
        TextView title = new TextView(this);
        title.setText("DeltaStream 隐私政策");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        // 更新日期
        TextView date = new TextView(this);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        date.setText("更新日期：" + today);
        date.setTextSize(12);
        date.setTextColor(Color.parseColor("#888888"));
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, 0, 0, 24);
        root.addView(date);

        // 内容（可滚动）
        ScrollView scroll = new ScrollView(this);
        TextView content = new TextView(this);
        content.setTextSize(14);
        content.setTextColor(Color.parseColor("#e0e0e0"));
        content.setLineSpacing(6, 1.2f);
        content.setMovementMethod(ScrollingMovementMethod.getInstance());

        content.setText(buildPolicyText());
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        root.addView(scroll, scrollParams);

        setContentView(root);
    }

    private String buildPolicyText() {
        StringBuilder sb = new StringBuilder();

        sb.append("欢迎使用 DeltaStream。本应用基于开源项目 Moonlight（GPLv3 协议）二次开发，")
          .append("致力于为您提供 PC 到平板的低延迟串流游戏体验。\n\n");

        sb.append("━━━ 数据收集 ━━━\n\n");
        sb.append("DeltaStream 不收集、不上传、不跟踪任何个人身份数据。\n");
        sb.append("应用不包含任何分析 SDK、广告 SDK 或用户追踪代码。\n\n");

        sb.append("━━━ 本地存储 ━━━\n\n");
        sb.append("以下数据仅存储在您的设备本地：\n");
        sb.append("• 已配对的电脑列表（IP 地址、主机名）\n");
        sb.append("• 串流设置（分辨率、帧率等）\n");
        sb.append("• 触控按键布局配置\n");
        sb.append("• 陀螺仪瞄准设置\n");
        sb.append("• Pro 激活状态（仅布尔值，不存凭证）\n\n");

        sb.append("━━━ Pro 激活 ━━━\n\n");
        sb.append("Pro 激活采用「不留痕」设计：\n");
        sb.append("• 激活页面禁止截图（FLAG_SECURE）\n");
        sb.append("• 订单号输入框为密码模式，不可见\n");
        sb.append("• 激活成功后立即清空输入内容\n");
        sb.append("• 仅保存「已激活」布尔值，不存储任何订单号或凭证\n");
        sb.append("• 激活状态页不显示任何凭证信息\n\n");

        sb.append("━━━ 云同步与配置分享（Pro 功能）━━━\n\n");
        sb.append("云同步功能使用 GitHub Gist API：\n");
        sb.append("• 您需要自行提供 GitHub Personal Access Token\n");
        sb.append("• Token 仅存储在设备本地，不上传到任何第三方服务器\n");
        sb.append("• 配置数据直接上传到您自己的 GitHub Gist，我们无法访问\n");
        sb.append("• 配置分享码是在本地生成的 Base64 编码，不经过任何服务器\n\n");

        sb.append("━━━ 配置模板库 ━━━\n\n");
        sb.append("模板库从公开的 GitHub 仓库拉取配置模板：\n")
          .append("https://github.com/guo6x/deltastream-templates\n")
          .append("• 仅请求模板 JSON 文件，不发送任何用户数据\n")
          .append("• 应用模板前会自动备份当前配置\n\n");

        sb.append("━━━ 网络权限说明 ━━━\n\n");
        sb.append("• INTERNET / ACCESS_NETWORK_STATE：串流连接、mDNS 发现、模板下载\n");
        sb.append("• ACCESS_WIFI_STATE：获取 WiFi 状态以优化串流\n");
        sb.append("• CHANGE_WIFI_MULTICAST_STATE：mDNS PC 自动发现\n");
        sb.append("• VIBRATE：触控按键震动反馈\n");
        sb.append("• WAKE_LOCK：串流时保持设备唤醒\n");
        sb.append("• HIGH_SAMPLING_RATE_SENSORS：陀螺仪高刷新率瞄准\n\n");

        sb.append("━━━ 第三方组件 ━━━\n\n");
        sb.append("本应用使用以下开源组件：\n");
        sb.append("• Moonlight（GPLv3）— 串流核心\n");
        sb.append("• BouncyCastle — 加密库\n");
        sb.append("• OkHttp — 网络请求\n");
        sb.append("• jMDNS — 局域网设备发现\n\n");

        sb.append("━━━ 开源声明 ━━━\n\n");
        sb.append("本应用基于 Moonlight Android 开源项目（GPLv3 协议）修改。\n");
        sb.append("源代码可在 GitHub 获取，遵循 GPLv3 协议开放。\n\n");

        sb.append("━━━ 联系方式 ━━━\n\n");
        sb.append("如有隐私相关问题，请通过应用内的「Pro 赞助者」→「捐赠」页面联系开发者。\n");

        return sb.toString();
    }
}
