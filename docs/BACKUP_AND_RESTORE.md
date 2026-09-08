# 图片备份与恢复

入口位于全局设置的通用设置中：导出备份、导入/恢复数据。旧版目录导入已并入第二个入口。

备份包含图片、线稿原图、图片顺序、已保存的位置/缩放/旋转/透明度及其他图片参数。不包含通用设置和未保存的手势编辑。通过系统文件选择器创建 `.fpbackup.zip`，导出成功后再卸载；卸载前未导出的私有数据无法凭重新安装找回。

备份使用版本 1 的 ZIP 格式及逐文件 SHA-256 校验。恢复先解压到私有临时目录，验证 JSON、图片引用、图片格式及参数，再显示图片数量并等待用户确认。旧版目录同样先暂存、验证及确认。安装通过同文件系统目录重命名完成，保留旧目录以应对失败回滚和两次重命名之间的进程终止。临时文件的后台清理使用本次操作的独立目录，避免与下一次恢复争用回滚目录。

每份备份最多 10,000 个数据/图片文件，解压总量最多 2 GB。导入导出会占用临时存储空间。备份不加密，选择云盘时由相应文件提供方处理存储与传输。

## 验证记录（2026-09-07）

- APK 编译通过；Android Lint 无错误，项目仍有警告。
- `BackupArchiveTest`：11 项通过，涵盖字节级往返、空图库、损坏/缺失/未来版本/路径穿越/截断 ZIP、大小限制、失败回滚、启动恢复及整体替换。
- `BackupIntegrationTest`：Android API 36.1 只读模拟器上 11 项通过，涵盖实际文件流与位图校验、参数及顺序校验、旧版目录及父目录、恢复预览、确认恢复、页面重建与返回、系统创建文件请求及取消回调。
- 保存窗口取消测试使用匹配 `ACTION_CREATE_DOCUMENT`、`CATEGORY_OPENABLE` 和 `application/zip` 的测试拦截器；未手动遍历各厂商文件管理器或云盘。
- 已检查导出页面模拟器截图，未在用户的实体手机上验证。

## 运行测试

本机 Gradle 9.0-milestone-1 的 `testDebugUnitTest` 启动器报告测试类 `ClassNotFoundException`，编译后的同一测试类直接运行正常。备份核心测试可用独立入口运行，避免依赖该启动器；未调整项目全局构建工具版本。

```powershell
.\scripts\Test-BackupArchive.ps1 -JavaHome '<本机 JDK 目录>'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s <测试设备> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <测试设备> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <测试设备> shell am instrument -w -r -e class tool.xfy9326.floatpicture.Activities.BackupIntegrationTest tool.g1nsy.floatpicture.test/androidx.test.runner.AndroidJUnitRunner
```

独立核心测试脚本使用 Gradle 已缓存的 JUnit 4.13.2 和 Hamcrest 1.3，不额外下载依赖。
