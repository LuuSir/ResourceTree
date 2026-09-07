# 多选与批量操作

后续交互更新：长按已改为拖动排序，多选统一从「更多」进入。详见 [REORDER-PIN.md](REORDER-PIN.md)。以下记录批量功能最初交付时的行为与结果。

已在原有长按菜单中增加「多选」「复制」，并保留编辑、移动、删除。

## 使用方式

1. 长按文件夹或条目，点击「多选」，该节点自动勾选。
2. 点击其他行或复选框增加/取消选择；顶部支持全选与取消全选。
3. 底部点击「移动」「复制」选择目标目录，或点击「删除」确认整批删除。
4. 点击「取消」或系统返回键退出多选。搜索结果同样支持多选。

多选模式不会执行条目 Action，也不会进入文件夹。操作期间禁用重复提交；失败保留勾选，成功退出多选。单个节点也可直接通过长按菜单复制。

## 数据行为

- 复制文件夹包含全部后代；副本均分配新 UUID，保留内容、标签、动作、子节点名称及内部排序。
- 目标目录中重名时，副本根节点追加「（副本）」「（副本 2）」等后缀；没有重名则保留名称。
- 复制到原目录会生成独立副本；复制到所选文件夹的后代也只读取操作开始时的快照，不会无限复制。
- 移动禁止选择自身或后代目录作为目标；父子关系及内容保持不变。
- 删除确认显示包含后代在内的节点总数。
- 同时选中祖先与后代时，以最高层的所选祖先为单位处理一次，避免重复复制或破坏层级。
- 批量移动、复制、删除分别在一个 Room 事务中完成。任一写入失败时整批回滚。
- 数据库 schema 未改变，无需迁移。

## 修改文件

- `data/repository/NodeRepository.kt`：统一单节点与批量操作；子树去重、复制、校验及事务。
- `ui/viewmodel/BrowserViewModel.kt`：批量任务、成功回调、忙碌状态与结果反馈。
- `ui/screens/BrowserScreen.kt`：多选状态、全选、底部操作栏、目标选择和删除确认。
- `ui/components/NodeRow.kt`：复选框、勾选高亮及操作禁用状态。
- `src/test/.../RoomRepositoryTest.kt`：新增 7 项批量事务与复制测试。
- `src/androidTest/.../BatchSelectionInstrumentedTest.kt`：新增 2 项真机界面测试，使用临时内存数据库。

## 实际验证结果（2026-09-07）

| 验证 | 结果 |
| --- | --- |
| assembleDebug | SUCCESS |
| testDebugUnitTest | SUCCESS，47 项通过，0 失败 |
| assembleDebugAndroidTest | SUCCESS |
| lintDebug | SUCCESS，0 errors、24 warnings |
| 真机 AndroidJUnitRunner | OK (9 tests)，全部通过 |
| 覆盖安装 | 主 APK 与测试 APK 均返回 Success |

真机型号 `23124RN87C`，Android 13。界面测试覆盖长按多选、单选/全选/取消全选、批量复制、批量移动、删除取消与确认，以及勾选不会执行条目动作。设备侧原有 Room 测试也全部通过。

MIUI 首次后台启动测试 Activity 被限制，经用户确认并打开测试页面后完成全部测试。测试只使用临时内存数据库；覆盖安装使用 `adb install -r`，未清空或卸载现有 ResourceTree 数据。

证据：`batch-build.log`、`batch-lint.log`、`batch-device-tests.log`、`app/build/reports/tests/testDebugUnitTest/index.html`。

APK：`D:\Android\Projects\ResoureTree\app\build\outputs\apk\debug\app-debug.apk`。

SHA-256：`45EDDD44B9F804C5C1ABCAF8B06233539AFA16399E8B2C2E070E58CD041FD49B`。
