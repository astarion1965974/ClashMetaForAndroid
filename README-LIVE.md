# Clash Meta Live（自用构建）

在 Android 16 / API 36 及以上的 VPN 模式下，默认用系统 Live Update 模板显示通知。ColorOS 负责将符合条件的通知渲染为胶囊和展开卡片；本构建不依赖悬浮窗、Root 或厂商私有接口。

## 使用

安装 APK 后，允许通知、导入自己的配置并连接 VPN。在“设置 → 应用”中可以切换“VPN 实时通知”、进入系统通知权限页面，并选择是否显示流量。开关修改后重新连接生效。若系统没有显示胶囊，请在本应用的系统通知设置中允许实时通知 / 实时活动 / 流体云（名称取决于固件）。

胶囊显示精简节点文字；展开卡片显示完整节点、模式、配置、策略组对应的出口、速率及累计流量，并提供断开按钮。手动选节点后立即更新，自动测速 / 故障切换在下一次采样更新：亮屏约 3 秒，息屏约 15 秒，不持有唤醒锁。

全局模式沿 `GLOBAL` 递归解析节点；规则模式列出各策略组的选择。多个组选择不同节点时显示节点数量，负载均衡显示“多线路分流”。这些是配置中的所选出口，不是对全部实时连接出口的断言。胶囊文字受系统宽度限制，完整名称见展开卡片。

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

单元测试覆盖嵌套及隐藏策略组、不同组分流、相同节点去重、循环 / 缺失组、负载均衡、直连、自动切换、Unicode 截断。构建产物还需要在目标 ColorOS 手机上验证胶囊、展开操作、权限开关和锁屏显示。

Live Updates 参考：[Android 官方说明](https://developer.android.com/develop/ui/views/notifications/live-update)。
