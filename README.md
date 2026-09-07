# ResourceTree MVP 0.1

离线树状快捷资源管理器。在现有 `ResoureTree` Android Studio 项目上实现，目录和应用包名均保留。

## 使用

- 首页只显示当前目录的直接子节点。点击文件夹进入，点击面包屑或返回键回到上层。
- 点击右下角「＋ 新建」创建文件夹或条目。
- 条目表单支持名称、内容、逗号分隔的标签、动作、目标 App package。
- 「动作复制文本」默认跟随内容，也可独立修改，导入的独立 action.text 不会被编辑表单静默覆盖。
- 点击条目执行动作；在目录内长按条目即可上下拖动排序，靠近列表边缘可自动滚动，松手后保存。
- 点击「更多」可以置顶/取消置顶、编辑、多选、移动、复制、删除。
- 「更多」→「多选」后，当前条目自动勾选；点其他行继续勾选，顶部可全选/取消全选，底部批量移动、复制、删除。
- 置顶条目固定在普通条目前面，两组各自支持拖动排序；取消置顶后排在普通条目组前部。搜索结果和多选模式不支持拖动排序。
- 多选模式中的点击只改变勾选，不会启动条目动作。返回或「取消」退出多选；搜索结果同样支持多选。
- 复制支持文件夹及其完整子树，所有副本使用新 UUID，保留内容、标签和动作。同名副本自动添加「（副本）」「（副本 2）」等后缀；在目标目录末尾追加。
- 批量移动/复制/删除均在单个 Room 事务中完成；同时选中祖先与后代时只处理一次子树。失败时保留选择并提示原因，数据库整批回滚。
- 移动选择目标目录，不允许移入自身或后代。删除目录会确认并删除全部后代。
- 「搜索」匹配所有目录中的条目名称、内容、标签；结果点击执行动作，长按管理。
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

Room 数据库 `resource-tree.db`，版本 2（从版本 1 原地迁移，保留已有资源）：

- `nodes`：UUID 主键，parentId、type、name、sortOrder、isPinned、createdAt、updatedAt、content、tagsJson、actionType、actionText、packageName。
- 新增 isPinned 默认 false，迁移只增加这一列。DAO 和界面均按置顶优先、sortOrder 顺序读取。
- `parentId` 为 null 表示根节点。父节点外键引用 `nodes.id`，有索引和 `ON DELETE CASCADE`。
- Repository 校验父节点必须是 Folder，禁止循环、孤儿和超深目录。
- `metadata`：保存 `demo_initialized` 标志。初始化和标志写入在同一事务中完成。
- 标签使用 JSON 字符串，由 `TagsCodec` 封装；领域模型中仍是 `List<String>`，以后可替换为规范化关系表。
- Repository 负责事务、CRUD、移动、初始化、导入导出；DAO 提供 Flow；ViewModel 输出不可变 StateFlow。
- ViewModel 不持有 Context；文件读写委托 DocumentStore，动作委托 ActionExecutor。
- 浏览位置、搜索词、编辑表单通过 SavedStateHandle 恢复。

Room 自动生成的结构快照位于 `app/schemas/com.example.resouretree.data.local.database.ResourceDatabase/`，保留版本 1 和版本 2。

## JSON v1

完整可导入示例：[`examples/resource-tree-v1.json`](examples/resource-tree-v1.json)。

```text
哔哩哔哩
├── 生产力
│   ├── 新三国up锐评老三国16：我与老贼，势不两立！
│   └── 编程学习笔记
└── 生命力
    └── 给自己的一句话
```

- 顶层必须有 `schemaVersion: 1` 和数组 `roots`。
- 每个节点要求 `type`（`folder` / `item`）与非空 `name`。
- Folder 用 `children` 递归表达层级，缺省时为空目录；Item 不可有非空 children。
- Item 保存通用 `content`、字符串数组 `tags`、`action`。
- Action 有 `type`、`text`、`packageName`；缺少整个 action 时默认为 NONE。
- Action 缺少 text 时默认使用 content；显式空字符串仍保持为空。
- COPY_AND_LAUNCH / LAUNCH_APP 要求非空 packageName。
- 合法 UUID 且无冲突时保留；缺失、非 UUID、重复、与已有数据库冲突时生成新 UUID。所有子节点绑定本次实际生成的父 ID。
- 可选 `sortOrder`、`createdAt`、`updatedAt`；省略时分别使用数组位置、导入时间、createdAt。
- 可选 `isPinned` 布尔值保存置顶状态，省略时为 false；仍使用兼容的 schemaVersion 1。
- 导出包含排序与时间信息；采用独立 Export DTO，不序列化 Room Entity。
- 导入限制为单文件 10 MB、10000 个节点、100 层目录；文件先全部解析和校验，再在 Room 事务中整体写入。
- 更高 schemaVersion 显示需要更新应用；未知节点/动作类型和不正确字段类型均明确报错，不默默丢弃数据。

## 动作流程与权限

```text
Item click → BrowserViewModel → ActionExecutor
                               ├─ NONE：无副作用
                               ├─ COPY：ClipboardWriter
                               ├─ LAUNCH_APP：PackageLauncher
                               └─ COPY_AND_LAUNCH：先 ClipboardWriter，再 PackageLauncher
```

ActionExecutor 通过接口隔离系统 API，未来添加 DEEPLINK / INTENT / OPEN_URL / SHARE 时无需将逻辑写入 Composable。

启动器先用 PackageManager.getLaunchIntentForPackage；无结果时 Android 13+ 尝试 getLaunchIntentSenderForPackage。未安装、无法启动或系统拒绝均返回错误结果，由 Snackbar 提示；复制成功不会因启动失败而撤回。

Manifest 无 INTERNET、存储权限、QUERY_ALL_PACKAGES 或 Accessibility 权限。`queries` 包含两个示例包及 MAIN/LAUNCHER intent；后者让 Android 11–12 能发现用户填写的其他可启动应用。它不请求读取全部软件包的权限。

应用不读取后台剪贴板，不联网，不自动同步。目标 App 是否读取并识别已复制文本由目标 App 决定。

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
- BatchSelectionInstrumentedTest：使用临时内存数据库，在真机验证长按多选、全选、批量复制/移动/删除、删除取消及勾选不执行动作，不改动用户资源。

实际验证结果见 [`BUILD-RESULTS.md`](BUILD-RESULTS.md)。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

相关官方参考：[AGP 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Room](https://developer.android.com/jetpack/androidx/releases/room)、[包可见性声明](https://developer.android.com/training/package-visibility/declaring)、[Robolectric 测试配置](https://robolectric.org/getting-started/)。
