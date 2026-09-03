# Google Play 发布准备清单

## 2026-09-03 支付宝收款码替换

当前 2.0.6-beta（versionCode 7）已按用户要求将支持页改为本地支付宝收款码，图片原样内置并支持查看大图。应用内爱发电跳转、复制链接及相应中英文文案已移除，README 与应用内隐私说明同步更新。`externalSupport=false` 继续用于关闭整张收款码卡片。下文的爱发电及旧安装包描述为历史检查记录，不代表当前实现。二维码展示本身不是支付政策豁免或审核通过证明；未修改线上隐私政策或提交审核。

核对日期：2026-08-31。对象：FloatPicture 2 / 悬浮图片2，包名 `tool.g1nsy.floatpicture`。

本清单结合本地代码、构建产物与 Google 官方规则整理。下方保留发布前检查记录；账号验证、测试人数、商店后台进度及已使用的版本号，需要由账号所有者核对。

## 最新发布进度（2026-08-31）

- 已检查用户通过 Android Studio 生成的签名 AAB：包名 `tool.g1nsy.floatpicture`，versionCode 6，版本 2.0.5-beta，最低 API 19，目标 API 36；签名验证通过。
- 用户已反馈在 Play Console 发布内部测试版本；未代用户操作后台，也未独立核实后台状态。内部测试不代表正式公开上架，也不能代替新个人账号所需的封闭测试。
- 应用内双语隐私政策已加入联系邮箱 `nimedea@gmail.com`。独立隐私政策网页仍需单独同步；更新应用仓库不会自动更新该网页。
- 本次源码同步不更改包名、版本号、最低 SDK 或 R8 优化配置，也不会自动更新 Google Play 中的安装包。后续发布新构建前应先核对已使用的版本代码。
- 签名密钥、安装包、本地 SDK 配置及测试截图不纳入 Git。本次新增的商店图标、导出脚本和自动化测试源码纳入版本管理。

## 当前结论

应用已完成签名包检查，用户已反馈发布内部测试。正式公开上架仍需完成赞助链接政策确认、已有公开隐私政策的内容同步、其余商店素材与后台声明；新个人账号还要完成封闭测试与正式发布资格申请。以下表格和复核记录反映发布前各阶段的检查结果，涉及当前后台状态时以上述最新进度及 Play Console 为准。

| 项目 | 本地检查结果 | 下一步 |
| --- | --- | --- |
| Android 目标版本 | compileSdk / targetSdk 均为 36 | 已满足 2026-08-31 起的新应用目标版本要求 |
| 包名和版本 | `tool.g1nsy.floatpicture`；versionCode 6；2.0.5-beta | 包名首次上架后不可随意更换；确认 6 未被后台使用，确定正式版本名称 |
| AAB 构建 | release bundle 可生成；当前命令行产物未签名 | 用户可通过 Android Studio 的 Generate Signed Bundle / APK 向导生成签名 AAB，并在 Play Console 上传 |
| 发布静态检查 | 修复后 0 个错误、48 个警告；关于页布局缺失和默认小数格式警告已消除 | 不是完整审核通过证明，还需实体设备测试及预发布报告 |
| 原生库 | 生成的 AAB 中未发现 `.so` | 未发现需要处理的第三方原生库对齐问题，仍建议进行 16 KB 设备测试 |
| 图片与网络 | 系统选择器导入，本地处理；无 INTERNET 权限、广告或统计 SDK | 最终包再次检查合并权限与依赖，再填数据安全表 |
| 支持页面 | 侧栏“支持开发”；中英文；爱发电链接、复制、确认提示 | 普通构建默认有链接，可在构建时关闭；付款相关合规仍待确认 |
| 隐私政策 | 独立仓库 `G1NsY/floatpicture-privacy` 已有双语网页；GitHub Pages 返回 HTTP 200 | 线上仍为 2026-08-11 版本，需补充外部网站及复制链接的说明，与应用内政策同步 |
| 商店图标 | 已采用用户选定的 `logo-google.png`，导出 `docs/play-store/icon-512.png`：512×512、32 位 RGBA PNG、46,308 字节 | 可上传此文件；逐像素验证与用户原图一致，外部设计原稿保留 |
| 宣传素材 | 未在项目中找到完整的 Play 素材集 | 准备 1024×500 宣传图、应用实际截图及中英文介绍 |
| 后台与测试 | 未访问 Play Console，状态未知 | 检查身份/设备验证、封闭测试、正式发布申请及应用内容表单 |

目标版本依据：[目标 API 要求](https://support.google.com/googleplay/android-developer/answer/11926878)。纯 Java/Kotlin 且依赖中无原生代码的应用通常已支持 16 KB，但 Google 仍建议实测：[16 KB 指南](https://developer.android.com/guide/practices/page-sizes)。

## 1. 签名与发布包

- 使用正式上传密钥签署 AAB，并配置 Play App Signing。不要用 Android debug.keystore 发布。Android Studio 的 Build → Generate Signed Bundle / APK → Android App Bundle 可以完成签名；生成 AAB 后仍需在 Play Console 上传和提交。
- 没有扫描其他个人目录寻找私钥；这里只确认仓库没有 release 签名配置，也没有生成新的密钥。
- 私钥、密码和 keystore.properties 不进入仓库；备份由你控制，不在聊天中粘贴密码。
- 先确认是否已有上传密钥、是否已经在 Play Console 创建应用或上传过版本。不要无理由更换密钥、包名或版本号。
- 当前包名、versionCode 和版本名称未被本次功能修改改变。

依据：[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)。

## 2. 爱发电链接与 Google Play 版本

新增页面不实现内购、不承诺付费权益、不校验赞助状态，也不传递图片、图片设置或用户标识。它只在用户确认后请求外部应用打开固定公开网址。

Google 的直接打赏例外包含“100% 给创作者”和“没有数字内容或服务回报”的条件；爱发电官网说明有 6% 的平台及支付费用。因此，不能仅凭“自愿、没有解锁功能”就保证适用例外，也不能保证放到外部浏览器即合规。区域性外部付款计划另有条件。

本次添加了构建参数 `externalSupport=false`。它会在支持页隐藏整张爱发电卡片，并使打开/复制的处理逻辑失效。正常构建默认保留爱发电功能，未擅自决定最终上架版本。

**这个开关只管理新增支持页，不代表对全应用链接路径的完整合规审核。** 原有“关于”页面仍链接 GitHub，而 README 已有赞助链接；最终提交前应把这些直接/间接路径一起提供给 Play 支持确认，或另行调整发行内容。不要把 GitHub 中转当作规避支付规则的办法。

依据：[Google 支付政策说明](https://support.google.com/googleplay/android-developer/answer/10281818)、[爱发电官网费用说明](https://afdian.com/)。

## 3. 隐私政策与应用内容表

- 已确认现有公开页面：[FloatPicture 隐私政策](https://g1nsy.github.io/floatpicture-privacy/)，2026-08-31 实测 HTTP 200。网页源码位于独立仓库 [G1NsY/floatpicture-privacy](https://github.com/G1NsY/floatpicture-privacy)，不是应用仓库。
- Play Console 应填写上述 GitHub Pages 网页地址。网页已有开发者、包名、中英文数据处理说明和联系邮箱；内容仍为 2026-08-11 版本，需要补上本次新增的外部赞助页面与剪贴板说明，并将应用名称同步为“悬浮图片2 / FloatPicture 2”。未修改或推送该独立仓库。
- 公开政策应与应用内 `app/src/main/assets/PRIVACY_POLICY.txt` 一致，包含开发者/应用名称、联系方式、数据访问用途、保留与删除方式。
- 数据安全：按当前离线处理实现，初步可按“不收集、不共享用户数据”准备；提交时必须再按最终包、全部 SDK 和外部交互核对。即使不收集数据，也需要填写表格及隐私政策。
- 广告：当前未发现广告 SDK 或广告展示，可按最终实际功能填写。
- 应用访问：当前无登录功能，准备操作指引即可，不需要编造测试账号。
- 完成内容分级、目标年龄/受众、金融功能等后台实际要求的声明。年龄段根据真实定位选择，不能为省事随意勾选儿童。
- 准备支持邮箱、应用分类、发行国家/地区和价格设置。尚未替你填写后台表单。

依据：[用户数据政策](https://support.google.com/googleplay/android-developer/answer/10144311)、[数据安全表](https://support.google.com/googleplay/android-developer/answer/10787469)、[审核准备](https://support.google.com/googleplay/android-developer/answer/9859455)。

## 4. 悬浮窗与前台服务

Manifest 中的权限为 SYSTEM_ALERT_WINDOW、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS。服务类型为 specialUse，已填写 subtype 用途描述；这不是后台审核通过证明。

需要在 Play Console 申报前台服务用途，说明为什么延迟或中断会影响用户，并提供演示视频。建议录制以下完整路径：

1. 打开应用，展示隐私说明和悬浮窗授权。
2. 选择一张有权使用的参考图，开启悬浮显示。
3. 切换到相机或其他应用，展示参考图、控制器及运行通知。
4. 展示调整、隐藏和关闭，证明任务由用户发起并可停止。

视频链接应能由审核人员直接打开。不要使用含个人信息或无权公开的照片。还需实测拒绝权限、后台切换、旋转、大字体、低内存以及退出后服务停止等情形。

依据：[前台服务声明要求](https://support.google.com/googleplay/android-developer/answer/13392821)。

## 5. 商店文案与素材

- 应用名称已确定：中文“悬浮图片2”，英文“FloatPicture 2”。已同步应用显示名称、支持页面、应用内隐私说明和 README；包名、版本号、数据目录及旧版导入路径不变，原作者署名保留。Play Console 与外部隐私网页仍需分别同步。
- 应用名称：最多 30 个字符；简短介绍最多 80 个字符；完整介绍最多 4000 个字符。建议准备简体中文与英文。
- 商店图标：512×512、32 位 PNG、最大 1024 KB。本次使用用户选定的 `logo-google.png`，原样复制为 `app/src/main/ic_launcher-playstore.png`，再导出 `docs/play-store/icon-512.png`，46,308 字节，图案和颜色逐像素不变。可使用 `scripts/Export-PlayStoreIcon.ps1` 重复导出。本次仅替换商店图标，未重绘或替换 Android 桌面自适应图标。
- 宣传图：1024×500，JPEG 或无透明通道的 24 位 PNG。
- 发布要求至少 2 张截图；推荐准备 4–6 张真实操作截图，展示导入、彩色线稿、悬浮参考、手势及控制器。不要把本次支持页测试截图当作完整商店素材。
- 根据实际支持的手机和平板设备，补充相应截图；检查尺寸、比例、无调试信息及隐私泄露。
- README 已保留原作者与 GPL 授权说明；商店介绍应如实说明功能，不宣称自动姿态识别等应用并未提供的能力。

依据：[创建应用与文案限制](https://support.google.com/googleplay/android-developer/answer/9859152)、[素材要求](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)、[图标规范](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)。

## 6. 账号、测试及提交顺序

如果这是 2023-11-13 后创建的新个人开发者账号，需要至少 12 名测试者连续加入封闭测试 14 天，再申请正式发布权限。内部测试不能代替这一要求；达到天数也不等于自动获批，应收集真实测试反馈。

可以通过 B 站等社交媒体招募真实目标用户；Google 官方明确允许社交媒体招募，视频内容仍须遵守发布平台自身规则。建议先招募 15–20 人作为人数缓冲（并非 Google 额外要求），优先寻找能正常使用 Google Play 的 Android 手机或平板用户。

测试者使用自己的 Google 账号，通过 Play Console 配置的邮箱名单或 Google 群组取得测试资格，打开加入测试链接，再从 Google Play 安装并实际体验。仅加入聊天群、提供邮箱或侧载 APK，不等于已加入 Play 封闭测试。提醒连续保持测试资格至少 14 天，并提供问题反馈；不要承诺满足人数和天数就一定通过审核，也不要要求五星好评。报名邮箱通过私信或仅自己可见的报名表收集，不让参与者在公开评论区披露；不收集密码或验证码。

新个人账号还需按 Console 指引，用 Android 10 或更高版本的未 root 实体手机完成设备验证。模拟器不能替代这项账号验证。

推荐顺序：确认账号剩余任务 → 准备签名、隐私页面与素材 → 内部测试与预发布报告 → 封闭测试并记录反馈 → 申请正式发布资格 → 提交正式版本。

另请留意 Console 的 Android 开发者验证/包名注册提示：2026-09-30 起，巴西、印度尼西亚、新加坡和泰国的参与应用商店开始实施相关验证。不要为了这项要求重新注册一个不必要的开发者账号，应先查看现有 Play Console 的状态。

依据：[新个人账号测试](https://support.google.com/googleplay/android-developer/answer/14151465)、[实体设备验证](https://support.google.com/googleplay/android-developer/answer/14316361)、[Android 开发者验证](https://support.google.com/android-developer-console/answer/16561738)。

## 本次构建与复核方法

普通调试包（含爱发电入口）：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintRelease bundleRelease
```

不带支持页付款外链的构建（仍须进行完整政策审查，并配置上传签名）：

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintRelease bundleRelease -PexternalSupport=false
```

两种参数使用同一默认输出目录，后构建者会替换之前的产物。默认 release AAB 位于 `app/build/outputs/bundle/release/app-release.aab`；没有 signingConfig 时仍是未签名文件，不能直接上传。

本次新建的页面测试为 `SupportActivityTest`，覆盖外部页面确认/取消、复制链接、关闭外链时操作无效、页面重建和中英文横竖屏截图。未执行支付交易，也没有向爱发电上传用户数据。

验证结果：普通版与关闭外链版各通过 4 项测试（共 8 项），在 Android API 36.1 临时只读模拟器运行；已检查中英文、横竖屏实际截图。没有在实体手机、旧版 Android 或 16 KB 环境中完成全应用回归。

保留产物：根目录 `FloatPicture-2.0.5-beta-support-debug.apk` 可用于本地体验普通版；`app/build/outputs/bundle/no-external-support/app-no-external-support-unsigned.aab` 是关闭支持页外链的未签名候选包，不可直接上传。截图位于 `captures/support-page/`，不纳入 Git。

后续命名与商店图标更新：已重新通过 `assembleDebug lintRelease -PexternalSupport=true`，并读取 APK 确认中文显示名为“悬浮图片2”、默认英文名为“FloatPicture 2”；包名与版本号不变。根目录测试 APK 已更新。本次没有重跑设备测试或重建 AAB，前述 AAB 和截图属于改名前验证产物；正式签名时应从当前源码重新构建。

## 发布前缺陷修复复核（2026-08-31）

在上述命名更新之后，已修复横屏“关于”页与数字输入问题，并重新构建当前源码：

- 横屏布局补齐工具栏和内容容器，容器类型与竖屏一致，避免 Android 15/16 空指针及旋转恢复状态时的类型冲突；横屏内容可滚动，原作者和许可证说明保留。
- 透明度输入统一校验 0–1 范围；空值、不完整小数、负数、越界及非有限数值不再崩溃或被保存。键盘确认与页面确认使用同一处理，取消恢复原值，输入如 0.375 不会被滑块舍入。
- 缩放显示统一为小数点格式，接受逗号小数及本地数字输入；确认前校验两轴，错误时保留对话框，避免只保存一个轴；有效输入继续按尺寸限制处理。
- `assembleDebug assembleDebugAndroidTest lintRelease bundleRelease -PexternalSupport=true` 全部成功；最终静态检查 0 个错误、48 个警告，未发现 `.so` 原生库。
- 在临时 Android API 36.1 模拟器，`AboutLayoutTest` 2 项、`NumericEditingTest` 6 项、原有 `RotationEditingTest` 6 项和 `SupportActivityTest` 4 项，共 18 项全部通过。测试包括实际中英文横竖屏、页面重建、数字确认/取消/持久化；新增测试使用合成图片，没有操作实体设备或用户图片。
- 已检查 `captures/release-fixes/` 的关于页截图；临时模拟器测试结束后关闭。尚未完成实体手机、旧版 Android 和签名 release 安装回归。
- 根目录 `FloatPicture-2.0.5-beta-support-debug.apk` 和默认 `app/build/outputs/bundle/release/app-release.aab` 已更新。当前 AAB 为未签名、带爱发电入口版本；另存的 `no-external-support` AAB 仍是早期产物，不能作为本次修复后的发布包使用。

本次未更改包名、版本号、支持页开关或线上隐私政策，未提交或推送代码。可以从当前源码通过 Android Studio 生成签名 AAB 供测试；正式提交前仍须处理本清单中的付款入口政策、线上隐私说明及后台声明等事项。
