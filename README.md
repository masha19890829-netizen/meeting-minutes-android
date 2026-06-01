# 会议库 Android

会议库是一个私人试用版 Android APK，用来把会议录音转写成文字，自动生成会议纪要，并归档到手机本地会议库。

## 已实现

- 录音入口，录音结束后自动转写和总结。
- 录音开始后以前台服务常驻，锁屏、切到其他应用时继续录音。
- 停止录音前二次确认，避免误触中断会议记录。
- 内置 Vosk 离线中文语音识别模型，不需要 OpenAI Key，不需要充值。
- 本地规则生成会议纪要，输出摘要、决策、风险、问题、待办和 Markdown。
- Room 本地库，保存会议、转写、纪要、待办、导出记录、洞察和日历同步状态。
- APP 内日历查看，支持授权后写入系统日历。
- Markdown 与 PDF 导出分享。
- WorkManager 自动生成每日会议洞察。
- 全流程默认本地处理，不上传录音，不内置任何云服务密钥。
- GitHub Actions 可自动构建 debug APK。

## 试用步骤

1. 安装 debug APK。
2. 回到“录音”，输入会议标题，点击录音按钮。
3. 停止录音后，APP 会自动在手机本地转写、总结、删除原始音频并归档。
5. 在“库”或“日历”里查看会议，使用 PDF/Markdown 图标导出分享。

第一次识别会把离线模型解压到手机本地，可能会稍慢。识别效果取决于手机性能、环境噪音和说话清晰度。

## 构建

本项目使用：

- Android Gradle Plugin 9.0.1
- Gradle 9.1.0
- JDK 17
- Compose BOM 2026.05.00
- compileSdk 36

本地构建：

```powershell
gradle :app:assembleDebug
```

GitHub Actions 构建完成后，APK 位于 artifact `meeting-minutes-debug-apk`。
