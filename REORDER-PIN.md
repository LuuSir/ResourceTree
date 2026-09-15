# 长按拖动排序与置顶

- 在目录内长按文件夹或条目，上下拖动，松手保存排序，不显示成功 Snackbar。
- 拖动项从列表内容中提取到独立悬浮层，列表保留目标位置空位；其他行使用独立 spring placement 动画让位，松手使用弹簧动画落位。长按给出触觉反馈，靠近上下边缘自动滚动。
- 拖动期间暂停普通列表滚动，避免排序手势被滚动手势取消。取消手势不保存变更。
- 「更多」菜单新增「置顶／取消置顶」，保留编辑、多选、移动、复制和删除。
- 置顶项显示「已置顶」，始终排在普通项前面；两组分别支持拖动排序。
- 新置顶的节点排在置顶组最前面，取消置顶后排在普通组最前面。
- 多选从「更多」进入；搜索结果和多选模式不提供拖动排序。搜索时长按不会误执行条目动作。
- 所有排序只影响当前目录的直接子节点，不改变目录归属或资源内容。

## 数据与兼容

Room 数据库升级为版本 2，通过 `MIGRATION_1_2` 增加 `isPinned INTEGER NOT NULL DEFAULT 0`，保留已有节点、动作、标签和初始化记录。

排序使用 `isPinned DESC, sortOrder, createdAt, id`。拖动排序和置顶更新在事务中完成；目录内容发生变化、顺序包含重复 ID、跨目录或破坏置顶分组时会拒绝保存。

JSON 继续使用 schemaVersion 1，增加可选布尔字段 `isPinned`；旧文件省略该字段时默认为 false，新文件导入导出保留置顶和手动顺序。

## 相关文件

- `ui/components/ReorderableNodeList.kt`：长按识别、拖动预览、边缘滚动与松手提交。
- `ui/components/NodeRow.kt`：移除长按菜单，显示置顶状态和独立「更多」入口。
- `ui/screens/BrowserScreen.kt`：接入排序列表及置顶菜单。
- `ui/viewmodel/BrowserViewModel.kt`：保存排序和切换置顶。
- `data/repository/NodeRepository.kt`：排序校验和置顶事务。
- `domain/model/ResourceNode.kt`、`data/local/entity/NodeEntity.kt`：置顶字段及排序规则。
- `data/local/dao/NodeDao.kt`、`data/local/database/ResourceDatabase.kt`、`ResourceTreeApplication.kt`：查询规则和无损迁移。
- `data/transfer/TreeJson.kt`：置顶字段 JSON 往返。
- `app/schemas/.../2.json`：新的 Room schema，版本 1 快照保留。

## 验证覆盖

本地测试覆盖旧数据库迁移、排序持久化、置顶分组与取消置顶、非法/过期排序拒绝、JSON 往返和实际长按拖动手势。设备测试使用临时内存数据库验证拖动、置顶切换、多选及批量操作，不修改用户资源。
