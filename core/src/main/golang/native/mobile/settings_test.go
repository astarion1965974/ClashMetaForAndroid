package mobile

import (
	"cfa/native/routing"
	"encoding/json"
	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/config"
	_ "github.com/metacubex/mihomo/hub/executor"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"
)

func base() *config.RawConfig {
	c := config.DefaultRawConfig()
	c.Proxy = []map[string]any{{"name": "Tokyo 1", "type": "ss"}, {"name": "Tokyo 2", "type": "ss"}}
	c.ProxyGroup = []map[string]any{{"name": "Live · 默认代理", "type": "select", "include-all-proxies": true}}
	c.Rule = []string{"RULE-SET,live-openai,Live · 默认代理", "MATCH,Live · 默认代理"}
	c.RuleProvider = map[string]map[string]any{"live-cn": {"type": "http", "interval": 86400}}
	return c
}
func TestMissingSettingsDefaultAndDisabled(t *testing.T) {
	s, err := Read(filepath.Join(t.TempDir(), "missing"))
	if err != nil || !s.Enabled || !s.SecureDNS || !s.WifiUpdates {
		t.Fatal(s, err)
	}
	c := base()
	before, _ := json.Marshal(c)
	if err := Apply(c, Settings{}); err != nil {
		t.Fatal(err)
	}
	after, _ := json.Marshal(c)
	if string(before) != string(after) {
		t.Fatal("disabled preset changed source config")
	}
}
func TestOrderedFallbackAndAI(t *testing.T) {
	c := base()
	err := Apply(c, Settings{Enabled: true, StableNodes: []string{"Tokyo 2", "Tokyo 1"}, AINodes: []string{"Tokyo 1"}})
	if err != nil {
		t.Fatal(err)
	}
	if c.Rule[0] != "RULE-SET,live-openai,"+AIGroup || c.Rule[1] != "MATCH,"+StableGroup {
		t.Fatal(c.Rule)
	}
	g := c.ProxyGroup[1]
	if g["type"] != "fallback" || g["proxies"].([]string)[0] != "Tokyo 2" || g["empty-fallback"] != "REJECT" {
		t.Fatal(g)
	}
}
func TestExplicitExitPreserved(t *testing.T) {
	c := base()
	c.Rule[0] = "RULE-SET,live-openai,MyCustom"
	_ = Apply(c, Settings{Enabled: true, AINodes: []string{"Tokyo 1"}})
	if !strings.HasSuffix(c.Rule[0], ",MyCustom") {
		t.Fatal(c.Rule)
	}
}
func TestProviderOnlyWhitelist(t *testing.T) {
	c := base()
	c.Proxy = nil
	c.ProxyProvider = map[string]map[string]any{"airport": {"type": "http", "interval": 300, "health-check": map[string]any{"interval": 60, "lazy": false}}}
	if err := Apply(c, Settings{Enabled: true, WifiUpdates: true, StableNodes: []string{"JP [1]", "JP 2"}}); err != nil {
		t.Fatal(err)
	}
	g := c.ProxyGroup[1]
	if g["filter"] != "^(?:JP \\[1\\]|JP 2)$" {
		t.Fatal(g)
	}
	if c.ProxyProvider["airport"]["interval"] != 86400 {
		t.Fatal(c.ProxyProvider)
	}
}
func TestDNSBootstrapAndCustomResolver(t *testing.T) {
	c := base()
	c.DNS.ProxyServerNameserver = []string{"https://custom.example/dns-query"}
	c.DNS.NameServerPolicy = orderedmap.New[string, any]()
	c.DNS.NameServerPolicy.Set("internal.example", []string{"system://"})
	domesticResolvers := []string{"https://dns.alidns.com/dns-query#DIRECT", "https://doh.pub/dns-query#DIRECT"}
	c.DNS.NameServerPolicy.Set("rule-set:live-cn", domesticResolvers)
	if err := Apply(c, Settings{Enabled: true, SecureDNS: true}); err != nil {
		t.Fatal(err)
	}
	if c.DNS.ProxyServerNameserver[0] != "https://custom.example/dns-query" {
		t.Fatal(c.DNS)
	}
	if len(c.DNS.DefaultNameserver) == 0 || len(c.DNS.NameServer) != 1 {
		t.Fatal(c.DNS)
	}
	if resolvers, _ := c.DNS.NameServerPolicy.Get("rule-set:live-cn"); !reflect.DeepEqual(resolvers, domesticResolvers) {
		t.Fatalf("mobile preset replaced managed domestic resolvers: %v", resolvers)
	}
	if _, ok := c.DNS.NameServerPolicy.Get("rule-set,live-cn"); ok {
		t.Fatal("mobile preset added a comma-delimited domestic DNS policy")
	}
}
func TestNoDirectFallbackOnMissingNodes(t *testing.T) {
	c := base()
	if Apply(c, Settings{Enabled: true, StableNodes: []string{"Removed"}}) == nil {
		t.Fatal("accepted missing nodes")
	}
	c = base()
	c.ProxyGroup = append(c.ProxyGroup, map[string]any{"name": StableGroup})
	if Apply(c, Settings{Enabled: true}) == nil {
		t.Fatal("reserved name accepted")
	}
}
func TestMaintenanceAndScreenPolicy(t *testing.T) {
	enabled.Store(true)
	wifiUpdates.Store(true)
	SetState(true, false, true, false, false)
	if HealthAllowed() {
		t.Fatal("screen off check")
	}
	if UpdateAllowed(time.Now()) {
		t.Fatal("fresh metered update")
	}
	if !UpdateAllowed(time.Now().Add(-73 * time.Hour)) {
		t.Fatal("expiry bound")
	}
	SetState(false, true, false, true, false)
	if HealthAllowed() || UpdateAllowed(time.Now().Add(-100*time.Hour)) {
		t.Fatal("offline activity")
	}
	SetState(true, true, false, false, true)
	if HealthAllowed() {
		t.Fatal("home check")
	}
	if !UpdateAllowed(time.Now()) {
		t.Fatal("wifi update")
	}
	SetState(true, true, true, true, false)
	if !HealthAllowed() || !UpdateAllowed(time.Now()) {
		t.Fatal("foreground/charging")
	}
}
func TestSettingsDecodeAndLimit(t *testing.T) {
	p := filepath.Join(t.TempDir(), "mobile.json")
	_ = os.WriteFile(p, []byte(`{"enabled":false,"future":1}`), 0600)
	s, err := Read(p)
	if err != nil || s.Enabled {
		t.Fatal(s, err)
	}
	c := base()
	if Apply(c, Settings{Enabled: true, StableNodes: []string{"1", "2", "3", "4", "5", "6"}}) == nil {
		t.Fatal("node cap")
	}
}

func TestGeneratedFallbackParsesWithMihomo(t *testing.T) {
	SetState(false, false, true, false, false)
	c := config.DefaultRawConfig()
	c.Proxy = []map[string]any{{"name": "JP 1", "type": "socks5", "server": "127.0.0.1", "port": 1080}, {"name": "JP 2", "type": "socks5", "server": "127.0.0.1", "port": 1081}}
	c.ProxyGroup = []map[string]any{{"name": "Live · 默认代理", "type": "select", "proxies": []string{"JP 1"}}}
	c.Rule = []string{"MATCH,Live · 默认代理"}
	c.DNS.Enable = true
	c.RuleProvider = map[string]map[string]any{
		"live-cn":     {"type": "inline", "behavior": "domain", "payload": []string{"+.example.cn"}},
		"live-openai": {"type": "inline", "behavior": "domain", "payload": []string{"+.example.org"}},
	}
	if err := Apply(c, Settings{Enabled: true, SecureDNS: true, StableNodes: []string{"JP 2", "JP 1"}, AINodes: []string{"JP 1"}}); err != nil {
		t.Fatal(err)
	}
	parsed, err := config.ParseRawConfig(c)
	if err != nil {
		t.Fatal(err)
	}
	policies := parsed.DNS.NameServerPolicy
	if len(policies) != 2 || policies[0].Matcher == nil || policies[1].Matcher == nil {
		t.Fatalf("expected domestic and AI rule-set DNS policies, got %+v", policies)
	}
	if policies[0].NameServers[0].ProxyName != "DIRECT" || policies[1].NameServers[0].ProxyName != AIGroup ||
		parsed.DNS.NameServer[0].ProxyName != StableGroup {
		t.Fatal("unexpected DNS exits")
	}
	for _, name := range []string{StableGroup, AIGroup} {
		if parsed.Proxies[name] == nil {
			t.Fatal("missing group", name)
		}
	}
	for _, p := range parsed.Providers {
		if closer, ok := p.(interface{ Close() error }); ok {
			_ = closer.Close()
		}
	}
}

func TestProtectedDNSNotReplacedByMobilePreset(t *testing.T) {
	c := base()
	c.ProxyGroup = append(c.ProxyGroup, map[string]any{"name": routing.ProtectedGroup})
	c.DNS.NameServer = []string{"https://1.1.1.1/dns-query#" + routing.ProtectedGroup}
	before := append([]string{}, c.DNS.NameServer...)
	if err := Apply(c, Settings{Enabled: true, SecureDNS: true}); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(before, c.DNS.NameServer) {
		t.Fatalf("protected DNS changed: %v", c.DNS.NameServer)
	}
}
