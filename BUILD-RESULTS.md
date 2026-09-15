# 验证结果

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
