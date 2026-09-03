# 2.0.6 Beta 本地构建检查

## 支付宝收款码更新（同日后续）

- 按用户要求替换爱发电入口，原样内置用户提供的支付宝收款码；新增本地大图查看与返回，更新中英文说明、README 和应用内隐私政策。
- 源码中已移除爱发电跳转、复制链接及其对应文案。保留原构建开关 `externalSupport=false` 用于关闭收款码卡片。
- 调试 APK：项目根目录 `FloatPicture-2.0.6-beta-alipay-debug.apk`，versionName `2.0.6-beta`，versionCode `7`。最终文件 10,906,898 字节；SHA-256：`7D946CE8C4225DAF5B98FD7D63A2F28BE6FF14F9D651C57768FA66BD9E2D5655`。
- 最终 `assembleDebug lintRelease` 通过，静态检查 0 个错误、50 个警告。支持页 3 项专项测试通过；最后的状态栏颜色调整后再次安装 APK 并复核中英文横竖屏布局。
- 收款码图片与用户原文件逐字节一致，APK 中打包的图片也一致。OpenCV 能解码原图、中文竖屏和英文横屏的大图截图，解码内容相同。此验证不涉及支付宝账号认证、收款或支付交易。
- 检查记录和截图在 `captures/alipay-support/`。本次没有重建或签署 AAB，没有改动线上隐私政策或上传应用。

以下是替换收款码之前的同日构建检查记录，旧 AAB 与 18 项测试结论对应当时的源码。

检查日期：2026-09-03。结论：当前源码可构建，未发现阻止打包的错误。最终签名 AAB 由用户在 Android Studio 中生成。

## 版本

- 包名：`tool.g1nsy.floatpicture`
- versionName：`2.0.6-beta`
- versionCode：`7`（由本地原值 `6` 递增；未访问 Play Console 核对是否有更高的已用编号）
- minSdk：19；targetSdk / compileSdk：36
- 本次源码修改仅为版本配置及 README 标题；保留已有功能修改。

## 验证结果

- `assembleDebug assembleDebugAndroidTest lintRelease bundleRelease --console=plain`：BUILD SUCCESSFUL。
- 发布构建的压缩优化及资源处理通过，合并 Manifest 中版本为 `2.0.6-beta / 7`。
- Android Lint：0 个错误、50 个警告。完整报告：`app/build/reports/lint-results-release.html`。
- 在临时只读 Android API 36.1 模拟器中运行现有测试：18 项通过，无失败或跳过。
  - AboutLayoutTest：2 项，中英文关于页横竖屏和页面重建。
  - NumericEditingTest：6 项，透明度和缩放的输入校验、精度、确认和取消。
  - RotationEditingTest：6 项，旋转编辑、单图/多图控制、隐藏状态恢复及手势刷新。
  - SupportActivityTest：4 项，页面重建、外链确认、复制及中英文横竖屏。
- 原始测试输出：`captures/release-2.0.6/instrumentation-results.txt`。测试使用临时安装与合成图片，模拟器已关闭。

## 未阻止构建的提示与检查范围

- 警告主要涉及未使用资源、矢量图兼容性、触摸控件无障碍、硬编码符号和旧 API 的界面属性。
- CrashHandler 未调用原系统异常处理器，属于诊断与维护方面的改进项；本次未修改其行为。
- 构建工具还提示使用了将来 Gradle 10 不兼容的旧功能，本次构建通过。
- 本次没有验证实体设备、旧 Android、低内存场景，也未完成新增线稿移除、悬浮透明度控制器等功能的专项回归；测试通过不代表所有使用场景都无缺陷。
- 当前命令行构建未配置发布签名，不可将该未签名 AAB 直接上传。用户应从当前源码使用此前的上传密钥生成签名 AAB。
- 未改动、签名或覆盖 `app/release/app-release.aab`；该目录原有文件仍是旧包。未上传应用、提交审核或核验商店后台。
