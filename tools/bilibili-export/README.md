# Bilibili 收藏夹 → ResourceTree

独立、单文件 Userscript：`bilibili-fav-export.user.js`。仅导出当前登录账号自己创建的视频收藏夹。安装无需 Node、npm、构建或外部 CDN。

当前版本：**0.1.2**。已有用户请在 Tampermonkey / Violentmonkey 编辑原脚本，将内容全部替换为本目录的新文件并保存，再刷新 Bilibili 空间页面；不要额外保留一个同时运行的旧版本。新版权限不变。

## 0.1.2：下载跳转与空页总数误判

- 旧版将 Blob 下载链接插入 Bilibili 主文档，可能被站点点击捕获、链接改写或 SPA 路由处理，表现为含 UUID 和 `spm_id_from` 的错误空间地址。新版在独立空白 iframe 文档中创建并点击下载链接，隔离主页面事件；iframe 只允许同源文档访问和下载，不允许脚本、弹窗或顶层导航。不会用打开 blob URL 的方式替代失败下载。
- 空页/越界页返回的 `info.media_count` 可能是 0 或默认值。新版先判断 medias 是否为空，不再将空页 info 当成新的收藏夹快照，也不会覆盖此前已知的总数。差额仍如实报告。
- 如果两张**非空页**真的报告不同总数，仍停止以防结果不一致，但说明是“接口返回不同总数、原因未确定”，不推断用户修改过收藏夹。
- 若旧版已经导致空间页显示 404，请在更新后重新打开正常的个人空间/收藏夹地址；不要刷新错误的 `space.bilibili.comhttps...` 地址。

下载隔离采用标准 [iframe sandbox / allow-downloads](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/iframe#sandbox)，没有新增 Userscript 权限。

## 只导出一个视频

1. 点击“导出 ResourceTree”，全不选后勾选视频所在收藏夹。
2. 勾选“先选择视频再导出（可只导出一个）”。
3. 点击“读取视频供选择”，等待 API 分页读取完成。
4. 在下面的视频列表按标题或 BV 搜索，勾选一个视频。
5. 点击“导出选中视频”。也可勾选多个视频；初始不勾选任何视频。

此模式从 API 数据中选择，仍不扫描网页视频卡片。下载前不自动导出整个收藏夹；搜索不清除已勾选项目，可用“全不选视频”清空选择。JSON 保留“哔哩哔哩 → 所属收藏夹 → 所选视频”；未选择视频的目录不会加入单视频结果。读取阶段仍受现有节点容量限制，建议只选择目标收藏夹。

## “has_more=false 但未达到资源总数”的修复

0.1.0 把接口 `media_count` 当作必须精确等于实际返回条数的硬条件，可能把正常结束的分页判断为失败。用户在“软件系统”“研究生”遇到了此错误；页面中已确认该错误来源于脚本检查，并非登录错误。

0.1.1 在末页出现数量差额时继续探查下一页，有资源就继续读取，直到确认空页。若标注数量仍较多，界面单独显示“标注数量／实际返回／差额”。**差额原因未确定**，不会擅自归为“已删除”或并入跳过视频统计。

整夹模式存在差额时不自动下载，提供“确认下载已返回的 N 个视频”；确认后 JSON 仅含 API 实际返回且可转换的视频。单视频模式保留差额提示，用户可直接选择已返回的视频。真实 HTTP/API 错误、重复分页、非空页总数不一致及取消仍停止任务，不能确认完整性的真实请求失败不会被此修复忽略。

## 安装和使用

1. 在浏览器安装 Tampermonkey 或 Violentmonkey。
2. 在扩展管理面板新建 Userscript。
3. 将 `bilibili-fav-export.user.js` **全部内容**替换进去，保留顶部 metadata。
4. 保存并确认脚本已启用。若扩展提示 Userscript 执行权限未开启，请按该扩展官方说明设置。
5. 在同一个浏览器正常登录 Bilibili；无需输入 UID 或 Cookie。
6. 打开或刷新个人空间 `https://space.bilibili.com/` 下的页面。
7. 点击右下角“导出 ResourceTree”。空间首页与收藏夹页均保留该按钮，支持 SPA 切换；重复初始化只创建一个按钮。
8. 等待当前账号与收藏夹列表显示，单独勾选、全选或全不选。默认全选，保留空收藏夹。
9. 目标 App 默认“Bilibili 国内版”。高级设置可将 package 改为 `com.bilibili.app.in` 等实际安装的包名；脚本不检测手机应用。
10. 点击“开始导出”，等待逐夹、逐页读取；可随时取消。取消后当前请求结束即停止，不再发出新请求。
11. 完成后浏览器下载 `resourcetree-bilibili-YYYY-MM-DD.json`。文件名采用浏览器当地日期，UTF-8、两空格缩进、无 BOM。同名文件由浏览器处理；如果下载未保存，可点“重新下载”。
12. 把 JSON 传到 Android 手机，在 ResourceTree 中选择“导入 JSON”。
13. 打开导入的“哔哩哔哩”目录，点击 Item 会复制 BV 并启动所选 Bilibili App。

## 权限与隐私

- 只匹配 `https://space.bilibili.com/*`，不在 iframe 中运行。
- 唯一特权 API 为 `GM_xmlhttpRequest`；唯一 `@connect` 为 `api.bilibili.com`，没有通配域名或 `@require`。
- 所有业务请求统一封装为 GET，只读现有收藏夹；没有添加、编辑、删除收藏、登录器或自动同步。
- 使用脚本管理器自动附带的浏览器登录态（`anonymous: false`），不读取或手工设置 Cookie 请求头。
- 不要求填写、不保存、不显示、不记录 Cookie、SESSDATA 或其他凭据；不访问浏览器 Cookie 数据库。
- 无第三方收藏数据上传；JSON 完全在浏览器本地生成。输出通过明确字段白名单构造，不含原始 API response、账号 UID、UP 主或平台元数据。
- 收藏夹名、视频标题、错误消息通过 `textContent` 显示，不解释为 HTML。CSS 仅作用于 `rt-export-` 自有界面。

网络 API 行为参考 [Violentmonkey 官方 GM_xmlhttpRequest 文档](https://violentmonkey.github.io/api/gm/#gm_xmlhttprequest) 和 [Tampermonkey 官方文档](https://www.tampermonkey.net/documentation.php)。采用 GM 请求处理空间域与 API 域跨源访问，不依赖页面 fetch 的 CORS 配置。

## 当前真实 ResourceTree Schema

唯一依据：`app/src/main/java/com/example/resouretree/data/transfer/TreeJson.kt`，未修改 Android production code。

| 部分 | 真实规则及脚本输出 |
| --- | --- |
| 顶层 | 必须为对象；`schemaVersion` 为数字整数 `1`，`roots` 为节点数组 |
| 公共字段 | `id`, `type`, `name`, `sortOrder`, `createdAt`, `updatedAt`；脚本全部显式输出 |
| 类型 | JSON 字符串为小写 `folder` / `item`；Android 内部枚举为 `FOLDER` / `ITEM` |
| 名称 | 非空字符串；解码器会 trim |
| 排序、时间 | 非负整数；`sortOrder` 不超过 Int.MAX_VALUE，按兄弟节点位置编号；时间为导出时 Unix 毫秒 |
| Folder | `children` 为递归节点数组；空目录为 `[]`；不输出 item 专属字段 |
| Item | `content` 为 BV 字符串，`tags: []`；不输出 children |
| Action | `type: "COPY_AND_LAUNCH"`, `text: BV`, `packageName: "tv.danmaku.bili"`（或所选包名） |
| 全部动作枚举 | `NONE`, `COPY`, `LAUNCH_APP`, `COPY_AND_LAUNCH` |
| ID | 解码器允许字符串缺省；转为领域节点时保留有效 UUID，其余、冲突 ID 会重新生成。脚本用 `crypto.randomUUID()`，fallback 为 `getRandomValues` 构造 UUID v4 |
| 容量 | 当前解码器限制 10 MB UTF-8、10000 个节点（含总根和收藏夹）、100 层；脚本固定三层且在下载前校验字节/节点数，超限提示减少选择 |

缺省与 null 的实际区别：

- 可选字符串缺省/null → 空字符串（`id` 进一步转为空 ID）；必填 `type/name` 不能缺失/null。
- `tags/children` 缺省 → 空数组，但显式 null 不接受；`roots` 必须存在且不能为 null。
- `action` 缺省/null → 默认 NONE；有 action 时 type 必填；启动类动作要求非空 `packageName`。
- `action.text` **缺省**时使用 `content`；显式空字符串或 null 会解码为空字符串，不再回退。脚本同时写入相同 BV 到 `content/text`。
- 数值缺省时：排序用当前位置，createdAt 用导入时间，updatedAt 用 createdAt；显式 null、负数、字符串或小数不接受。
- 当前解码器手工取字段，忽略未知字段；脚本仍只输出现有编码器支持的字段。

映射：收藏夹 `title → Folder.name`；视频 `title → Item.name`；有效 `bvid`（或 `bv_id`）→ `content` 和 `action.text`；tags 始终为空。外部平台数据只作为转换输入，不形成第二套 Android 数据模型。同一 BV 在不同收藏夹保留不同 UUID 的成员节点，不做全局去重。

## API 与完整分页

所有 URL 均在 `https://api.bilibili.com` 下：

| 路径 | 参数/用途 |
| --- | --- |
| `/x/web-interface/nav` | 检查 `isLogin/mid/uname`，未登录即停止；开始导出时再确认账号未切换 |
| `/x/v3/fav/folder/created/list-all` | `up_mid=当前mid`，读取自己创建的收藏夹；验证 list、id、title、重复 ID 和可用 count |
| `/x/v3/fav/resource/list` | `media_id=收藏夹id&pn=页号&ps=20&platform=web` |

收藏夹和页都顺序处理，每次正常请求前间隔 200ms。网络错误、超时、HTTP 429/5xx 最多重试 3 次，退避 500/1000/2000ms；HTTP 401/403/412、非零 Bilibili code、非 JSON 和返回结构异常不重试，不进行 WBI、验证码或风控绕过。错误包含收藏夹名称、页号和可用 HTTP/API code/message。

分页以 `pn=1` 开始，最多每夹 10000 页：

- `has_more=true/1` 时继续，即使当前页不足 20 或达到旧 media_count，避免提前截断。
- `has_more=false/0` 且达到已知总数（或没有总数）时结束；存在数量差额则继续请求下一页确认，不直接报错。
- 没有 has_more 时，短页和达到数量只作提示，继续请求下一页直到空页。空页与 has_more=true 冲突时失败；空页确认结束后仍有数量差额，则报告差额并要求用户决定是否下载已返回的资源。
- `medias:null` 仅在明确 has_more=false 时当作空页；字段缺失或错误类型报错。
- 非空资源页 `info.media_count` 优先于选择界面的旧计数；后续非空页总数不一致、同夹重复资源、分页上限都使本次导出失败。终止空页的默认 info 不参与总数一致性比较。
- 单个资源没有有效 BV、非视频、没有标题或结构异常可以跳过，读取计数仍包含这些项。完成面板列出分类统计和最多 100 条简短记录，JSON 不含日志。
- BV 只要求 trim 后以 `BV` 开头、有后缀且无空白，不绑定固定长度或固定字母表。`bvid` 无效时尝试 `bv_id`。

任何整夹/分页错误或取消都不会自动下载部分 JSON。接口不是事务快照，导出期间请不要同时修改收藏夹；数量和重复检测无法发现所有“数量不变但内容被替换”的并发变更。

## 离线测试

不需要安装 JS 依赖，在项目根目录运行：

```powershell
node --check tools/bilibili-export/bilibili-fav-export.user.js
node --test tools/bilibili-export/test/exporter.test.cjs
```

交付 sample 是脚本的真实 Exporter 从人工 fixture 生成的：总根“哔哩哔哩”下有“生产力”“生命力”，共 3 个虚构视频；另有 `samples/resourcetree-bilibili.single.sample.json`，包含筛选后的 1 个视频。均无用户真实数据。重建两份示例：

```powershell
node tools/bilibili-export/test/generate-sample.cjs
```

Android 测试 `BilibiliExportCompatibilityTest` 从该文件读取内容，用真实 TreeJson decode/toNodes 验证动作、内容、包名、树关系、UUID、往返和重复导入。沿用项目原有 JDK 和缓存设置：

```powershell
$env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'D:\Android\GradleCache'
.\gradlew.bat :app:testDebugUnitTest --console=plain --offline
```

离线浏览器 UI 验证页：

```powershell
node tools/bilibili-export/test/serve-fixture.cjs
```

打开 `http://127.0.0.1:18765`。仅监听本机，固定路径，不代理真实请求；页面可切换成功、慢速取消、第二页错误和未登录场景，重复注入脚本以检查按钮唯一性。关闭服务用 Ctrl+C。此页面并非脚本安装地址，不能替代真实脚本管理器测试。

## 已知限制与实测状态

- 依赖 Bilibili 非公开 Web API，接口改变后可能需要升级。
- 用户已在真实空间页面运行旧脚本并提供数量不一致错误。本次已读取该错误；直接打开资源 API 仍返回 `net::ERR_BLOCKED_BY_CLIENT`。新版在两个真实收藏夹上的复测及已安装脚本替换尚未完成。无需为此提供 Cookie。
- 失效且缺少 BV 的视频会跳过。有 BV 的条目保留，但脚本不另调播放接口检查可播放性；私密、审核或删除状态可能仍导致手机打开不可用。
- 只包含自己创建的收藏夹，不包含别人合集、追番、稍后再看、历史等系统集合。
- 不自动加 UP 主标签，不下载封面，不修改 Bilibili 数据，不同步手机，不上传第三方。
- 超过 App 当前容量请分批选择收藏夹导出；每个文件有独立“哔哩哔哩”总根，重复导入会产生新节点，不执行自动合并。

必须继续人工验收：Tampermonkey/Violentmonkey 安装、真实登录识别、私密收藏夹、真实多页大收藏夹、失效资源、真实请求期间取消、下载 JSON 的 Android 真机导入、点击 Item 后复制 BV 并启动 Bilibili。

本轮实际测试证据与结果见 `VALIDATION.md`。
