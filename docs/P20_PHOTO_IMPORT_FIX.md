# 华为 P20 照片导入兼容修复

日期：2026-09-06。版本仍为 2.0.6-beta / versionCode 7。

## 问题证据

用户反馈照片无法添加、截图可以添加。提供的异常截图明确显示
`PictureSettingsFragment.exit()` 对空 `Bitmap` 调用了 `recycle()`。
这证实退出清理存在空指针问题，但不能单凭该堆栈确定原始照片解码失败的原因。
本次没有获得失败的原始照片，也未连接华为 P20 实机。

## 修改

- 图片摘要改为流式读取，关闭流，不再依赖 `available()` 或内存映射，支持图库返回的管道和文件片段。
- 解码前复制到应用缓存，读取尺寸后按采样率导入，长边最多 4096 像素，像素总数最多 800 万，并按进程堆预算进一步限制。内存不足时降低采样分辨率重试。较大的照片会缩小应用内导入副本，原始照片不变。
- 导入时应用 EXIF 旋转、镜像信息；结束后清理临时源文件。
- 新图片仅在缓存写入成功后返回图片 ID；导入失败时显示提示并关闭编辑页。
- 退出新增图片页时检查图片是否为空、是否已回收，以及悬浮视图是否已附着。
- 添加和替换共用选图逻辑；兼容 URI 位于 `ClipData` 的结果。9 月 6 日测试包优先系统文档选择器；9 月 7 日按用户反馈恢复原来的 `ACTION_GET_CONTENT` 图片选择入口，仅在入口不可用时回退到系统文档选择器。两个入口均限制 `image/*`，实际界面由手机系统和已安装应用决定。

流式读取行为参考 [Android ContentResolver 官方文档](https://developer.android.com/reference/android/content/ContentResolver#openInputStream(android.net.Uri))。

## 验证

- `assembleDebug assembleDebugAndroidTest lintRelease --offline --console=plain` 构建成功；新增测试后重新构建测试 APK 成功。
- Lint：0 错误，50 警告。
- Android API 36.1 临时只读模拟器：`PictureImportTest` 10 项全部通过。
- 覆盖管道与文件片段的摘要和解码、截图尺寸保留、大尺寸 JPEG 采样和 EXIF 方向、导入失败及缓存清理、选图结果兼容与选择器回退。
- 页面测试验证照片进入编辑页，并人为重建截图中的空图片清理状态，退出成功；无效照片导入后编辑页关闭且不新增条目。
- 首轮测试曾因模拟器图形环境问题和测试提供器缺少独立进程依赖而中断。切换软件图形模式，并让测试提供器使用系统 EXIF API 后完成上述 10 项验证。生产代码仍使用既有 AndroidX EXIF 库。
- 最终测试日志：`captures/p20-import-tests.txt`。临时模拟器已关闭。
- 未在旧 Android、EMUI 或用户原始照片上完成实机复测；若仍失败，需要该照片原文件进一步定位格式或设备解码差异。

## 测试包

文件：`FloatPicture-2.0.6-beta-p20-photo-fix-debug.apk`，6,904,958 字节。

SHA-256：`A150AE1D247F60E542AA120123B97FBD3D9FB9756EF3AE41B88ABF0F1562889A`。

本次生成调试签名 APK，未生成、签署或发布正式 AAB。
