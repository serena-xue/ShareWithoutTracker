# ShareWithoutTracker

一个基于剪贴板的“净化链接后再分享”的 Android 小工具：从通知栏一键读取剪贴板，去除常见平台分享链接中的追踪参数/跳转层，并发送到 Telegram。

> APK：请到本仓库的 GitHub **Releases** 页面下载。

## 功能

- 常驻通知入口
  - 点击通知空白区域：**直接分享**（读取剪贴板 → 清洗 → 发送）
  - 点击按钮：**评论并分享**（先输入评论，再读取剪贴板并发送）
- Telegram 发送格式
  - 基础内容：`[来源] 标题`（HTML 链接）
  - 如填写评论，会追加一行：

    ```
    [眼镜鹅评论] <你的评论>
    ```
- 支持的平台（按当前实现）
  - 小红书
  - Bilibili
  - 知乎（需要 WebView 获取标题；处理时会显示“处理中……”提示）

## 使用方法

1. 安装并打开 App，配置 Telegram：
   - `tg_bot_token`：你的 Bot Token
   - `tg_chat_id`：目标 Chat ID
2. 把你要分享的链接复制到系统剪贴板。
3. 从通知栏触发：
   - **直接分享**：点通知的空白区域
   - **评论并分享**：点“评论并分享”按钮，输入评论后提交

## 权限说明

- `INTERNET`：调用 Telegram Bot API、解析链接
- `POST_NOTIFICATIONS`：显示常驻通知（Android 13+ 需要授权）

## 隐私说明（简要）

- App 会读取你触发分享时的剪贴板内容，并仅用于链接识别/清洗与发送。
- 发送目标为你配置的 Telegram Bot 与 Chat。

## 本地构建（可选）

- Android Studio 打开项目后构建，或使用命令：
  - Debug：`./gradlew :app:assembleDebug`

## 免责声明

本项目按现状提供（AS IS）。请自行评估并妥善保管 Telegram Bot Token，避免泄露。
