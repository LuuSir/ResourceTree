# ResourceTree 0.6

一款本地快捷资源管理器。

## 使用

[B站教程视频](https://www.bilibili.com/video/BV1dQYs6JEbT/?vd_source=6076e72eb03a33f77c50c30c9947e961)

## 悬浮按钮

在「菜单 → 悬浮按钮」中开启，并允许系统的「显示在其他应用上层」权限。Android 13 及以上还会询问通知权限，用于显示服务通知与关闭入口；拒绝通知权限仍可从应用菜单关闭。

设置页中的滑杆支持 36–96 dp，默认 56 dp；调整会立即作用于正在显示的按钮，关闭后再次开启仍保留，可点击「恢复默认大小」。

在其他应用复制文字后，点击悬浮的文件树按钮返回 ResourceTree 首页。应用获得前台焦点后按现有剪贴板规则预填新条目，仍需确认保存；不匹配规则的内容不会创建条目。拖动按钮可以改变位置，拖动不会触发打开。若当前有未保存的条目编辑，会先询问是否放弃。

通过菜单或服务通知可关闭按钮。部分手机需要额外允许「后台弹出界面」。服务被系统停止或手机重启后，需在菜单重新开启。

## 构建与签名

使用 Android Studio 配套 JBR 和项目 Gradle Wrapper。运行 `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug`；连接设备后可运行 `connectedDebugAndroidTest`。

发布构建使用 `assembleRelease lintRelease`。签名通过环境变量配置：`RESOURCETREE_SIGNING_STORE`、`RESOURCETREE_SIGNING_STORE_PASSWORD`、`RESOURCETREE_SIGNING_ALIAS`、`RESOURCETREE_SIGNING_KEY_PASSWORD`。不配置时仅生成未签名 release APK，不将密钥提交到仓库。

v0.3 为兼容已发布的 v0.2，沿用原安装包的签名证书，版本号升级为 0.3（versionCode 3）。本次不修改 Room 版本或数据库结构；可直接覆盖更新，无需卸载。历史签名证书是 Android Debug 证书，后续发行须保留同一密钥，不能直接换签名覆盖安装。

## 图视角与新图标

点击左上角 ResourceTree 标题切换列表／图视角。图中以当前目录为中心，默认只显示直属节点；点击文件夹进入下一层，点击条目执行已有动作，面包屑可返回任意上级。「展开全部」显示当前目录以下所有后代。

双指缩放、单指平移，底部提供放大、缩小和「适应画布」。节点位置按目录层级及原有置顶／排序确定，不会持续漂移。小比例时隐藏文字，放大后可查看名称。编辑、拖拽排序和批量管理继续在列表进行。

图标采用米白与暖橙的文件树设计；桌面、自适应形状、单色主题图标和悬浮入口保持一致。矢量源文件见 `design/resourcetree-icon.svg`，预览见 `design/icon-variants.png`。

v0.4（versionCode 4）沿用之前版本的签名和数据库结构，可直接覆盖安装。没有连接设备时，只执行本地测试与测试包编译，不将其标记为真机验证。

## 连续图过渡与正式发布

图视角进入文件夹、返回上级或切换「展开全部」时保留当前画布，节点位置、连线、透明度和视口同步过渡。快速进入／返回从当前显示帧衔接，点击坐标与显示位置一致。

发布安装包使用非调试 Release 构建，沿用原签名；发布工具 `tools/publish_release.py` 会拒绝 Debug APK、签名不一致、版本不匹配或损坏的 ZIP，并验证远端分支／标签和上传文件摘要。

本地稿件不属于公开源码，已移出公开 Git 历史并加入忽略规则。清理历史后旧版本标签的提交哈希有所改变，已有 APK 的签名与安装数据不受影响。

完整本地验证命令：`assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug assembleRelease lintRelease`。设备回归可运行常规 Android 测试；数据保留检查默认跳过，需明确传入 `preservationMode=snapshot` 或 `compare`。比较模式还需提供快照返回的四个字段：`snapshotNodes`、`snapshotTree`、`snapshotRules`、`snapshotSchema`。

2026-10-08 已完成 Android 13 真机回归：22 项通过，手动升级检查默认跳过；覆盖安装正式 Release 后显式数据比较另行通过。图过渡测试使用独立资源，不写入现有资源树。MIUI 可能阻挡测试宿主的后台页面启动，测试时需允许该行为，结束后恢复原设置。详细结果见 `BUILD-RESULTS.md`。
