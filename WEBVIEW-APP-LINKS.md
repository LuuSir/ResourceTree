# 网页跳转 App

修复日期：2026-09-30。

原问题：短链接先通过 HTTP 302 到 H5，再由页面 JavaScript 跳转到 `snssdk1128://...` 或 `intent://...` 时，原 WebView 回调统一拒绝非 HTTP(S)，导致抖音无法唤起。

- `webUrl()` 继续负责条目初始网址校验。后续页面导航交给 WebNavigation 分类：HTTP(S) 留在 WebView，私有协议交给 Android 的 ACTION_VIEW / CATEGORY_BROWSABLE，intent URI 解析数据 URI 与指定包名。
- 不转发网页提供的任意 action、component、selector、flags 或 extras，重新构造浏览 Intent；不允许 file/content/javascript/data 等本地或执行协议。
- 不要求主页面 JS 跳转具有用户手势；背景 iframe 自动跳转不唤起 App，iframe 中的直接点击可以处理。
- 不预先依赖 PackageManager 可见性来断言应用是否存在，直接启动并捕获 ActivityNotFoundException / SecurityException。
- 打开失败时，合法的 `browser_fallback_url` 在原 WebView 内加载；没有回退地址则保留 H5，并提示未能打开对应应用。拒绝非网页回退地址，阻止同一回退地址反复循环。
- 成功打开同一个私有链接后，自动重复跳转会被忽略；用户主动点击或刷新可以重新打开。
- OPEN_WEBVIEW 仍无配置 target，不新增应用选择要求。此修改不涉及数据库、剪贴板规则或 JSON schema。

参考：[Android WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient)、[Chrome Intent URI 与网页回退格式](https://developer.chrome.com/docs/android/intents)。

## 验证

- `web-jump-validation.log`：assembleDebug、92 项本地测试、assembleDebugAndroidTest、lintDebug 全部通过；Lint 0 errors / 33 warnings（新增一条 KTX 写法建议）。
- 新增本地测试覆盖主页面无手势 JS 唤起、Intent 数据/包名解析与其他字段清理、未安装/无权限时回退、重复跳转保护、背景 frame 限制、非法协议拦截。
- `douyin-web-device.log`：用户提供的 `https://v.douyin.com/bdRNyijQu_s/` 在真机经过 WebView/H5 后成功打开已安装的 `com.ss.android.ugc.aweme/.detail.ui.DetailActivity`，单独用例通过。
- `web-jump-regression.log`：正式真机回归 `OK (18 tests)`，其中包含同一抖音短链实际跳转、网页历史/恢复/关闭、已有 B站动作、剪贴板规则、AppPicker、拖拽/置顶/批量操作、分享与导入测试。
- 项目原有 `DouyinDeepLinkDebugTest` 是等待后打印网页状态的诊断脚本；包含它的首次运行停滞并被终止（日志 `web-jump-device-verified.log`）。保留原脚本未修改，正式回归通过 `notClass` 排除该脚本，不把它计为通过。
- 已覆盖安装，无数据库迁移或数据清空。安装前后 Room v3 的 897 个节点、metadata、剪贴板规则完全一致；foreign_key_check 无错误。核对记录位于忽略目录 `verification/clipboard/web-jump-before/`、`web-jump-after/`。手机恢复到 ResourceTree 首页。
- APK SHA-256：`096FF40EFC1C357B1592479235B14872D98FDDDDC3E189C07D2EAB4F5607F388`。

## 验证

- `web-jump-validation.log`：assembleDebug、92 项本地测试、assembleDebugAndroidTest、lintDebug 全部通过；Lint 0 errors / 33 warnings（新增一条 KTX 写法建议）。
- 新增本地测试覆盖主页面无手势 JS 唤起、Intent 数据/包名解析与其他字段清理、未安装/无权限时回退、重复跳转保护、背景 frame 限制、非法协议拦截。
- `douyin-web-device.log`：用户提供的 `https://v.douyin.com/bdRNyijQu_s/` 在真机经过 WebView/H5 后成功打开已安装的 `com.ss.android.ugc.aweme/.detail.ui.DetailActivity`，单独用例通过。
- `web-jump-regression.log`：正式真机回归 `OK (18 tests)`，其中包含同一抖音短链实际跳转、网页历史/恢复/关闭、已有 B站动作、剪贴板规则、AppPicker、拖拽/置顶/批量操作、分享与导入测试。
- 项目原有 `DouyinDeepLinkDebugTest` 是等待后打印网页状态的诊断脚本；包含它的首次运行停滞并被终止（日志 `web-jump-device-verified.log`）。保留原脚本未修改，正式回归通过 `notClass` 排除该脚本，不把它计为通过。
- 已覆盖安装，无数据库迁移或数据清空。安装前后 Room v3 的 897 个节点、metadata、剪贴板规则完全一致；foreign_key_check 无错误。核对记录位于忽略目录 `verification/clipboard/web-jump-before/`、`web-jump-after/`。手机恢复到 ResourceTree 首页。
- APK SHA-256：`096FF40EFC1C357B1592479235B14872D98FDDDDC3E189C07D2EAB4F5607F388`。
