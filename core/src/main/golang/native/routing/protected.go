package routing

import (
	"fmt"
	"path/filepath"
	"strings"

	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	T "github.com/metacubex/mihomo/tunnel"
)

// The public selector contains only two internally generated safe groups.
// Each child excludes DIRECT and every other nested group, failing to REJECT.
const ProtectedGroup = "Live · 严格代理"
const ProtectedManual = "Live · 严格手动"
const ProtectedAuto = "Live · 严格自动"
const excludedAdapterTypes = "Direct|Reject|RejectDrop|Compatible|Pass|PassRule|Rematch|Dns|Relay|Selector|Fallback|URLTest|LoadBalance"

// A small list of domestic services supplements the maintained CN domain set.
// Neither this list nor upstream categorization proves the ownership of a host.
var verifiedCN = []string{
	"baidu.com", "bdstatic.com", "bilibili.com", "bilivideo.com",
	"biliimg.com", "douyin.com", "douyinvod.com", "douyinpic.com", "toutiao.com",
	"qq.com", "gtimg.com", "weixin.qq.com", "taobao.com", "tmall.com", "alipay.com",
	"jd.com", "360buyimg.com", "dianping.com", "meituan.com", "meituan.net",
	"163.com", "126.com", "sina.com.cn", "weibo.com", "zhihu.com", "zhimg.com",
}

func applyProtected(cfg *config.RawConfig, s Settings, cacheDir string) error {
	for _, d := range append(append([]string{}, s.DirectDomains...), s.ProxyDomains...) {
		if !ValidDomain(d) {
			return fmt.Errorf("invalid exception domain: %q", d)
		}
	}
	for _, p := range append(append([]map[string]any{}, cfg.Proxy...), cfg.ProxyGroup...) {
		if n, _ := p["name"].(string); n == ProtectedGroup || n == ProtectedManual || n == ProtectedAuto {
			return fmt.Errorf("reserved managed name: %s", n)
		}
	}
	cfg.ProxyGroup = append(cfg.ProxyGroup,
		map[string]any{"name": ProtectedManual, "type": "select", "include-all-proxies": true,
			"include-all-providers": true, "exclude-type": excludedAdapterTypes, "empty-fallback": "REJECT"},
		map[string]any{"name": ProtectedAuto, "type": "fallback", "include-all-proxies": true,
			"include-all-providers": true, "exclude-type": excludedAdapterTypes, "empty-fallback": "REJECT",
			"url": "https://www.gstatic.com/generate_204", "expected-status": "204", "interval": 600,
			"lazy": true, "timeout": 5000, "max-failed-times": 3},
		map[string]any{"name": ProtectedGroup, "type": "select", "proxies": []string{ProtectedAuto, ProtectedManual},
			"empty-fallback": "REJECT"},
	)
	if cfg.RuleProvider == nil {
		cfg.RuleProvider = map[string]map[string]any{}
	}
	for _, r := range Sets {
		// Domestic domains use a maintained ruleset in strict mode, after
		// known foreign services; a China IP alone is never a direct signal.
		if r.Name == "cn-ip" || (r.Name == "cn" && s.Protection != "strict") || (r.Category == "ads" && !s.BlockAds) {
			continue
		}
		name := Prefix + r.Name
		if r.Name == "cn" {
			name = Prefix + "cn-compat"
		}
		if _, exists := cfg.RuleProvider[name]; exists {
			return fmt.Errorf("reserved managed provider: %s", name)
		}
		cfg.RuleProvider[name] = map[string]any{"type": "http", "format": "mrs", "behavior": r.Behavior,
			"url": Source + r.Path, "path": filepath.Join(cacheDir, r.Name+".mrs"), "interval": 86400, "proxy": ProtectedGroup}
	}
	rules := []string{"RULE-SET,live-private-domain,DIRECT", "RULE-SET,live-private-ip,DIRECT,no-resolve"}
	if s.BlockAds {
		rules = append(rules, "RULE-SET,live-ads,REJECT")
	}
	for _, d := range s.ProxyDomains {
		rules = append(rules, "DOMAIN-SUFFIX,"+strings.ToLower(d)+","+ProtectedGroup)
	}
	rules = append(rules, claudeRules(ProtectedGroup)...)
	rules = append(rules, museRules(ProtectedGroup)...)
	for _, n := range []string{"openai", "anthropic", "gemini", "media", "telegram", "twitter", "facebook", "github", "dev", "foreign"} {
		rules = append(rules, "RULE-SET,live-"+n+","+ProtectedGroup)
	}
	direct := []string{}
	if s.Protection == "strict" {
		direct = append(direct, verifiedCN...)
		direct = append(direct, s.DirectDomains...)
		// The maintained domain set covers domestic application dependencies;
		// protected overseas categories above always take precedence.
		rules = append(rules, "RULE-SET,live-cn-compat,DIRECT")
		for _, d := range direct {
			rules = append(rules, "DOMAIN-SUFFIX,"+strings.ToLower(d)+",DIRECT")
		}
	}
	cfg.Rule = append(rules, "MATCH,"+ProtectedGroup)
	cfg.SubRules = nil
	cfg.Mode = T.Rule

	// DNS is independent of subscription resolvers. Node bootstrap is kept
	// separate to avoid routing its own lookup through an unavailable proxy.
	cfg.DNS.Enable = true
	cfg.DNS.IPv6 = true
	cfg.DNS.EnhancedMode = C.DNSFakeIP
	cfg.DNS.FakeIPRange = "198.18.0.1/16"
	cfg.DNS.FakeIPRange6 = "fc00::/18"
	cfg.DNS.FakeIPFilterMode = C.FilterBlackList
	cfg.DNS.FakeIPFilter = []string{"rule-set:live-private-domain"}
	cfg.DNS.DefaultNameserver = []string{"223.5.5.5", "119.29.29.29"}
	cfg.DNS.ProxyServerNameserver = []string{"https://dns.alidns.com/dns-query#DIRECT"}
	cfg.DNS.ProxyServerNameserverPolicy = nil
	cfg.DNS.NameServer = []string{"https://1.1.1.1/dns-query#" + ProtectedGroup}
	cfg.DNS.Fallback = nil
	cfg.DNS.RespectRules = false // explicit outbound on each resolver
	cfg.DNS.NameServerPolicy = orderedmap.New[string, any]()
	cfg.DNS.NameServerPolicy.Set("rule-set:live-private-domain", []string{"system://"})
	proxyDNS := []string{"https://1.1.1.1/dns-query#" + ProtectedGroup}
	for _, domain := range append(append(append([]string{}, s.ProxyDomains...), claudeSuffixes...), museSuffixes...) {
		cfg.DNS.NameServerPolicy.Set("+."+strings.ToLower(domain), proxyDNS)
	}
	for _, domain := range append(append([]string{}, claudeExactDomains...), museExactDomains...) {
		cfg.DNS.NameServerPolicy.Set(strings.ToLower(domain), proxyDNS)
	}
	// Overseas policies must win over a suffix in the direct list.
	for _, n := range []string{"openai", "anthropic", "gemini", "media", "telegram", "twitter", "facebook", "github", "dev", "foreign"} {
		cfg.DNS.NameServerPolicy.Set("rule-set:live-"+n, []string{"https://1.1.1.1/dns-query#" + ProtectedGroup})
	}
	if s.Protection == "strict" {
		cfg.DNS.NameServerPolicy.Set("rule-set:live-cn-compat", []string{"https://dns.alidns.com/dns-query#DIRECT", "https://doh.pub/dns-query#DIRECT"})
	}
	proxyOnly := map[string]bool{}
	for _, d := range append(append(append([]string{}, s.ProxyDomains...), claudeSuffixes...), museSuffixes...) {
		proxyOnly[strings.ToLower(d)] = true
	}
	for _, d := range direct {
		d = strings.ToLower(d)
		// Do not replace an explicitly protected policy with a direct DNS
		// policy when the same suffix appears in both lists.
		if proxyOnly[d] {
			continue
		}
		cfg.DNS.NameServerPolicy.Set("+."+d, []string{"https://dns.alidns.com/dns-query#DIRECT", "https://doh.pub/dns-query#DIRECT"})
	}
	cfg.DNS.DirectNameServer = nil
	cfg.DNS.DirectNameServerFollowPolicy = false
	cfg.DNS.UseSystemHosts = false
	cfg.DNS.UseHosts = false
	cfg.Sniffer.Enable = true
	cfg.Sniffer.ParsePureIp = true
	cfg.Sniffer.OverrideDest = false
	cfg.Sniffer.Sniff = map[string]config.RawSniffingConfig{
		"HTTP": {Ports: []string{"80", "8080-8880"}},
		"TLS":  {Ports: []string{"443", "8443"}},
		"QUIC": {Ports: []string{"443", "8443"}},
	}
	return nil
}
