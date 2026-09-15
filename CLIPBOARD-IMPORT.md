# 剪贴板预填条目

完成日期：2026-09-15。

- 应用前台窗口获得焦点时读取剪贴板；前台运行期间也响应剪贴板变化。未添加后台读取权限、服务或后台启动 Activity。
- 去除开头空白后匹配 `BV` / `【淘宝】` 前缀，TEXT 保留剪贴板原文，包括换行、链接和标点。
- BV 默认名称取首行（最多 80 字符），目标优先使用已安装的 `tv.danmaku.bili`，其次 `com.bilibili.app.in`。淘宝默认名称为“淘宝分享”，目标为 `com.taobao.taobao`。未安装时仍保留预设目标，编辑页可重新选择应用。
- 两类草稿均设置 COPY_AND_LAUNCH，并默认保存至首页。所有数据仍由现有 Editor 保存，自动打开时不会写数据库；用户确认并点击保存后才创建条目。
- 剪贴板文本通过导航条目的 SavedStateHandle 传入现有编辑表单，不放入导航 URL；旋转/重建保留用户已经修改的内容。
- 正在编辑时暂缓处理新剪贴板，不覆盖当前表单。返回浏览页后再打开待处理草稿。
- 用剪贴板时间戳与文字哈希标识同一次复制，持久化的只是最近一次处理标识。同一次剪贴板不会重复弹出；应用自身 COPY 动作不会反过来触发新建。
- 忽略无文字的 URI、敏感标记、未匹配前缀及超过 100000 字符的剪贴板，避免把媒体、敏感内容或过大数据放入页面恢复状态。系统若拒绝读取，正常浏览和编辑不受影响。

## 验证

- `clipboard-validation.log`：assembleDebug、unit tests、assembleDebugAndroidTest、lintDebug 成功。
- 73 项本地测试通过：新增前缀、目标版本选择、原文保留、去重、自身复制排除、已有编辑保护、草稿恢复和确认后首页保存；原有模型、迁移、排序、批量操作、应用选择器、分享和导入兼容测试保持通过。
- `clipboard-device-tests.log`：15 项 Android 13 真机测试全部通过。真实系统剪贴板触发 BV / 淘宝预填；取消后数据库不增加条目；恢复测试前剪贴板。B站点击、图片跨 UID 分享、系统分享面板和位置选择回归同时通过。
- Lint 0 errors，31 warnings（依赖/模板及风格建议）。
- 覆盖安装前后数据库均为 Room v3、888 个节点；nodes 与 metadata 逐行一致。本次没有数据库迁移或清空数据。核对记录保存在已忽略的本地 `verification/clipboard/`。
- APK SHA-256：`0B3E9BF6E2F27CAFAEF7465C38E723F9B0160ABBB9362DCC9976B18522E29053`。

实现涉及 MainActivity、ClipboardDraftParser、ClipboardDraftInbox、ResourceTreeApp 与 EditorScreen；复用当前 AppPicker 和保存逻辑。

此前模型和分享升级见 [CONTENT-ACTION-V2.md](CONTENT-ACTION-V2.md)。平台剪贴板行为参考 [Android 复制与粘贴](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)。
