package routing

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
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
		route := func(host string) string {
			m := &C.Metadata{Host: host}
			for _, rule := range parsed.Rules {
				if rule.RuleType() == C.RuleSet {
					if parsed.RuleProviders[rule.Payload()].Match(m, C.RuleMatchHelper{}) {
						return rule.Adapter()
					}
				} else if ok, _ := rule.Match(m, C.RuleMatchHelper{}); ok {
					return rule.Adapter()
				}
			}
			return ""
		}
		for _, host := range []string{"chatgpt.com", "claude.ai", "muse.ai", "unknown-site-example.xyz", "google.com"} {
			if v := route(host); v != ProtectedGroup {
				t.Errorf("%s %s -> %s", level, host, v)
			}
		}
		want := "DIRECT"
		if level == "full" {
			want = ProtectedGroup
		}
		if v := route("www.baidu.com"); v != want {
			t.Errorf("%s baidu -> %s, want %s", level, v, want)
		}
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
