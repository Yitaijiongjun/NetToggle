# custom17：Release APK 的 SIM 选择器修复

用户 custom16 日志显示 Shizuku 已授权、AUTO 数据卡可解析为 slot=0/subId=1，USER/effective/缓存均为优先 4G，但磁贴操作失败于 `null is unavailable`。

源代码在入口检查 target 非空，随后从枚举数组遍历目标。因此单靠入口判空或修改用户 SIM 设置不能解释该错误。

检查已发布 custom16 的实际 DEX，发现 TargetSim 已被 R8 转成整数；循环读取了数组元素，却将零/null 传给 SimIdentityResolver，并将诊断标签折叠为字符串 `null`：

```text
aget v17, v12, v13
const/4 v8, #int 0
iput v8, v7, Lf/k;.c:I
invoke-static {v7, v8}, Lb/a;.g:(Lf/k;I)Lf/j;
const-string v5, "null"
```

这一编译产物与失败日志对应。此时还未进入小米或 Android 电话 setter。现有 JVM 回归测试验证压缩前的类，未覆盖这个 Release 优化结果。

修复只保护 TargetSim 枚举与很小的 targetForStep 函数，保留其他 Release 压缩优化；切换循环改为直接选择目标，去掉枚举数组。

scripts/check_release_target.py 在最终签名 APK 的 DEX 中验证真实枚举及静态选择器，对 AUTO、SIM_1、SIM_2、BOTH 第一步、BOTH 第二步执行受限的字节码路径检查。它检查打包后的纯选择器，不执行 Android 服务或实际电话操作。custom16 会被该检查拒绝。

Release-only Actions 同时执行原有回归测试、新的选择器测试以及 APK 检查，仍只上传一个签名 APK。不连接手机，不进行本地编译。
