# custom15：Shizuku 单页裁剪

## 保留的产品范围

Shizuku 授权、SIM 选择、网络能力过滤、磁贴循环/亮起状态/自动收起、桌面快捷方式、主题和错误诊断。首页只保留一个滚动容器；快捷方式放在磁贴配置下面，不再使用悬浮按钮或分页导航。

## 实际删除

- Root 命令执行器、现代/旧版控制器、app_process payload、Root 状态检查、Root 选择控件和相关资源。
- 外部 AutomationReceiver、广播页面、token/开关/API、广播请求/结果对象和整个 automation 包。
- 指南页面、指南 helper、指南卡片、指南专用图标、三页导航及横屏导航。
- 无引用的资源文件和各语言废弃字符串。快捷方式使用的共享样式改为 shortcut 命名，保留其外观。

BootReceiver 只处理系统开机和应用更新以清理旧缓存，仍为非导出接收器。ShortcutActionActivity 是桌面快捷方式的必要入口，与外部广播控制无关。

## 缩小定制差异

- NetworkMode 模型和 SimResolver 恢复上游原样。
- OEM 网络掩码解析留在新增的 NetworkModeReadback 中。
- NetworkModeController 只负责 Shizuku 分发；验证、双卡协调、错误记录集中在新的 NetworkActionExecutor。它仅接受应用内部和快捷方式调用，没有外部广播 API。
- 删除上一版主页 onResume 的额外模式回读；授权、SIM UI、能力过滤、主题切换和快捷方式编辑尽量沿用既有逻辑。
- 保留实现真正切换所必需的按槽位小米 Binder、Android USER 掩码及连续回读验证。没有恢复伪造 carrier-change 广播的旧图标刷新逻辑。
- 保留既有 SIM/循环/快捷方式偏好，仅清理已退役的执行模式、广播开关和 token；历史 Root 错误状态会解除。

## 验证范围

scripts/check_surface.py 校验应用入口、非导出 BootReceiver、唯一滚动页面、快捷方式位置、无 Root/app_process 执行及资源引用完整性。GitHub Actions 同时运行 Release 变体的 11 项网络回归测试和 Release 构建，仅上传一个签名 APK。不连接手机，不进行本地编译；实际 ROM 显示效果仍需安装后确认。
