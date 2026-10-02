# custom16：自动收起后的磁贴刷新延迟

用户反馈：系统信号图标变化正常，但开启自动收起后应用磁贴经常需要反复下拉，频繁切换时更明显。

## 已确认的代码问题

1. TileActionActivity 后台操作完成后，只写入偏好缓存并调用 requestListeningState。服务未声明 ACTIVE_TILE，该 API 在此模式下不生效；可见磁贴也没有监听缓存变化，所以错过后台切换完成通知。再次下拉触发 onStartListening 才可能读到新值。
2. 网络验证每次先睡 350 ms，成功必须两次一致，因此即使配置已生效也至少固定等待 700 ms。
3. 同一轮回读多次解析 SIM，普通解析还包含运营商名称并可能回落 dumpsys isub。目标身份与运营商元数据混在切换热路径中。
4. 主线程绘制自动卡角标时也调用完整 SIM 解析，存在阻塞磁贴更新的风险。

这些是静态代码能够确认的刷新缺陷与慢路径；没有连接手机，因此没有将每一秒真实延迟归因到某条调用。

## 修复

- 在服务 listening 生命周期中监听缓存、错误状态、目标 SIM 和磁贴配置变化；变化后合并成一次主线程 updateTile 调用。重新打开面板时仍立即绘制最新缓存。
- 原子保存模式、已解析自动卡槽和回读时间。绘制角标只读取缓存，不访问电话服务或 shell。
- 新增身份专用 SimIdentityResolver：现代 Android 用 SubscriptionManager 的 slot/subId API；元数据和旧系统 fallback 保留在原样的 SimResolver 中。身份在一轮切换和验证中复用，并检查 SIM 是否被移除或替换。
- 现代小米按槽位接口不再提前查询无用的默认数据卡；旧版全局接口仍按需检查默认卡。
- 首次验证立即回读，以 100 ms 间隔要求连续两次一致，最多 51 次，保留约 5 秒等待收敛的间隔预算。没有乐观伪造目标状态。
- 移除对非 ACTIVE_TILE 无效的 requestListeningState 调用，使用可见时的缓存监听和重新显示时的缓存绘制。

## 验证

Release 变体回归测试增加“立即首读、只等待一次”和“较晚收敛仍可验证”两个用例；结构检查增加磁贴绘制不得解析 SIM、缓存监听必须注册/注销的约束。Actions 只上传一个签名 Release APK，不构建 Debug APK。

参考：[TileService 生命周期与 requestListeningState](https://developer.android.com/reference/android/service/quicksettings/TileService#requestListeningState(android.content.Context,%20android.content.ComponentName))；[SubscriptionManager 映射 API](https://developer.android.com/reference/android/telephony/SubscriptionManager#getSubscriptionId(int))。
