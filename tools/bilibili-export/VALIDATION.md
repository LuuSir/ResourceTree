# 验证记录

日期：2026-09-07，Asia/Shanghai。

当前交付版本：0.1.2（下载事件隔离 + 终止空页计数修复，保留单视频选择）。

| 检查 | 实际结果 |
| --- | --- |
| `node --check tools/bilibili-export/bilibili-fav-export.user.js` | SUCCESS |
| `node --test tools/bilibili-export/test/exporter.test.cjs` | SUCCESS，29 项，0 失败，0.1.2 实际执行 |
| 当前 TreeJson sample compatibility test | SUCCESS，BilibiliExportCompatibilityTest 2 项（整夹与单视频） |
| `gradlew.bat :app:testDebugUnitTest --console=plain --offline` | 0.1.1 阶段 SUCCESS，40 项，0 失败/错误，BUILD SUCCESSFUL in 42s；0.1.2 未重跑（Kotlin、Schema、sample 未改，JS 已验证 sample 与真实转换结果一致） |
| Chrome + 人工 API fixture | 已验证选择面板、全不选校验、全选、三页导出、45 视频/2 跳过项、空收藏夹、跳过记录展开、按钮防重入、取消后停止请求、第二页 API 错误停止并显示上下文、未登录提示、重复注入只显示一个按钮；下载验证至触发浏览器下载，未核实下载目录实际文件 |
| 0.1.1 Chrome + 人工 API fixture | 数量差额在空页探查后显示、无自动下载；单视频模式默认不勾选、按 BV 搜索、选择 1 项并触发下载；下载后仍保留数量差异提示 |
| 0.1.2 Chrome + 人工 API fixture | 旧版主文档 anchor.click 被模拟站点捕获 1 次；新版隔离框架下载后捕获数仍为 1、页面 URL 不变。第一页返回 14 项且总数 15，第二页 null 且总数 0：正常结束并报告差 1 项 |
| 0.1.2 下载落盘 | 未确认：工具访问 chrome://downloads/ 被浏览器策略拒绝；未绕过策略。界面状态仅证明触发下载且未触发模拟主文档路由 |
| 真实 Bilibili 登录态与 GM 请求 | 已读取用户真实页面上旧版“软件系统”第 3 页数量不匹配错误；用户另报告“研究生”相同错误。直接打开资源 API 返回 ERR_BLOCKED_BY_CLIENT，新版真实收藏夹导出未完成复测 |
| Tampermonkey / Violentmonkey 实际安装 | 用户已运行旧版并反馈下载错误网址及页 2 数量变化报错；0.1.2 尚需覆盖原脚本并在真实页面复测 |
| Android 真机导入与启动 Bilibili | 本轮未测试 |

Android 原有 38 项与导出器兼容性 2 项全部通过；生产 Kotlin 代码、数据模型、Schema 和构建配置未修改。默认 shell Java 8 不适用于该构建，本次采用项目 README 指定的 Android Studio JBR 和现有 GradleCache，通过获准的本地离线测试调用完成。JBR/Robolectric 的 native-access 警告没有使测试失败。

数量差异回归使用人工“标注 15/返回 14”“标注 56/返回 55”场景，并非从用户真实 API 抄录的数量。覆盖空页确认、后续页仍有资源、探查页失败/重复/取消/安全上限、全部资源未返回、差额与跳过统计分离。单视频回归验证只保留所选成员及所属目录、跨夹相同 BV 不会误选、空选择拒绝、示例由真实选择函数生成并被 Android TreeJson 解析。

0.1.2 新增 3 项分页回归，覆盖终止空页 info 总数为 0/缺省/其他值、没有 has_more 时的空页归零、首次空页保留列表计数、非空页总数不一致仍拒绝且不推断用户修改。下载隔离通过浏览器 fixture 的主文档捕获阶段处理器验证（对旧代码有效、新代码不传播至主文档），不是通过简单给 anchor 增加冒泡阶段监听器掩盖问题。未捕获用户真实 API 响应，空页默认值属于针对边界情形的修复，不宣称已经证实用户接口恰好返回 0。

证据：

- `test/android-test.log`：本轮成功构建日志。
- `test/js-test.log`：离线 JS 测试输出。
- 项目 `app/build/test-results/testDebugUnitTest/TEST-com.example.resouretree.BilibiliExportCompatibilityTest.xml`。
- 项目 `app/build/reports/tests/testDebugUnitTest/index.html`：完整 Android 测试报告。

JS 覆盖：当前 Schema 字段白名单、真实 Exporter 与 sample 完全一致、45 项三页顺序读取、has_more 与短页/旧总数冲突、缺失 has_more 的空页确认、空夹、跨夹重复 BV 的独立 ID、无效资源统计、bv_id fallback、自定义 package、UUID fallback、计数变化/重复/错误页防截断、分页上限、整夹失败、请求结束/退避期间取消、429/5xx/网络退避、风控与登录错误不重试、API 参数、登录/列表结构防御、GET/浏览器登录态、10 MB/10000 节点限制、跳过记录上限。

尚需在真实浏览器脚本管理器内验证私密收藏夹、真实多页与失效数据、取消、JSON 下载，再在 Android 真机验证导入与 Item 复制 BV/启动 Bilibili。不得将上述离线验证解释为这些环节已经通过。

## 本轮新增文件完整路径

```text
D:\Android\Projects\ResoureTree\tools\bilibili-export\bilibili-fav-export.user.js
D:\Android\Projects\ResoureTree\tools\bilibili-export\README.md
D:\Android\Projects\ResoureTree\tools\bilibili-export\VALIDATION.md
D:\Android\Projects\ResoureTree\tools\bilibili-export\samples\resourcetree-bilibili.sample.json
D:\Android\Projects\ResoureTree\tools\bilibili-export\samples\resourcetree-bilibili.single.sample.json
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\fixtures.cjs
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\generate-sample.cjs
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\exporter.test.cjs
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\browser-fixture.html
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\serve-fixture.cjs
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\js-test.log
D:\Android\Projects\ResoureTree\tools\bilibili-export\test\android-test.log
D:\Android\Projects\ResoureTree\app\src\test\java\com\example\resouretree\BilibiliExportCompatibilityTest.kt
```
