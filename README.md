# ResourceTree MVP 0.1

离线树状快捷资源管理器。在现有 `ResoureTree` Android Studio 项目上实现，目录和应用包名均保留。

## 使用

- 首页只显示当前目录的直接子节点。点击文件夹进入，点击面包屑或返回键回到上层。
- 点击右下角「＋ 新建」创建文件夹或条目。
- 复制以 `BV` 或 `【淘宝】` 开头的文字后回到应用，会自动打开预填的新建条目：TEXT 内容保留完整剪贴板文字，动作是「复制并打开 App」，目标分别是已安装的哔哩哔哩版本或淘宝，默认保存到首页。名称已填写，仍需点击保存才会入库。
- 同一份剪贴板不会重复弹出；应用自身复制不会触发新建。正在编辑时暂缓打开新草稿，返回浏览页后再处理。仅在前台窗口获得焦点时读取，不进行后台监听或后台启动页面。
- 首页「菜单 → 剪贴板规则」可添加、编辑、停用或删除预填规则。设置匹配前缀、默认名称和候选包名（每行一个，按顺序优先选择可打开的应用）。自动预填只查询指定包名，编辑页只读取目标应用的名称与图标；主动打开应用选择器时才加载完整列表。规则独立保存于本机，详细验证见 [CLIPBOARD-RULES.md](CLIPBOARD-RULES.md)。
- 条目表单支持名称、内容、逗号分隔的标签和动作；需要打开应用时，从带图标的已安装应用列表中选择，支持按名称或包名搜索，无需输入包名。
- Content 支持 TEXT / IMAGE / VIDEO / FILE。TEXT 编辑文本；媒体通过系统文件选择器选择，保存私有副本及 MIME 类型。Action 仅保存 type / target，复制和分享读取当前 Content。
- 点击条目执行动作；在目录内长按条目即可上下拖动排序，靠近列表边缘可自动滚动，松手后保存。
- 点击「更多」可以置顶/取消置顶、编辑、多选、移动、复制、删除。
- 「更多」→「多选」后，当前条目自动勾选；点其他行继续勾选，顶部可全选/取消全选，底部批量移动、复制、删除。
- 置顶条目固定在普通条目前面，两组各自支持拖动排序；取消置顶后排在普通条目组前部。搜索结果和多选模式不支持拖动排序。拖动项在独立悬浮层跟手移动，其他行通过各自的弹簧动画让位，松手后平滑落位。
- 多选模式中的点击只改变勾选，不会启动条目动作。返回或「取消」退出多选；搜索结果同样支持多选。
- 复制支持文件夹及其完整子树，所有副本使用新 UUID，保留内容、标签和动作。同名副本自动添加「（副本）」「（副本 2）」等后缀；在目标目录末尾追加。
- 批量移动/复制/删除均在单个 Room 事务中完成；同时选中祖先与后代时只处理一次子树。失败时保留选择并提示原因，数据库整批回滚。
- 移动/复制使用固定宽度的位置窗口，初始位置为当前目录，面包屑可跳回任意上级；只列直接子文件夹，进入后点「移动到此处／复制到此处」确认。移动不允许移入自身或后代。删除目录会确认并删除全部后代。
- 「搜索」匹配所有目录中的条目名称、内容、标签；结果点击执行动作，通过「更多」管理。
- 「菜单 → 导出 JSON」通过系统文件选择器选择保存位置，导出整个数据库。
- 「菜单 → 导入 JSON」选择文件，完整校验后追加到首页，不覆盖现有节点。失败不会留下半棵树。
- 示例数据只初始化一次；清空后重启也不会重新生成。

## 原工程检查与工具链

| 项目 | 原配置及处理 |
| --- | --- |
| 项目目录 / Gradle root name | `ResoureTree`，保留 |
| namespace / applicationId | `com.example.resouretree`，保留 |
| Gradle | 9.5.0，保留 wrapper、镜像和校验值 |
| AGP | 9.3.2，保留，使用内置 Kotlin |
| Kotlin Compose 插件 | 2.2.10，保留 |
| Compose BOM | 2026.02.01，保留 |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37，保留 |
| Java 字节码 | 11，保留 |
| Gradle daemon JDK | 项目原有 JDK 25 配置，保留 |
| Material 3 / 主题 | 沿用现有 Compose、Material 3 和动态色主题 |
| 原源码 | MainActivity 的 Hello Android 模板与主题，无业务实现 |
| 新依赖 | Room 2.8.4、KSP 2.3.6、Navigation Compose 2.9.7、Lifecycle Compose 2.9.4、kotlinx JSON 1.9.0 |

`local.properties` 中 SDK 路径未修改。该文件仅适用于本机环境，不提交到仓库。

## 数据库与分层

Room 数据库 `resource-tree.db`，版本 3（支持 1 → 2 → 3 和 2 → 3 无损迁移，保留已有资源）：

- `nodes`：UUID 主键，parentId、type、name、sortOrder、isPinned、createdAt、updatedAt、contentType、contentText、contentPath、contentMimeType、tagsJson、actionType、actionTarget。
- 1 → 2 增加 isPinned，2 → 3 在同一事务内快照、重建节点表并恢复数据，删除旧动作文本副本字段。DAO 和界面继续按置顶优先、sortOrder 顺序读取。
- `parentId` 为 null 表示根节点。父节点外键引用 `nodes.id`，有索引和 `ON DELETE CASCADE`。
- Repository 校验父节点必须是 Folder，禁止循环、孤儿和超深目录。
- `metadata`：保存 `demo_initialized` 标志。初始化和标志写入在同一事务中完成。
- 标签使用 JSON 字符串，由 `TagsCodec` 封装；领域模型中仍是 `List<String>`，以后可替换为规范化关系表。
- Repository 负责事务、CRUD、移动、初始化、导入导出；DAO 提供 Flow；ViewModel 输出不可变 StateFlow。
- ViewModel 不持有 Context；文件读写委托 DocumentStore，动作委托 ActionExecutor。
- 浏览位置、搜索词、编辑表单通过 SavedStateHandle 恢复。

Room 自动生成的结构快照位于 `app/schemas/com.example.resouretree.data.local.database.ResourceDatabase/`，保留版本 1、2、3。

## JSON v2 与 v1 兼容

完整可导入示例：[`examples/resource-tree-v1.json`](examples/resource-tree-v1.json)。

```text
哔哩哔哩
├── 生产力
│   ├── 新三国up锐评老三国16：我与老贼，势不两立！
│   └── 编程学习笔记
└── 生命力
    └── 给自己的一句话
```

- 新导出统一使用 `schemaVersion: 2` 和数组 `roots`；继续接受 schemaVersion 1 的旧 B站导出文件。
- 每个节点要求 `type`（`folder` / `item`）与非空 `name`。
- Folder 用 `children` 递归表达层级，缺省时为空目录；Item 不可有非空 children。
- Item 保存通用 `content`、字符串数组 `tags`、`action`。
- Content 是对象：TEXT 用 type/text；IMAGE、VIDEO、FILE 用 type/path/mimeType。Action 只有 type/target；缺少整个 action 时默认为 NONE。
- v1 的 content 包装为 TEXT，packageName 映射到 target；旧 action.text 通常忽略，仅旧 content 为空时用作 fallback。
- COPY_AND_LAUNCH / LAUNCH_APP 要求非空 target；SHARE 的 target 可为空，表示系统分享面板。
- 同名根目录追加为独立的同名目录，不合并、不覆盖旧树。合法 UUID 且无冲突时保留；缺失、非 UUID、重复、与已有数据库冲突时生成新 UUID。所有子节点绑定本次实际生成的父 ID。
- 可选 `sortOrder`、`createdAt`、`updatedAt`；省略时分别使用数组位置、导入时间、createdAt。
- 可选 `isPinned` 布尔值保存置顶状态，省略时为 false；v1/v2 均保留这一字段。
- 导出包含排序与时间信息；采用独立 Export DTO，不序列化 Room Entity。媒体 JSON 保存引用及 MIME，不内嵌文件字节，跨设备导入后可能需要重新选择文件。
- 导入限制为单文件 10 MB、10000 个节点、100 层目录；文件先全部解析和校验，再在 Room 事务中整体写入。
- 更高 schemaVersion 显示需要更新应用；未知节点/动作类型和不正确字段类型均明确报错，不默默丢弃数据。

## 动作流程与权限

```text
Item click → BrowserViewModel → ActionExecutor
                               ├─ NONE：无副作用
                               ├─ COPY：ClipboardWriter
                               ├─ LAUNCH_APP：PackageLauncher
                               ├─ COPY_AND_LAUNCH：先 ClipboardWriter，再 PackageLauncher
                               └─ SHARE：Content → ACTION_SEND → target / 系统分享面板
```

ActionExecutor 通过接口隔离系统 API，未来添加 DEEPLINK / INTENT / OPEN_URL 时无需将逻辑写入 Composable。

启动器先用 PackageManager.getLaunchIntentForPackage；无结果时 Android 13+ 尝试 getLaunchIntentSenderForPackage。未安装、无法启动或系统拒绝均返回错误结果，由 Snackbar 提示；复制成功不会因启动失败而撤回。

Manifest 无 INTERNET、存储权限、QUERY_ALL_PACKAGES 或 Accessibility 权限。`queries` 包含两个示例包及 MAIN/LAUNCHER intent；后者声明应用选择器所需的可启动应用可见性，支持 Android 11 及以上查询应用名称、图标与启动目标。标准 Android 不提供单独的运行时「读取应用列表」权限弹窗；若厂商系统另行询问，允许后可在选择器中刷新。无需 QUERY_ALL_PACKAGES。应用列表只在本机加载、搜索，不上传。媒体分享通过仅开放 files/media 的 FileProvider 或可读 content URI，设置 EXTRA_STREAM、ClipData 和临时只读授权，不发送 file://。

排序、置顶、复制、移动、删除、导入导出以及动作执行成功后不再显示应用内 Snackbar；操作失败仍显示原因。系统自带的剪贴板反馈不由本应用控制。应用不读取后台剪贴板，不联网，不自动同步。目标 App 是否读取并识别已复制文本由目标 App 决定。

## 文件导航

```text
app/src/main/java/com/example/resouretree/
  MainActivity.kt                     主题与应用入口
  ResourceTreeApplication.kt          创建数据库与依赖
  data/local/entity/NodeEntity.kt     Room Entity、标签编解码、领域映射
  data/local/dao/NodeDao.kt            Flow 查询与持久化
  data/local/database/ResourceDatabase.kt
  data/repository/NodeRepository.kt   业务校验与事务
  data/transfer/TreeJson.kt            独立 DTO、树转换、JSON 校验
  data/transfer/DocumentStore.kt       SAF 文件流读写
  domain/model/ResourceNode.kt        Node / Action / Tree
  domain/action/ActionExecutor.kt     可测试动作执行器
  domain/action/AndroidActions.kt     系统剪贴板和启动器
  ui/navigation/ResourceTreeApp.kt    Navigation Compose
  ui/screens/BrowserScreen.kt         浏览、搜索、菜单、确认对话框
  ui/screens/EditorScreen.kt          复用表单、放弃修改确认
  ui/components/NodeRow.kt            文件夹/条目视觉与交互
  ui/viewmodel/BrowserViewModel.kt
  ui/viewmodel/EditorViewModel.kt
```

另修改 `build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、Manifest、app_name；保留原主题和环境文件。

## 构建与验证

在 Android Studio 同步后选择 app 运行；支持 Android 8.0 及以上。

当前电脑命令行默认 Java 为 8，构建时仅在进程中指定 Android Studio 自带 JDK，不修改系统设置：

```powershell
$env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'D:\Android\GradleCache'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# 有连接设备时
.\gradlew.bat :app:connectedDebugAndroidTest
```

- TreeAndJsonTest：树、父子关系、后代集合、Room Entity / Domain / DTO / JSON 往返、异常导入。
- ActionExecutorTest：复制/启动顺序、NONE、单独动作、缺失应用与系统异常。
- RoomRepositoryTest：Robolectric Android 35 环境下验证真实 Room/SQLite 初始化、CRUD、搜索、移动、外键、删除及导入回滚。
- AppFlowTest：Robolectric + Compose 测试启动应用，浏览、新建、复制、搜索、编辑和删除。
- RepositoryInstrumentedTest：设备侧 Room 测试，已配置独立 instrumentation APK。
- BatchSelectionInstrumentedTest：使用临时内存数据库，在真机验证长按拖动、置顶、「更多」多选、全选、批量复制/移动/删除、删除取消及勾选不执行动作，不改动用户资源。

- AppPickerTest / AppPickerInstrumentedTest：搜索、空结果、错误重试，以及真机读取应用图标、选择并保存启动目标。
- ReorderGestureTest：实际长按手势、独立悬浮层、两行在弹簧动画中途的位置以及最终顺序。

实际验证结果见 [`BUILD-RESULTS.md`](BUILD-RESULTS.md)。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

相关官方参考：[AGP 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Room](https://developer.android.com/jetpack/androidx/releases/room)、[包可见性声明](https://developer.android.com/training/package-visibility/declaring)、[Robolectric 测试配置](https://robolectric.org/getting-started/)。
