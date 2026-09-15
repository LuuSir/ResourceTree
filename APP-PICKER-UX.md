# 应用选择器与交互优化

验证日期：2026-09-07。

## 使用变化

- 新建和编辑条目选择「打开 App」或「复制并打开 App」时，通过「目标应用」打开选择器。
- 列出手机中可启动应用的图标与名称，支持按名称或包名搜索；点选即返回表单，保存时自动记录包名。用户不再手填包名。
- 提供加载、空结果、读取失败、重试和刷新状态。编辑已有条目时保留原目标；如果目标应用已卸载，会显示不可用并允许重新选择。
- 应用列表在后台加载，按本地语言排序、按包名去重，只在本机使用。
- 长按拖动使用独立悬浮层；目标位置保留空位，其他行使用各自的弹簧位置动画让位，松手后悬浮项平滑落位。保留边缘滚动、置顶分组和「更多」多选。
- 排序、置顶、批量操作、导入导出及条目动作成功后不再显示应用内黑色 Snackbar。失败仍提示原因。Android/MIUI 自带的剪贴板提示不属于应用内提示。

## 应用列表访问

Manifest 使用 MAIN/LAUNCHER `<queries>` 声明查询可启动应用所需的包可见性；无需 `QUERY_ALL_PACKAGES`。标准 Android 没有单独的运行时「读取应用列表」权限弹窗，因此不伪造权限请求。如果手机厂商额外询问应用列表访问，允许后可刷新。真机测试已确认当前手机能返回多个可启动应用及图标。

参考：[Android 包可见性声明](https://developer.android.com/training/package-visibility/declaring)、[Compose 列表动画](https://developer.android.com/develop/ui/compose/lists)。

## 验证

- 最终构建：`:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug` 成功，见 `picker-final-build.log`。
- 53 项本地测试全部通过。新增应用搜索/选择/错误重试测试；拖动测试同时检查悬浮层和两行在弹簧动画中途的位置，验证没有直接跳到终点。
- 12 项 Android 13 真机测试全部通过，见 `picker-device-tests.log`。覆盖真实应用列表查询、搜索选择后保存启动目标、拖动排序、置顶切换、多选及批量移动/复制/删除、成功反馈静默与错误提示，以及 Room 数据操作。
- 图标组件的 Modifier 默认值规范修正后，再次完成全部本地检查，并对最终安装包复验应用选择流程，见 `picker-final-device-test.log`。
- Lint：0 errors，24 warnings；剩余项为现有依赖/模板建议、冗余 label，以及多选状态使用每次替换的 ArrayList 引发的静态检查提示。
- 设备测试使用临时内存数据库；覆盖安装保留用户资源，未卸载应用或清除其数据。

## 最终 APK

`app/build/outputs/apk/debug/app-debug.apk`

- applicationId：`com.example.resouretree`
- 文件大小：12,760,080 bytes
- SHA-256：`1F361E73B4EAA32FB7716398B295D38167CE2D80974AEA37E017106418E7BAFC`

相关实现：`data/apps/AppCatalog.kt`、`ui/components/AppPicker.kt`、`ui/screens/EditorScreen.kt`、`ui/viewmodel/EditorViewModel.kt`、`ui/components/ReorderableNodeList.kt`、`ui/viewmodel/BrowserViewModel.kt`。
