# 验证结果

v0.6 修复版本（2026-10-08）：124 项本地回归全部通过，Debug/Release 构建、Android 测试包编译及 lint 通过。Android 13 真机完整套件实际 22 项通过、1 项手动数据保留检查按设计跳过，0 失败；不包含本地抖音诊断类。补充的图过渡真机测试确认导航不重建画布、不出现加载闪烁，快速返回从当前帧衔接；本地测试另覆盖动画期间命中及一万节点匹配。悬浮尺寸真机测试确认实时 84 dp／168×168 px、关闭重启保留尺寸与恢复默认，结束恢复原 40 dp。其余真机覆盖应用选择器、拖拽、置顶、多选、剪贴板规则、图片分享及网页跳转。恢复同签名非调试 Release 后，显式 compare 另行通过：904 个节点、规则和 Room schema 3 摘要与升级前完全一致，首次安装时间未变；正式版文件夹图导航与返回也完成检查。MIUI 后台启动限制会阻挡测试宿主，本次仅在测试期间临时允许，随后恢复原 ignore；电源设置亦恢复并锁屏。发布检查实测接受 Release、拒绝 Debug；v0.5 下载附件已纠正。私有稿件已从公开分支、相关历史标签及源码档案移除，本地文件保留；按用户决定保留 GitHub 旧直链缓存，仓库继续公开。

悬浮按钮尺寸与安装修复版本（2026-10-06）：0.5 APK 已成功覆盖安装到已连接的 Android 13 真机，未卸载且首次安装时间保持不变。此前 `PackageInfo is null` 的根因是手机 `/sdcard/Download/ResourceTree-v0.4.apk` 下载文件损坏（ZIP/signature 校验失败），不是应用包名或数据库问题；当前 0.5 APK 安装成功并能冷启动。新增悬浮按钮 36–96 dp 尺寸调节，默认 56 dp，修改立即更新窗口并持久化；真机将滑杆调到 84 dp 后验证系统窗口为 168×168 px（320 dpi），随后恢复原 40 dp。Room schema 仍为 3，904 个已有节点与规则可正常读取。当前构建、119 项本地单元/Compose 测试、AndroidTest APK 编译和 lint 均通过（0 errors，37 warnings）。当前 debug APK SHA-256：`D2F7C68652EB10CB8AB18B39627D320B3FC85055DEA50C23D4ED35CE525F2621`。悬浮按钮专用 instrumentation 因 MIUI UI 自动化窗口枚举卡住而中止，但已用 adb UI 与 `dumpsys window` 完成等价真机验证。

通配符规则版本（2026-10-02）：构建、99 项本地测试、19 项正式真机回归、lint 通过（0 errors）。已覆盖安装；前置快照中的 896 个节点全部保留且逐字段未变，期间新增 1 个节点，最终 897 个。规则兼容保存。详见 [CLIPBOARD-WILDCARDS.md](CLIPBOARD-WILDCARDS.md)。

网页唤起 App 修复（2026-09-30）：构建、92 项本地测试、18 项正式真机回归及 lint 通过（0 errors）。用户给出的抖音短链已实测打开抖音详情页。覆盖安装前后 897 个节点及规则一致；旧的停滞诊断脚本不计入正式回归。详情见 [WEBVIEW-APP-LINKS.md](WEBVIEW-APP-LINKS.md)。

网页动作版本（2026-09-30）：assembleDebug、86 项本地测试、17 项真机测试及 lint 通过（0 errors）。已覆盖安装；895 个节点、metadata 与原剪贴板规则保持一致。新增 OPEN_WEBVIEW 和规则动作选择，无目标应用。详情见 [WEBVIEW-ACTION.md](WEBVIEW-ACTION.md)。下文保留此前版本结果。

最新规则配置版本（2026-09-15）：assembleDebug、unit tests、Android tests、lint 均完成；80 项本地测试、16 项真机测试通过。自动预填不再扫描应用列表，支持编辑前缀和候选包名。已覆盖安装；安装前后 Room v3 的 890 个节点与 metadata 逐行一致。详情见 [CLIPBOARD-RULES.md](CLIPBOARD-RULES.md)。下文为此前版本的验证记录。

最新剪贴板草稿版本（2026-09-15）：assembleDebug、unit tests、Android tests、lint 均已完成；73 项本地测试、15 项真机测试通过。BV / 【淘宝】剪贴板文字可自动预填首页条目，保存前不入库，并保留既有功能。已覆盖安装到 Android 13 手机。详情见 [CLIPBOARD-IMPORT.md](CLIPBOARD-IMPORT.md)。Content/Action v2 和位置选择记录见 [CONTENT-ACTION-V2.md](CONTENT-ACTION-V2.md)。以下保留首次 MVP 交付的历史记录。

验证日期：2026-09-07（Asia/Shanghai）。

最终实际执行：

```text
gradlew.bat :app:assembleDebug :app:test :app:assembleDebugAndroidTest :app:lintDebug --console=plain
BUILD SUCCESSFUL in 36s
88 actionable tasks: 12 executed, 76 up-to-date
```

| 检查 | 结果 |
| --- | --- |
| assembleDebug | SUCCESS |
| test | SUCCESS，实际执行 testDebugUnitTest，共 38 项，0 失败、0 错误 |
| assembleDebugAndroidTest | SUCCESS，设备测试 APK 已编译 |
| lintDebug | SUCCESS，0 errors、23 warnings |
| 真机 / Android 模拟器测试 | 未执行：adb devices 无连接设备，未找到已配置 AVD / 系统镜像 |

## 单元与本地 Android 测试

| 测试类 | 通过数 |
| --- | ---: |
| TreeAndJsonTest | 21 |
| ActionExecutorTest | 7 |
| RoomRepositoryTest | 8 |
| AppFlowTest | 1 |
| 原模板 ExampleUnitTest | 1 |
| 总计 | 38 |

RoomRepositoryTest 在 Robolectric Android 35 的 SQLite 环境验证事务。测试设置 SQLite trigger 让第二条导入记录失败，确认整个事务回滚，原数据库完全不变。

AppFlowTest 实际创建 MainActivity 并操作 Compose 界面，验证浏览示例目录、新建条目、写入系统剪贴板、全局搜索、编辑与删除确认流程。

交付示例 JSON 已由应用实际 TreeJson 解码器读取并验证：3 个文件夹、3 个条目，JSON 往返不丢失信息。

静态检查警告为依赖更新提示、原模板未使用颜色、原 Manifest 冗余 label 和测试依赖建议迁入版本目录。没有为消除更新提示升级现有工具链。Robolectric 在 JDK 25 下有 native-access 提醒，不影响测试成功。

## APK

已检查实际文件和 output-metadata.json：

```text
D:\Android\Projects\ResoureTree\app\build\outputs\apk\debug\app-debug.apk
applicationId: com.example.resouretree
versionName: 0.1
versionCode: 1
minSdk: 26
大小: 12,574,958 bytes
SHA-256: AD0F3DDEE4C322CF462D199901427B51659794E325965BD334BF0F41D9442D4D
```

合并后的 Manifest 不包含网络、存储、QUERY_ALL_PACKAGES 或 Accessibility 权限；AndroidX 添加了本应用内部动态广播接收器的 signature 权限。

## 证据文件

- `build-verification.log`：最终构建原始日志。
- `app/build/reports/tests/testDebugUnitTest/index.html`：测试报告。
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`：逐项测试结果。
- `app/build/reports/lint-results-debug.html`：Android 静态检查报告。
- `app/build/outputs/apk/debug/output-metadata.json`：APK 构建元数据。

验证范围：本地 Android 测试环境已覆盖应用启动和核心交互；实体设备上的系统 SAF 文件选择器、已安装目标 App 的实际跳转及目标 App 对剪贴板的识别尚未实测。
