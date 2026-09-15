# Content / Action v2 与位置选择

日期：2026-09-08。

## 数据模型与兼容

- `ResourceNode.content` 改为 `ResourceContent(type, text, path, mimeType)`，支持 TEXT、IMAGE、VIDEO、FILE。TEXT 使用 text，其他类型使用 path/mimeType。
- `ResourceAction` 只保留 type/target。COPY 和 COPY_AND_LAUNCH 读取当前 content.text；SHARE 接收当前 Content。旧动作文本副本和旧 Action 包名字段不保留在正常运行模型或 Entity 中。
- Room 升至版本 3，新增 `MIGRATION_2_3`，保留 `MIGRATION_1_2`。在 Room 事务内先建立无外键快照，再重建最终节点表、恢复数据和索引；避免旧 SQLite 的重命名外键差异和自引用级联删除影响快照。无 destructive migration。
- 新表仍使用扁平字段：contentType/contentText/contentPath/contentMimeType、actionType/actionTarget；其他节点字段和 metadata 表保留。
- 新导出统一使用 schemaVersion 2，继续读取 v1。v1 content 包装为 TEXT，packageName 映射到 target；只有旧 content 为空时，才使用旧 action.text 作为 fallback。
- v1/v2 导入都是追加。同名根目录不会合并或覆盖，而是成为独立的同名目录；ID 冲突时生成新 UUID，并重绑本次子树。导入不会自动给名称添加“副本”后缀。

## 媒体选择和分享

沿用 Editor 与 AppPicker。新增内容类型选择，TEXT 编辑文本；媒体通过 ACTION_OPEN_DOCUMENT 选择并在后台复制到 files/media 私有目录，MIME 自动填写且可调整。取消系统选择器不会改变内容。复制失败删除未完成文件。保存后的媒体不依赖原文件位置或临时选择授权。

SHARE 根据 Content 构造 ACTION_SEND：文本为 text/plain + EXTRA_TEXT；媒体为相应 MIME + EXTRA_STREAM。仅 files/media 下的私有文件通过 FileProvider 转成 content URI；已有 content URI 先检查可读性。设置 ClipData 与临时只读授权，不发送 file URI，不开放数据库或整个 files 目录。target 非空时设置目标包，空时使用系统分享面板。

媒体 JSON 只保存路径引用和 MIME，不包含文件字节。跨设备导入、引用失效时需要重新选择媒体文件；这一阶段尚未实现媒体打包导出。

## 移动/复制位置窗口

固定宽度，初始位置为当前目录。面包屑支持跳回任意上级，只显示当前目录的直接子文件夹；进入目录不会立即提交，点“移动到此处／复制到此处”才确认。移动仍排除所选文件夹自身及其后代，复制支持完整子树。未改动拖动排序、弹簧动画、置顶、多选和成功操作静默的交互。

## 验证记录

- 最终构建和 Lint：`content-v2-final-build.log`。66 项本地测试通过；Lint 0 errors、25 warnings，主要为原依赖/模板建议及 KTX 风格建议。
- 本地覆盖：Room 1→2→3 与 2→3、父子外键、排序/置顶/标签/时间/初始化标记、v1 文本 fallback、实际 B站旧导出、v2 文本/媒体往返、Action 使用当前 Content、文件选择编辑保存、窗口固定宽度与面包屑、同名根目录追加不覆盖。
- 真机升级前后快照位于本地忽略目录 `verification/content-v2/`。节点数均为 871（848 个 Item）；全部节点按迁移规则逐字段匹配，metadata 一致，foreign_key_check 为 0。之后另一次快照为 878 个节点，存在增删、移动与更名差异，不能声称后续使用期间的数据保持不变；迁移无损结论以紧邻升级前后的 871 个节点逐字段核对为准。升级和测试均未卸载或清空应用。
- 真机分享接收 Activity 仅在测试 APK 内，运行于独立 UID。实际 PNG 分享验证 content URI、MIME、解码宽度、SHA-256；删除原始文件后私有副本仍可分享。定向文字分享和系统 Sharesheet 同时验证。
- 真机 B站回归从实际数据库读取已有条目，通过浏览页搜索并点击，确认复制 content.text 且 B站 Activity 成为前台。
- 设备测试日志：`content-v2-final-device-tests.log`。首轮批量删除等待空界面出现过一次超时，单独复验已通过（`destination-device-recheck.log`），随后完整回归 14 项全部通过（OK (14 tests)）。

## 安装包

`app/build/outputs/apk/debug/app-debug.apk`

- 大小：12,883,389 bytes
- SHA-256：`3107123A79A7855B619C48C95D2D63BEC9790974E5F323F43293590A5E03FC8F`

示例：[resource-tree-v2.json](examples/resource-tree-v2.json)。旧 v1 示例及 B站导出工具样本保留用于兼容验证。

实现参考：[Android 安全分享文件](https://developer.android.com/training/secure-file-sharing/share-file)、[系统文档选择](https://developer.android.com/training/data-storage/shared/documents-files)。
