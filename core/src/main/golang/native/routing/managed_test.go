package routing

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	_ "github.com/metacubex/mihomo/hub/executor"
)

func original() *config.RawConfig {
	c := config.DefaultRawConfig()
	c.Proxy = []map[string]any{{"name": "Test node", "type": "socks5", "server": "127.0.0.1", "port": 1080}}
	c.ProxyGroup = []map[string]any{{"name": "Subscription group", "type": "select", "proxies": []string{"Test node"}}}
	c.Rule = []string{"MATCH,Subscription group"}
	return c
}
func TestDisabledKeepsOriginalConfiguration(t *testing.T) {
	c := original()
	before, _ := json.Marshal(c)
	if err := Apply(c, Settings{}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	after, _ := json.Marshal(c)
	if string(before) != string(after) {
		t.Fatal("disabled managed mode modified subscription")
	}
}
func TestRulesPriorityAndExceptionTargets(t *testing.T) {
	c := original()
	if err := Apply(c, Settings{Enabled: true, BlockAds: true, DirectDomains: []string{"example.cn"}, ProxyDomains: []string{"example.org"}}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	if c.Rule[0] != "DOMAIN-SUFFIX,example.cn,DIRECT" || c.Rule[1] != "DOMAIN-SUFFIX,example.org,"+MainGroup {
		t.Fatal(c.Rule)
	}
	index := func(name string) int {
		for i, s := range c.Rule {
			if strings.HasPrefix(s, "RULE-SET,live-"+name+",") {
				return i
			}
		}
		return -1
	}
	order := []string{"private-domain", "private-ip", "ads", "openai", "media", "telegram", "github", "cn", "foreign", "cn-ip"}
	last := -1
	for _, s := range order {
		next := index(s)
		if next <= last {
			t.Fatalf("bad priority %s %v", s, c.Rule)
		}
		last = next
	}
	if c.Rule[len(c.Rule)-1] != "MATCH,"+MainGroup {
		t.Fatal("fallback missing")
	}
	if len(c.Proxy) != 1 || c.Proxy[0]["name"] != "Test node" || c.ProxyGroup[0]["name"] != "Subscription group" {
		t.Fatal("nodes or original groups changed")
	}
}
func TestTargetsAndDeletedSubscriptionTargetFallback(t *testing.T) {
	c := original()
	if err := Apply(c, Settings{Enabled: true, Targets: map[string]string{"ai": "Subscription group", "media": "deleted", "cn": "deleted"}}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	joined := strings.Join(c.Rule, "\n")
	for _, want := range []string{"RULE-SET,live-openai,Subscription group", "RULE-SET,live-media," + MainGroup, "RULE-SET,live-cn,DIRECT"} {
		if !strings.Contains(joined, want) {
			t.Fatal(want)
		}
	}
	if strings.Contains(joined, "live-ads") {
		t.Fatal("ad blocking should be opt-in")
	}
}
func TestRejectInvalidDomainAndReservedNames(t *testing.T) {
	for _, domain := range []string{"example.com,DIRECT", "https://example.com", "*.example.com", "bad..com", "-bad.com"} {
		if err := Apply(original(), Settings{Enabled: true, DirectDomains: []string{domain}}, t.TempDir()); err == nil {
			t.Fatal(domain)
		}
	}
	c := original()
	c.Proxy[0]["name"] = MainGroup
	if err := Apply(c, Settings{Enabled: true}, t.TempDir()); err == nil {
		t.Fatal("reserved group collision")
	}
}
func TestProviderCachePathsIndependentOfSubscription(t *testing.T) {
	dir := t.TempDir()
	a := original()
	b := original()
	for _, c := range []*config.RawConfig{a, b} {
		if err := Apply(c, Settings{Enabled: true}, dir); err != nil {
			t.Fatal(err)
		}
	}
	if !reflect.DeepEqual(a.RuleProvider, b.RuleProvider) {
		t.Fatal("cache not stable")
	}
	if a.RuleProvider["live-cn"]["path"] != filepath.Join(dir, "cn.mrs") {
		t.Fatal("wrong cache directory")
	}
}
func TestBundledRulesParseWithMihomo(t *testing.T) {
	dir, err := filepath.Abs("../../../assets/managed-rules")
	if err != nil {
		t.Fatal(err)
	}
	for _, s := range Sets {
		if _, err := os.Stat(filepath.Join(dir, s.Name+".mrs")); err != nil {
			t.Fatal(err)
		}
	}
	C.SetHomeDir(filepath.Dir(dir))
	c := original()
	c.DNS.Enable = true
	if err := Apply(c, Settings{Enabled: true, BlockAds: true, DomesticDNS: true}, dir); err != nil {
		t.Fatal(err)
	}
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	policy := parsed.DNS.NameServerPolicy
	if len(policy) != 1 || policy[0].Matcher == nil {
		t.Fatalf("domestic DNS must parse as one rule-set matcher, got %+v", policy)
	}
	if matcher, ok := policy[0].Matcher.(C.Rule); !ok || matcher.Payload() != "live-cn" {
		t.Fatalf("domestic DNS should match live-cn, got %v", policy[0].Matcher)
	}
	if len(policy[0].NameServers) != 2 {
		t.Fatalf("expected two domestic resolvers, got %+v", policy[0].NameServers)
	}
	for _, ns := range policy[0].NameServers {
		if ns.Net != "https" || ns.ProxyName != "DIRECT" {
			t.Errorf("expected direct DoH, got %+v", ns)
		}
	}
	if len(parsed.RuleProviders) != len(Sets) {
		t.Fatalf("got %d providers", len(parsed.RuleProviders))
	}
	for name, p := range parsed.RuleProviders {
		if err := p.Initial(); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if p.Count() == 0 {
			t.Fatalf("empty provider %s", name)
		}
		if closer, ok := p.(interface{ Close() error }); ok {
			defer closer.Close()
		}
	}
	// Exercise real bundled data and first-match routing, with no DNS/network access.
	route := func(host string) string {
		metadata := &C.Metadata{Host: host}
		for _, rule := range parsed.Rules {
			if rule.RuleType() == C.RuleSet {
				if parsed.RuleProviders[rule.Payload()].Match(metadata, C.RuleMatchHelper{}) {
					return rule.Adapter()
				}
			} else if matched, _ := rule.Match(metadata, C.RuleMatchHelper{}); matched {
				return rule.Adapter()
			}
		}
		return ""
	}
	for _, host := range []string{"www.baidu.com", "www.bilibili.com", "www.jd.com", "www.taobao.com", "music.163.com"} {
		if got := route(host); got != "DIRECT" {
			t.Errorf("%s routed to %s, want DIRECT", host, got)
		}
	}
	for _, host := range []string{"chatgpt.com", "api.openai.com", "claude.ai", "gemini.google.com", "www.netflix.com", "github.com", "t.me", "www.google.com"} {
		if got := route(host); got != MainGroup {
			t.Errorf("%s routed to %s, want main proxy", host, got)
		}
	}
}

func TestProviderOnlySubscriptionAndOriginalDNSReferences(t *testing.T) {
	c := original()
	c.ProxyProvider = map[string]map[string]any{"Airport": {
		"type": "inline", "payload": c.Proxy,
	}}
	c.Proxy = nil
	c.ProxyGroup = []map[string]any{{"name": "Subscription group", "type": "select", "use": []string{"Airport"}}}
	c.RuleProvider = map[string]map[string]any{"subscription-dns": {
		"type": "inline", "behavior": "domain", "payload": []string{"+.example.cn"},
	}}
	c.DNS.Enable = true
	c.DNS.NameServerPolicy = orderedmap.New[string, any]()
	c.DNS.NameServerPolicy.Set("rule-set:subscription-dns", []string{"223.5.5.5"})
	dir := t.TempDir()
	C.SetHomeDir(dir)
	if err := Apply(c, Settings{Enabled: true, DomesticDNS: true, Targets: map[string]string{"ai": "Subscription group"}}, dir); err != nil {
		t.Fatal(err)
	}
	if _, ok := c.RuleProvider["subscription-dns"]; !ok {
		t.Fatal("original DNS rule provider removed")
	}
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := parsed.Proxies[MainGroup]; !ok {
		t.Fatal("provider-only subscription lost default group")
	}
	if _, ok := parsed.Providers["Airport"]; !ok {
		t.Fatal("subscription provider removed")
	}
	for _, p := range parsed.RuleProviders {
		if closer, ok := p.(interface{ Close() error }); ok {
			defer closer.Close()
		}
	}
}

func TestPruneUnusedProvidersButKeepDNSReferences(t *testing.T) {
	cfg := config.DefaultRawConfig()
	cfg.Proxy = []map[string]any{{"name": "node", "type": "ss"}}
	cfg.RuleProvider = map[string]map[string]any{
		"old-a": {"type": "http"}, "Old-B": {"type": "http"},
		"fake-filter": {"type": "http"}, "unused": {"type": "http"},
	}
	cfg.DNS.NameServerPolicy = orderedmap.New[string, any]()
	cfg.DNS.NameServerPolicy.Set("RULE-SET:old-a,Old-B", []string{"system://"})
	cfg.DNS.FakeIPFilter = []string{"RULE-SET,fake-filter,real-ip"}
	if err := Apply(cfg, Settings{Enabled: true}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	if _, ok := cfg.RuleProvider["unused"]; ok {
		t.Fatal("unused provider retained")
	}
	for _, n := range []string{"old-a", "Old-B", "fake-filter"} {
		if _, ok := cfg.RuleProvider[n]; !ok {
			t.Fatal("DNS reference removed", n)
		}
	}
}
