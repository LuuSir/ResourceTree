# 剪贴板通配符规则

日期：2026-10-02。

入口：菜单 → 剪贴板规则 → 添加或编辑 → 选择「通配符匹配」。

| 规则 | 含义 |
| --- | --- |
| `BV*` | BV 开头，后面可为空或任意文字 |
| `*关键词*` | 任意位置包含关键词 |
| `*https://v.douyin.com/*` | 整段分享文案中包含该网站链接 |
| `BV?23` | BV 后恰好一个字符，再跟 23 |

- `*` 匹配零个或多个字符，包括换行；`?` 匹配一个 Unicode 字符，包括 emoji。其余字符按字面值匹配，不使用正则语法。
- 通配符匹配整段文字，区分大小写，忽略开头空白（与旧前缀模式相同）；需要允许前后文时填写 `*`。仅填 `BV` 在通配符模式下只匹配 BV 本身。
- 匹配后仍预填完整原文，不提取或替换内容。网页动作仍要求保存的 TEXT 为完整网址，混合分享文案需要用户在编辑页调整。
- 多条规则匹配时，非通配字符数量更多的优先；相同时优先前缀模式，再按现有列表顺序。默认 BV / 淘宝规则行为保持。
- 每条规则新增持久化 `matchType`（PREFIX / WILDCARD），原 `prefix` 字段继续保存匹配文本；缺少 matchType 的旧规则按 PREFIX 读取，旧问号和星号不会被重新解释。相同模式及相同文本不允许重复，跨模式可以分别配置。
- 匹配器使用显式星号回退算法，避免正则表达式的指数回溯。沿用规则长度 100、剪贴板长度 100000 的既有上限。
- 不改变 Action、目标应用查询、完整内容预填、确认保存、首页默认位置、去重、WebView 或数据库版本。

## 验证

- `clipboard-wildcard-validation.log`：assembleDebug、99 项本地测试、assembleDebugAndroidTest、lintDebug 通过，Lint 0 errors / 33 warnings。
- 新增测试验证零长度/多行星号、Unicode 单字符问号、整段匹配与大小写、正则标点按字面解释、旧前缀特殊字符兼容、优先级、持久化及重复规则校验、长文本近似匹配。
- `clipboard-wildcard-regression.log`：真机正式回归 `OK (19 tests)`；新增通过界面创建通配符规则，再读取真实剪贴板，验证前后文、换行和 emoji 的匹配及原文完整预填，取消不写条目。旧动作、规则、拖拽批量操作、导入和分享均通过。
- 与前次一样排除本地的 `DouyinDeepLinkDebugTest` 诊断脚本；包含断言的抖音实际短链回归仍运行并通过。首次未完成的测试进程日志保留于 `clipboard-wildcard-device.log`，不计为通过。
- 已覆盖安装，Room 保持 v3。前置快照 896 个节点在最终快照中全部存在且逐字段未变；期间另有 1 个新节点（最终 897），因此不将前后总数据宣称为完全一致。metadata 一致，旧规则补默认 matchType 后一致，foreign_key_check 无错误。未清空或恢复覆盖用户数据。
- 私有核对记录保存在忽略目录 `verification/clipboard/wildcard-before/` 和 `wildcard-after/`。测试创建的临时规则已删除，手机返回首页。
- APK SHA-256：`3D53E675E4F8F917D0C7F81E05F6C6B22AD2FE468E35E604979BDFAD686EA37C`。

## 验证

- `clipboard-wildcard-validation.log`：assembleDebug、99 项本地测试、assembleDebugAndroidTest、lintDebug 通过，Lint 0 errors / 33 warnings。
- 新增测试验证零长度/多行星号、Unicode 单字符问号、整段匹配与大小写、正则标点按字面解释、旧前缀特殊字符兼容、优先级、持久化及重复规则校验、长文本近似匹配。
- `clipboard-wildcard-regression.log`：真机正式回归 `OK (19 tests)`；新增通过界面创建通配符规则，再读取真实剪贴板，验证前后文、换行和 emoji 的匹配及原文完整预填，取消不写条目。旧动作、规则、拖拽批量操作、导入和分享均通过。
- 与前次一样排除本地的 `DouyinDeepLinkDebugTest` 诊断脚本；包含断言的抖音实际短链回归仍运行并通过。首次未完成的测试进程日志保留于 `clipboard-wildcard-device.log`，不计为通过。
- 已覆盖安装，Room 保持 v3。前置快照 896 个节点在最终快照中全部存在且逐字段未变；期间另有 1 个新节点（最终 897），因此不将前后总数据宣称为完全一致。metadata 一致，旧规则补默认 matchType 后一致，foreign_key_check 无错误。未清空或恢复覆盖用户数据。
- 私有核对记录保存在忽略目录 `verification/clipboard/wildcard-before/` 和 `wildcard-after/`。测试创建的临时规则已删除，手机返回首页。
- APK SHA-256：`3D53E675E4F8F917D0C7F81E05F6C6B22AD2FE468E35E604979BDFAD686EA37C`。
