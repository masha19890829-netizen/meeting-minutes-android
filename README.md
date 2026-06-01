# 会议库 Android

会议库是一个私人试用版 Android APK，用来把会议录音转写成文字，自动生成会议纪要，并归档到手机本地会议库。

## 已实现

- 录音入口，录音结束后自动转写和总结。
- 录音页内置周会、评审、复盘、1 对 1 模板，会前目标/议程会进入会议上下文，帮助纪要更贴近工作目标。
- 录音开始后以前台服务常驻，锁屏、切到其他应用时继续录音。
- 停止录音前二次确认，避免误触中断会议记录。
- 内置 Vosk 离线中文语音识别模型，不需要 OpenAI Key，不需要充值。
- 本地说话人分段标注，把转写保存为“角色A：...”和“角色B：...”这类记录，便于回看会议对话。
- 本地规则生成会议纪要，输出摘要、决策、风险、问题、待办和 Markdown。
- Kimi 后台整理：可在设置页填写 Kimi API Key，详情页点击“AI生成文档”后会在 APP 内调用 Kimi API 生成结构化会议文档，不再跳转网页。
- 未启用 Kimi 或 Kimi 调用失败时，APP 会自动回退到本地智能整理，会议仍然可以保存、查看和导出。
- Room 本地库，保存会议、转写、纪要、待办、导出记录、洞察和日历同步状态。
- APP 内日历查看，支持授权后写入系统日历。
- Markdown 与 PDF 导出分享。
- 会议详情支持一键复制纪要和未完成待办，便于直接粘贴到聊天、邮件或工作群。
- 会议详情底部提供明确的“删除会议”按钮，关联转写、纪要、待办和导出缓存会一起清理。
- 工作台聚合所有会议待办，可直接勾选完成状态，并保留最近会议洞察。
- GitHub Releases 更新检查：启动 APP 或在设置页手动检查时，发现更高 versionCode 的 APK 会提示下载更新。
- WorkManager 自动生成每日会议洞察。
- 全流程默认本地处理，不上传录音，不内置任何云服务密钥。只有打开“Kimi 后台整理”后，才会把会议文字发送到你填写的 Kimi API。
- GitHub Actions 可自动构建 debug APK。

## 试用步骤

1. 安装 debug APK。
2. 回到“录音”，可先点会议模板，确认标题和会前目标，然后点击录音按钮。
3. 停止录音后，APP 会自动在手机本地转写、总结、删除原始音频并归档。
4. 如果你有 Kimi API Key，可在“设置 > Kimi 后台整理”打开开关并填写 Key；默认接口为 `https://api.moonshot.ai/v1`，默认模型为 `kimi-k2.6`。
5. 回到会议详情点击“AI生成文档”，APP 会在后台完成 Kimi 调用并把结果保存为会议文档；不填 Key 也能继续使用本地规则整理。
6. 在“库”或“日历”里查看会议，使用复制、PDF/Markdown 图标导出分享；在“工作台”里处理跨会议待办。

第一次识别会把离线模型解压到手机本地，可能会稍慢。识别效果取决于手机性能、环境噪音和说话清晰度。

说话人标注使用手机本地音频特征做自动分段和聚类，适合私人会议记录里的角色区分；它不是严格声纹认证，同一人距离手机太远或多人同时说话时可能需要人工复核。

## 更新发布

APP 默认检查这个 GitHub Releases 地址：

```text
https://api.github.com/repos/masha19890829-netizen/meeting-minutes-android/releases/latest
```

发布新版时，把 `app/build.gradle.kts` 里的 `versionCode` 调高，推送 tag，例如 `v5`。GitHub Actions 会构建 APK，并在 Release 里上传 `meeting-minutes-debug.apk`。Release 说明里会自动写入：

```text
versionCode: 5
versionName: 1.4
```

手机里的旧版 APP 下次启动或手动检查更新时，会看到新版本提示并打开下载页。

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
