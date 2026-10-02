# 应用内网页动作

实现日期：2026-09-30。

## 使用

- 编辑条目：内容类型选 TEXT，文本内容填完整网址，动作选「应用内打开网页」。此动作不需要目标应用；保存时清除旧 target。
- 剪贴板规则：首页「菜单 → 剪贴板规则」，添加或编辑规则，设置前缀（例如 `https://` 或具体网站地址），预填动作选「应用内打开网页」。包名输入框隐藏，规则不保存候选包名，也不查询应用列表。草稿仍需确认保存，默认放入首页。
- 现有 BV、淘宝和自定义规则未设置动作字段时继续使用 COPY_AND_LAUNCH。不会自动添加通用网址规则，避免改变现有剪贴板触发范围。

## 实现

- 新动作类型 `OPEN_WEBVIEW`，从当前 `content.text` 读取网址。拒绝非文字内容、缺少协议/主机、多个网址混杂的文字，以及 file/content/javascript/intent 等非网页协议。
- 新增非导出 WebPageActivity，使用 Android 系统 WebView，在应用内加载网页。提供地址显示、进度、返回历史、刷新、关闭；保存并恢复网页历史，暂停/销毁时释放 WebView。
- 启用网页 JavaScript 和 DOM storage，未加入任何 JavaScript 原生桥接；关闭文件和 content URI 访问、不允许 HTTPS 混合明文内容、无效证书停止加载。后续已支持网页的 App 私有协议与 intent 跳转，见 [WEBVIEW-APP-LINKS.md](WEBVIEW-APP-LINKS.md)。
- 添加 INTERNET 普通权限，无运行时权限弹窗。网络仅用于用户打开的网页；树数据仍存储在本地。
- 保留系统默认明文流量限制。HTTP 页面可能被系统禁止，界面会提示使用网站提供的 HTTPS 地址；未启用全应用明文流量。该全局配置曾被自动审批以扩大安全风险为由拒绝，采用上述受限实现。
- Room 保持 v3，actionType 本身是字符串，不需要修改表结构或迁移。资源 JSON 保持 schema v2；新增动作可正常往返，旧 v1/v2 保持兼容。
- 规则偏好新增 actionType 字段，读取缺失字段回退 COPY_AND_LAUNCH，网页规则允许空 targets；旧规则和用户设置保留。

参考：[Android WebView 官方文档](https://developer.android.com/develop/ui/views/layout/webapps/webview)。

## 验证

- `webview-verified.log`、`webview-final-checks.log`：assembleDebug、testDebugUnitTest、assembleDebugAndroidTest、lintDebug 通过；86 项本地测试，0 失败，Lint 0 errors / 32 warnings。
- 新增测试覆盖：网址和协议校验；仅从 Content 打开且不复制/不启动目标包；Room entity / v2 JSON 往返；旧规则缺省动作；网页规则无 targets 的持久化；规则界面到草稿、重建后保存的完整流程。
- 第一轮新增测试中的非 UUID 测试数据已修正；真机测试中关闭独立 ActivityScenario 任务后不应要求原测试容器可见，已改为验证网页 Activity 结束。相关初次失败日志保留，不修改产品行为规避测试。
- `webview-device-verified.log`：Android 13 真机完整 `OK (17 tests)`，包含实际 HTTPS 页面的加载、刷新、第二页导航、Activity 重建恢复、历史返回和关闭；原有 B站、剪贴板、应用选择器、拖拽置顶、批量操作、分享和导入回归通过。
- 已覆盖安装，未清空数据。安装前后均为 Room v3、895 个节点，nodes 与 metadata 逐行一致，规则按旧动作缺省值归一化后完全一致；foreign_key_check 无错误。快照与比较记录位于忽略目录 `verification/clipboard/webview-before/`、`webview-after/`。手机恢复至应用首页。
- APK SHA-256：`1730F62B9C19F77A5164997566339A133083AFEABBA4A74BAC7AF049066F5F39`。
- `webview-device-verified.log`：Android 13 真机完整 `OK (17 tests)`，包含实际 HTTPS 页面的加载、刷新、第二页导航、Activity 重建恢复、历史返回和关闭；原有 B站、剪贴板、应用选择器、拖拽置顶、批量操作、分享和导入回归通过。
- 已覆盖安装，未清空数据。安装前后均为 Room v3、895 个节点，nodes 与 metadata 逐行一致，规则按旧动作缺省值归一化后完全一致；foreign_key_check 无错误。快照与比较记录位于忽略目录 `verification/clipboard/webview-before/`、`webview-after/`。手机恢复至应用首页。
- APK SHA-256：`1730F62B9C19F77A5164997566339A133083AFEABBA4A74BAC7AF049066F5F39`。
