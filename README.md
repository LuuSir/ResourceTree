# ResourceTree 0.3

一款本地快捷资源管理器。

## 使用

[B站教程视频](https://www.bilibili.com/video/BV1dQYs6JEbT/?vd_source=6076e72eb03a33f77c50c30c9947e961)

## 悬浮按钮

在「菜单 → 悬浮按钮」中开启，并允许系统的「显示在其他应用上层」权限。Android 13 及以上还会询问通知权限，用于显示服务通知与关闭入口；拒绝通知权限仍可从应用菜单关闭。

在其他应用复制文字后，点击悬浮的「树」按钮返回 ResourceTree 首页。应用获得前台焦点后按现有剪贴板规则预填新条目，仍需确认保存；不匹配规则的内容不会创建条目。拖动按钮可以改变位置，拖动不会触发打开。若当前有未保存的条目编辑，会先询问是否放弃。

通过菜单或服务通知可关闭按钮。部分手机需要额外允许「后台弹出界面」。服务被系统停止或手机重启后，需在菜单重新开启。

## 构建与签名

使用 Android Studio 配套 JBR 和项目 Gradle Wrapper。运行 `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug`；连接设备后可运行 `connectedDebugAndroidTest`。

发布构建使用 `assembleRelease lintRelease`。签名通过环境变量配置：`RESOURCETREE_SIGNING_STORE`、`RESOURCETREE_SIGNING_STORE_PASSWORD`、`RESOURCETREE_SIGNING_ALIAS`、`RESOURCETREE_SIGNING_KEY_PASSWORD`。不配置时仅生成未签名 release APK，不将密钥提交到仓库。

v0.3 为兼容已发布的 v0.2，沿用原安装包的签名证书，版本号升级为 0.3（versionCode 3）。本次不修改 Room 版本或数据库结构；可直接覆盖更新，无需卸载。历史签名证书是 Android Debug 证书，后续发行须保留同一密钥，不能直接换签名覆盖安装。
