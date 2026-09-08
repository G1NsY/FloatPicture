# 导入后首次显示的位置修复

验证环境：Android 14 / API 34，10inch_pad 模拟器，1920 × 1080。

## 原因与修复

备份中的隐藏图片在初始化时已完成旋转和缩放，但尚未创建窗口。
首次显示调用 `preserveCurrentWindowSize` 时没有旧窗口尺寸可复用，因而保留了
`WRAP_CONTENT`。边界约束使用尚未测量的视图尺寸，且 Android 随后的测量会限制
大图高度。隐藏后再显示时已有明确尺寸，两次显示结果因此不同。

现在无旧窗口尺寸时使用图片已计算好的渲染宽高，在第一次边界检查和窗口添加前
确定尺寸。已有窗口仍保留当前尺寸；显示操作不修改保存的图片坐标。

## 备份验证

输入：用户提供的 `FloatPicture-20260908-195417.fpbackup.zip`，包含 7 张隐藏图片。
原始备份未放入代码库。测试解压并安装到独立缓存图库，结束后还原路径与设置。

- 修复前：4 项测试中 3 项失败，见 `before.txt`。隐藏图片首次显示与重显不一致；
  原本可见的图片启动路径正常。
- 修复后：4 项全部通过，见 `after-backup.txt`。
- 覆盖单图模式、多图模式、允许越界、原本可见的图片。
- 对每张图片检查首次窗口尺寸、边界约束、第一次和第二次实际屏幕位置一致，
  并确认显示操作不改写保存的 X/Y。
- 具体坐标对比见 `position-comparison.txt`，包含修复前后的运行记录。

## 重跑

测试类：`tool.xfy9326.floatpicture.Methods.ImportedPicturePositionTest`。
默认生成独立的大尺寸旋转图片备份，不需要用户文件。
要验证指定备份，可向 AndroidJUnitRunner 传入 `positionBackup` 参数，值为设备上的
备份绝对路径；模拟器需已授予悬浮窗权限。

编译与检查：`:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过。
独立生成的图片位置测试 4 项及悬浮控制层级、左右展开测试 9 项全部通过，
见 `regression.txt`（13 项）。

安装包：`FloatPicture-2.0.7-import-position-fix-debug.apk`。
SHA-256：`84D946C6D8C582A76D0D4DAFE2A3EB55F48CB7AEAF64B9D31A7FDF6BA0D367C3`。
