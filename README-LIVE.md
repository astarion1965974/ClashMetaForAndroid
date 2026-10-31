# Clash Meta Live（自用构建）

在 Android 16 / API 36 及以上的 VPN 模式下，默认用系统 Live Update 模板显示通知。ColorOS 负责将符合条件的通知渲染为胶囊和展开卡片；本构建不依赖悬浮窗、Root 或厂商私有接口。

## 使用

安装 APK 后，允许通知、导入自己的配置并连接 VPN。在“设置 → 应用”中可以切换“VPN 实时通知”、指定“实时通知主策略组”、进入系统通知权限页面，并选择是否显示流量。实时通知和流量开关修改后重新连接生效；主策略组选择立即生效。若系统没有显示胶囊，请在本应用的系统通知设置中允许实时通知 / 实时活动 / 流体云（名称取决于固件）。

Live2 采用“指定主策略组”的方案：胶囊显示精简节点名；展开卡片以完整节点名为标题，显示主策略组、上下行速率、本次累计流量与配置，并提供“节点列表”和“断开连接”。节点列表打开应用节点页并定位到主策略组（该组在当前节点页可见时）。手动选节点后立即更新，自动测速 / 故障切换在下一次采样更新：亮屏约 3 秒，息屏约 15 秒，不持有唤醒锁。布局采用系统 BigTextStyle，背景、圆角、按钮和胶囊形态由 ColorOS 决定。

全局模式沿 `GLOBAL` 递归解析节点；规则模式只沿指定主策略组解析到最终节点。默认识别“节点选择 / 代理选择 / Proxy”等常见名称，找不到时使用配置中的第一个可见组。切换配置后，若指定组不再存在，则自动回退；设置仍允许重新指定。嵌套及隐藏组可以递归解析；负载均衡仍显示“多线路分流”。这是主策略组的所选出口，其他规则可能走其他节点；本版本不采样实际连接流量来判断主要活跃节点。速率和累计值为整个代理内核的数据，不是该节点独占的流量。胶囊文字受系统宽度限制，完整名称见展开卡片。

## 编译

依照上游流程初始化子模块，准备 JDK 21、Android SDK 36、NDK 29.0.14206865、CMake 3.22.1，以及上游 workflow 使用的 MetaCubeX Go 1.26 工具链，并应用 `.github/patch`。更新 CA 文件的方式与上游 workflow 相同。

`local.properties`（不要提交）：

```properties
sdk.dir=/path/to/android-sdk
custom.application.id=com.astarion.clash.live
custom.application.name=Clash Meta Live
remove.suffix=true
```

`signing.properties`（不要提交）：

```properties
keystore.path=/private/path/clashmeta-live.jks
keystore.password=<自己的签名库密码>
key.alias=cmfa-live
key.password=<自己的私钥密码>
```

```bash
git submodule update --init --recursive
./gradlew -Pcmfa.abis=arm64-v8a -Pandroid.suppressUnsupportedCompileSdk=36 \
  service:testAlphaReleaseUnitTest app:assembleAlphaRelease
```

`cmfa.abis` 可指定以逗号分隔的架构；不指定时保持上游的四架构构建。自用包使用独立包名，可与官方版并存。持续使用相同签名库才能覆盖升级，请私下保存签名备份，不要上传到公开仓库。

## 验证范围

单元测试覆盖嵌套及隐藏策略组、不同组分流、相同节点去重、循环 / 缺失组、负载均衡、直连、自动切换、Unicode 截断，以及主策略组指定、默认识别、配置变更回退、全局模式和仅查询主策略组链。构建产物还需要在目标 ColorOS 手机上验证胶囊、展开操作、权限开关和锁屏显示。

Live Updates 参考：[Android 官方说明](https://developer.android.com/develop/ui/views/notifications/live-update)。
