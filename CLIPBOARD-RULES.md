# 剪贴板预填规则

完成日期：2026-09-15。

- 入口：首页「菜单 → 剪贴板规则」。支持添加、编辑、启用/停用和删除规则，删除前确认，未保存编辑返回时可继续编辑或放弃。
- 字段：匹配前缀、默认名称、目标包名/候选包名。每行一个包名，按顺序选第一个可打开的应用；都不可用时保留第一个包名供后续调整。名称留空取剪贴板首行，最多 80 字符。
- 匹配忽略剪贴板开头空白，区分大小写，多个匹配选最长前缀；不允许重复前缀。候选包名校验格式、去空行和重复项，最多 20 个。默认规则保留 BV 和【淘宝】。
- 自动预填仅调用限定包名的启动 Intent 查询。编辑页只读取当前 target 的应用名称/图标，不触发完整 AppPicker 扫描。用户主动点击目标应用卡片时才加载现有应用选择器，保留图标、搜索和重试。
- 规则保存在独立 SharedPreferences，未更改 Room 版本、条目数据或 schema v2。规则暂不包含在资源 JSON 导入导出中。
- 继续保留完整剪贴板文字、TEXT、COPY_AND_LAUNCH、默认首页、确认保存才入库、同次剪贴板去重和自身复制排除。规则页面不会被新剪贴板打断；回到浏览页时按最新规则重新验证待处理草稿，正在编辑的条目不被覆盖。

## 验证结果

- `clipboard-rules-final-validation.log`：assembleDebug、testDebugUnitTest、assembleDebugAndroidTest、lintDebug 全部成功。
- 80 项本地测试，0 失败/错误/跳过。新增规则匹配优先级、持久化/停用/删除、格式校验、候选查询短路、显式打开前不扫描目录、规则页面编辑及待处理剪贴板使用最新规则的联动测试。
- `clipboard-rules-device-tests.log`：Android 13 MIUI 真机 `OK (16 tests)`。新增通过界面创建自定义规则并重读持久化设置、首候选未安装时选择下一候选、单应用图标可读取；测试完成后删除临时规则。既有剪贴板、应用选择器、批量操作、置顶排序相关回归、B站实际条目点击、跨 UID 图片/文字分享及系统分享面板通过。
- Lint：0 errors，32 warnings（依赖/模板及 KTX 风格建议）。
- 已通过 `install -r` 覆盖安装应用和测试 APK，未卸载或清空应用数据。安装前与全套回归后的 Room v3 数据库均为 890 个节点，nodes 和 metadata 逐行一致，foreign_key_check 无错误。私有快照与核对结果位于忽略目录 `verification/clipboard/rules-before/` 和 `rules-after/`。
- 真机检查规则列表布局，当前保留默认 BV / 淘宝规则；手机停留在规则页便于直接配置。
- APK SHA-256：`D8A7FAF3F0603EE0D0EC62CD77A2F33B06F45D73C5AD363685B766FAFFEDF45D`。

实现主要位于 ClipboardRule、ClipboardRuleStore、ClipboardRulesScreen、AppCatalog、EditorViewModel / EditorScreen 和 ResourceTreeApp；拖拽、位置选择、数据迁移、导入导出和动作执行代码未改动。
