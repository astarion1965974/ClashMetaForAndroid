package routing

import (
	"net/netip"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"cfa/native/diagnostic"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	T "github.com/metacubex/mihomo/tunnel"
)

func TestProtectedRoutesRejectUnsafeTargets(t *testing.T) {
	for _, level := range []string{"strict", "full"} {
		c := original()
		c.DNS.Enable = true
		err := Apply(c, Settings{Enabled: true, Protection: level, BlockAds: true,
			Targets:       map[string]string{"fallback": "DIRECT", "ai": "DIRECT", "foreign": "Subscription group"},
			DirectDomains: []string{"my-domestic.example"}, ProxyDomains: []string{"always-proxy.example"}}, t.TempDir())
		if err != nil {
			t.Fatal(err)
		}
		joined := strings.Join(c.Rule, "\n")
		for _, expected := range []string{"RULE-SET,live-private-ip,DIRECT,no-resolve", "DOMAIN-SUFFIX,always-proxy.example," + ProtectedGroup, "RULE-SET,live-foreign," + ProtectedGroup, "MATCH," + ProtectedGroup} {
			if !strings.Contains(joined, expected) {
				t.Errorf("%s missing %s", level, expected)
			}
		}
		if strings.Contains(joined, "live-cn-ip") || strings.Contains(joined, ",Subscription group") {
			t.Fatal(joined)
		}
		if (level == "strict") != strings.Contains(joined, "DOMAIN-SUFFIX,my-domestic.example,DIRECT") {
			t.Fatal(joined)
		}
		if c.DNS.EnhancedMode != C.DNSFakeIP || !c.DNS.IPv6 || len(c.DNS.NameServer) != 1 || !strings.HasSuffix(c.DNS.NameServer[0], "#"+ProtectedGroup) {
			t.Fatalf("unsafe DNS: %+v", c.DNS)
		}
		if c.DNS.NameServerPolicy == nil || len(c.DNS.ProxyServerNameserver) == 0 {
			t.Fatal("missing DNS policies or bootstrap")
		}
		for _, group := range c.ProxyGroup[len(c.ProxyGroup)-3 : len(c.ProxyGroup)-1] {
			if group["empty-fallback"] != "REJECT" || !strings.Contains(group["exclude-type"].(string), "Direct") {
				t.Fatal(group)
			}
		}
		outer := c.ProxyGroup[len(c.ProxyGroup)-1]
		if !reflect.DeepEqual(outer["proxies"], []string{ProtectedAuto, ProtectedManual}) {
			t.Fatal(outer)
		}
	}
}

func TestProtectedProxyGroupFiltersDirectAndReject(t *testing.T) {
	c := original()
	c.Proxy = append(c.Proxy, map[string]any{"name": "bad-direct", "type": "direct"})
	home := t.TempDir()
	C.SetHomeDir(home)
	if err := Apply(c, Settings{Enabled: true, Protection: "strict"}, home); err != nil {
		t.Fatal(err)
	}
	filtered := c.Rule[:0]
	for _, r := range c.Rule {
		if !strings.HasPrefix(r, "IP-ASN,") {
			filtered = append(filtered, r)
		}
	}
	c.Rule = filtered
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	p := parsed.Proxies[ProtectedManual]
	if p == nil {
		t.Fatal("missing protected group")
	}
	for _, name := range []string{"Test node", "bad-direct"} {
		selector, ok := p.Adapter().(interface{ Set(string) error })
		if !ok {
			t.Fatal("not selector")
		}
		err = selector.Set(name)
		if (name == "Test node") != (err == nil) {
			t.Fatalf("selection %s err %v", name, err)
		}
	}
}

func TestProtectedBundledRuleRouting(t *testing.T) {
	defer T.UpdateRules(nil, nil, nil)
	assets, err := filepath.Abs("../../../assets/managed-rules")
	if err != nil {
		t.Fatal(err)
	}
	home := t.TempDir()
	C.SetHomeDir(home)
	for _, set := range Sets {
		source, err := os.ReadFile(filepath.Join(assets, set.Name+".mrs"))
		if err != nil {
			t.Fatal(err)
		}
		if err = os.WriteFile(filepath.Join(home, set.Name+".mrs"), source, 0600); err != nil {
			t.Fatal(err)
		}
	}
	for _, level := range []string{"strict", "full"} {
		c := original()
		if err = Apply(c, Settings{Enabled: true, Protection: level}, home); err != nil {
			t.Fatal(err)
		}
		filtered := c.Rule[:0]
		for _, r := range c.Rule {
			if !strings.HasPrefix(r, "IP-ASN,") {
				filtered = append(filtered, r)
			}
		}
		c.Rule = filtered
		parsed, err := config.ParseRawConfig(c)
		if err != nil {
			t.Fatal(err)
		}
		for _, p := range parsed.RuleProviders {
			if err = p.Initial(); err != nil {
				t.Fatal(err)
			}
			if closer, ok := p.(interface{ Close() error }); ok {
				defer closer.Close()
			}
		}
		// Rule-set DNS matchers consult the active tunnel provider registry.
		T.UpdateRules(parsed.Rules, parsed.SubRules, parsed.RuleProviders)
		route := func(m *C.Metadata) string {
			for _, rule := range parsed.Rules {
				if rule.RuleType() == C.RuleSet {
					if parsed.RuleProviders[rule.Payload()].Match(m, C.RuleMatchHelper{}) {
						return rule.Adapter()
					}
				} else if matched, _ := rule.Match(m, C.RuleMatchHelper{}); matched {
					return rule.Adapter()
				}
			}
			return ""
		}
		for _, host := range []string{"chatgpt.com", "api.openai.com", "claude.ai", "meta.ai", "grok.com", "google.com", "unknown-site-example.xyz"} {
			if got := route(&C.Metadata{Host: host}); got != ProtectedGroup {
				t.Errorf("%s %s route=%s", level, host, got)
			}
			if got := diagnostic.InspectDNSPolicy(parsed.DNS, host).Route; got != "proxy" {
				t.Errorf("%s %s DNS=%s", level, host, got)
			}
		}
		domestic := []string{"www.baidu.com", "api.bilibili.com", "wx.qlogo.cn", "mmbiz.qpic.cn", "www.xiaohongshu.com", "sns-img-bd.xhscdn.com", "www.pinduoduo.com", "www.sankuai.com", "img.alicdn.com", "www.ctrip.com", "www.12306.cn"}
		want, dnsWant := "DIRECT", "direct"
		if level == "full" {
			want, dnsWant = ProtectedGroup, "proxy"
		}
		for _, host := range domestic {
			if got := route(&C.Metadata{Host: host}); got != want {
				t.Errorf("%s %s route=%s want=%s", level, host, got, want)
			}
			if got := diagnostic.InspectDNSPolicy(parsed.DNS, host).Route; got != dnsWant {
				t.Errorf("%s %s DNS=%s want=%s", level, host, got, dnsWant)
			}
		}
		// A public address, including a well-known CN resolver, is never
		// made direct solely by the country of its IP address.
		for _, ip := range []string{"223.5.5.5", "114.114.114.114", "8.8.8.8"} {
			if got := route(&C.Metadata{DstIP: netip.MustParseAddr(ip)}); got != ProtectedGroup {
				t.Errorf("%s IP %s route=%s", level, ip, got)
			}
		}
		if got := route(&C.Metadata{DstIP: netip.MustParseAddr("192.168.1.1")}); got != "DIRECT" {
			t.Errorf("%s private IP route=%s", level, got)
		}
		T.UpdateRules(nil, nil, nil)
	}
}

func TestProtectedGroupRejectsWhenOnlyDirectIsAvailable(t *testing.T) {
	home := t.TempDir()
	C.SetHomeDir(home)
	c := original()
	c.Proxy = []map[string]any{{"name": "only-direct", "type": "direct"}}
	c.ProxyGroup = nil
	if err := Apply(c, Settings{Enabled: true, Protection: "strict"}, home); err != nil {
		t.Fatal(err)
	}
	filtered := c.Rule[:0]
	for _, r := range c.Rule {
		if !strings.HasPrefix(r, "IP-ASN,") {
			filtered = append(filtered, r)
		}
	}
	c.Rule = filtered
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	p := parsed.Proxies[ProtectedManual]
	if p == nil {
		t.Fatal("no protected group")
	}
	selected := p.Unwrap(&C.Metadata{}, false)
	if selected == nil || selected.Type() != C.Reject {
		t.Fatalf("empty protected group must reject, got %#v", selected)
	}
}

func TestProtectedExplicitProxyKeepsProxyDNS(t *testing.T) {
	c := original()
	if err := Apply(c, Settings{Enabled: true, Protection: "strict", ProxyDomains: []string{"baidu.com"}}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	value, ok := c.DNS.NameServerPolicy.Get("+.baidu.com")
	servers, good := value.([]string)
	if !ok || !good || len(servers) != 1 || !strings.HasSuffix(servers[0], "#"+ProtectedGroup) {
		t.Fatalf("proxy exception DNS = %#v", value)
	}
}

func TestProtectedProviderFiltersDirectAndEmptyProviderRejects(t *testing.T) {
	for _, onlyDirect := range []bool{true, false} {
		home := t.TempDir()
		C.SetHomeDir(home)
		c := original()
		c.Proxy = nil
		c.ProxyGroup = nil
		nodes := []map[string]any{{"name": "provider-direct", "type": "direct"}}
		if !onlyDirect {
			nodes = append(nodes, map[string]any{"name": "provider-proxy", "type": "socks5", "server": "127.0.0.1", "port": 1080})
		}
		c.ProxyProvider = map[string]map[string]any{"Airport": {"type": "inline", "payload": nodes}}
		if err := Apply(c, Settings{Enabled: true, Protection: "strict"}, home); err != nil {
			t.Fatal(err)
		}
		filtered := c.Rule[:0]
		for _, r := range c.Rule {
			if !strings.HasPrefix(r, "IP-ASN,") {
				filtered = append(filtered, r)
			}
		}
		c.Rule = filtered
		parsed, err := config.ParseRawConfig(c)
		if err != nil {
			t.Fatal(err)
		}
		if err = parsed.Providers["Airport"].Initial(); err != nil {
			t.Fatal(err)
		}
		outer := parsed.Proxies[ProtectedGroup]
		initial := outer.Unwrap(&C.Metadata{}, false)
		if initial == nil || initial.Name() != ProtectedAuto {
			t.Fatal("automatic group must be the default")
		}
		choose, ok := outer.Adapter().(interface{ Set(string) error })
		if !ok {
			t.Fatal("outer not selectable")
		}
		if err = choose.Set(ProtectedAuto); err != nil {
			t.Fatal(err)
		}
		if choose.Set("provider-direct") == nil {
			t.Fatal("outer accepts untrusted direct")
		}
		auto := parsed.Proxies[ProtectedAuto]
		list, ok := auto.Adapter().(interface{ GetProxies(bool) []C.Proxy })
		if !ok {
			t.Fatal("automatic group missing proxies")
		}
		for _, candidate := range list.GetProxies(false) {
			if candidate.Type() == C.Direct {
				t.Fatal("auto group contains direct")
			}
		}
		if onlyDirect {
			selected := auto.Unwrap(&C.Metadata{}, false)
			if selected == nil || selected.Type() != C.Reject {
				t.Fatal("automatic group must reject if only direct exists")
			}
		}
		p := parsed.Proxies[ProtectedManual]
		selector, ok := p.Adapter().(interface{ Set(string) error })
		if !ok {
			t.Fatal("not selector")
		}
		if selector.Set("provider-direct") == nil {
			t.Fatal("provider direct is selectable")
		}
		if onlyDirect {
			selected := p.Unwrap(&C.Metadata{}, false)
			if selected == nil || selected.Type() != C.Reject {
				t.Fatal("provider direct-only must reject")
			}
		} else if err = selector.Set("provider-proxy"); err != nil {
			t.Fatal(err)
		}
	}
}

func TestProtectedForeignAndExplicitProxyPrecedeDomesticOverlap(t *testing.T) {
	assets, err := filepath.Abs("../../../assets/managed-rules")
	if err != nil {
		t.Fatal(err)
	}
	home := t.TempDir()
	C.SetHomeDir(home)
	for _, set := range Sets {
		data, e := os.ReadFile(filepath.Join(assets, set.Name+".mrs"))
		if e != nil {
			t.Fatal(e)
		}
		if e = os.WriteFile(filepath.Join(home, set.Name+".mrs"), data, 0600); e != nil {
			t.Fatal(e)
		}
	}
	c := original()
	if err = Apply(c, Settings{Enabled: true, Protection: "strict", ProxyDomains: []string{"baidu.com"}, DirectDomains: []string{"google.com"}}, home); err != nil {
		t.Fatal(err)
	}
	// Force an overlap: CN also claims a protected overseas and an explicit
	// proxy domain. Neither route nor DNS may be redirected to DIRECT.
	c.RuleProvider["live-cn-compat"] = map[string]any{"type": "inline", "behavior": "domain", "payload": []string{"+.baidu.com", "+.google.com"}}
	filtered := c.Rule[:0]
	for _, r := range c.Rule {
		if !strings.HasPrefix(r, "IP-ASN,") {
			filtered = append(filtered, r)
		}
	}
	c.Rule = filtered
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	for _, p := range parsed.RuleProviders {
		if err = p.Initial(); err != nil {
			t.Fatal(err)
		}
		if closer, ok := p.(interface{ Close() error }); ok {
			defer closer.Close()
		}
	}
	T.UpdateRules(parsed.Rules, parsed.SubRules, parsed.RuleProviders)
	defer T.UpdateRules(nil, nil, nil)
	for _, host := range []string{"www.baidu.com", "google.com", "www.google.com"} {
		m := &C.Metadata{Host: host}
		got := ""
		for _, rule := range parsed.Rules {
			matched := false
			if rule.RuleType() == C.RuleSet {
				matched = parsed.RuleProviders[rule.Payload()].Match(m, C.RuleMatchHelper{})
			} else {
				matched, _ = rule.Match(m, C.RuleMatchHelper{})
			}
			if matched {
				got = rule.Adapter()
				break
			}
		}
		if got != ProtectedGroup {
			t.Errorf("%s route=%s", host, got)
		}
		if dns := diagnostic.InspectDNSPolicy(parsed.DNS, host); dns.Route != "proxy" {
			t.Errorf("%s DNS=%+v", host, dns)
		}
	}
}
