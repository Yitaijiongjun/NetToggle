# NetToggle — Shizuku 定制版

用于小米 HyperOS 的网络模式磁贴与桌面快捷方式。当前界面只有一页，仅使用 Shizuku 执行特权操作。

## 使用

1. 启动 Shizuku，在首页授权。
2. 选择目标 SIM（自动、SIM 1、SIM 2 或双卡）。
3. 配置磁贴循环顺序、各模式的亮起状态，以及是否自动收起控制中心。
4. 在首页底部配置、测试和固定桌面快捷方式，最多四个。

支持优先 5G、优先 4G、仅 5G、仅 4G、优先 3G 和仅 2G，选项根据 SIM 能力过滤。仅 5G 依赖 SA 网络；优先 5G 不保证始终附着 NR。

切换通过 Shizuku 包装的小米/Android 电话 Binder 执行，并回读目标 SIM 的 USER 掩码、有效网络限制和小米 5G 偏好。只有连续回读一致才判定成功，失败可在首页查看诊断信息。

Root 执行路径、外部广播控制及其配置界面、指南页面、底部/横屏导航均已移除。系统开机与应用升级接收器仅用于清理过期磁贴缓存。

## 构建与验证

GitHub Actions 执行 Release 变体的网络回归测试、单页/入口/资源检查，仅上传一个已签名的 Release APK。使用仓库已有签名密钥时可以覆盖安装同签名版本。

构建命令：`./gradlew testReleaseUnitTest assembleRelease`。

## 来源与许可

基于 [Dhangofa/NetToggle](https://github.com/Dhangofa/NetToggle)，遵循 GNU GPL v3，详见 [LICENSE](LICENSE) 与 [NOTICE.md](NOTICE.md)。

切换修复分析见 [docs/HYPEROS_5G_FIX.md](docs/HYPEROS_5G_FIX.md)；裁剪范围见 [docs/SHIZUKU_ONLY.md](docs/SHIZUKU_ONLY.md)；磁贴刷新修复见 [docs/TILE_LATENCY.md](docs/TILE_LATENCY.md)。
