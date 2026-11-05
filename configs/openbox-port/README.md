# Open-Box 分流规则 → mihomo 配置模板

`openbox-mihomo.yaml` 是从路由器 Open-Box（sing-box）分流规则移植来的 mihomo 配置模板，已通过 mihomo v1.19.15 `mihomo -t` 校验。仓库里不含订阅地址、节点地址或任何密钥。

## 导入方法（ClashMetaForAndroid）

1. 复制 `openbox-mihomo.yaml`，把 `<SUBSCRIPTION_URL>` 换成你的订阅地址，`<CHANGE_ME>` 换成面板密钥（或删掉 `external-controller`/`secret`）。
2. 把文件存到手机上，在 App 里「配置 → 新建配置 → 文件」导入，然后选中它。
3. 首次启动会下载规则集（MetaCubeX meta-rules-dat 的 `.mrs`），需要能访问 GitHub。

改过的文件只留在手机上，**不要提交回仓库**。

## 分流顺序

NTP 直连 → md.gvdb.org（非日本节点）→ Dalamud 更新（日本）→ Steam 下载（直连）→ Steam 社区/商店（Steam网页节点）→ 局域网与测速（直连）→ AI 网站（非香港节点）→ 干净 IP 网站（台湾专线优先）→ Spotify（地区-日本）→ 谷歌与被墙网站（谷歌固定：日本优先，备用日本/新加坡）→ 国内网站与字节系（直连）→ 境外网站（台湾专线优先）→ 国内 IP（直连）→ 其他（台湾专线优先）。

## 偏好

- 不用 fake-ip：`enhanced-mode: redir-host`。
- 国内和字节系直连。
- AI 和谷歌不走香港/澳门：`非香港节点`、`谷歌固定` 的正则排除 香港|澳门|HK|Hong Kong|Macau。
- 不用家宽：订阅层 `exclude-filter: 家宽`，各组正则也排除；原来的「台湾家宽优先」组已删除。
- 所有组都用正则按地区筛节点，不写死节点名；同时排除「剩余/到期/官网/套餐/流量」这类信息节点。

## 已知限制

- Open-Box 的 failover 在主用恢复后会等 60 秒再切回，mihomo 的 `fallback` 没有这个设置，主用恢复后会在下一次健康检查时直接切回。
- Open-Box 按最终分流结果选 DNS，这里只能用 `respect-rules` 加 `nameserver-policy` 近似实现：gfw/google/geolocation-!cn/AI/spotify 走 1.1.1.1/8.8.8.8（经「其他」），其余走 223.5.5.5/119.29.29.29。
- 原来手工挑的节点名单（如 非香港节点 去掉了几个日本/美国/新加坡节点）改成了按地区正则，节点会比原来多。
- dnsmasq 接管、HTTPS/SVCB 屏蔽、TUN 这些路由器层的设置没有移植。
