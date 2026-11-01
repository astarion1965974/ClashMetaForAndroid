// Package routing builds managed rules without modifying subscription files.
package routing

import (
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"

	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/config"
	T "github.com/metacubex/mihomo/tunnel"
)

const MainGroup = "Live · 默认代理"
const Prefix = "live-"
const Source = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/"

type Settings struct {
	Enabled       bool              `json:"enabled"`
	BlockAds      bool              `json:"blockAds"`
	DomesticDNS   bool              `json:"domesticDns"`
	Targets       map[string]string `json:"targets"`
	DirectDomains []string          `json:"directDomains"`
	ProxyDomains  []string          `json:"proxyDomains"`
}

type RuleSet struct{ Name, Category, Path, Behavior string }

var Sets = []RuleSet{
	{"private-domain", "private", "geosite/private.mrs", "domain"},
	{"private-ip", "private", "geoip/private.mrs", "ipcidr"},
	{"ads", "ads", "geosite/category-ads-all.mrs", "domain"},
	{"openai", "ai", "geosite/openai.mrs", "domain"},
	{"anthropic", "ai", "geosite/anthropic.mrs", "domain"},
	{"gemini", "ai", "geosite/google-gemini.mrs", "domain"},
	{"media", "media", "geosite/category-entertainment.mrs", "domain"},
	{"telegram", "social", "geosite/telegram.mrs", "domain"},
	{"twitter", "social", "geosite/twitter.mrs", "domain"},
	{"facebook", "social", "geosite/facebook.mrs", "domain"},
	{"github", "dev", "geosite/github.mrs", "domain"},
	{"dev", "dev", "geosite/category-dev.mrs", "domain"},
	{"cn", "cn", "geosite/cn.mrs", "domain"},
	{"foreign", "foreign", "geosite/geolocation-!cn.mrs", "domain"},
	{"cn-ip", "cn", "geoip/cn.mrs", "ipcidr"},
}

func ValidDomain(s string) bool {
	if s == "" || len(s) > 253 || strings.ContainsAny(s, ", /:#@\n\r\t") {
		return false
	}
	for _, label := range strings.Split(s, ".") {
		if len(label) == 0 || len(label) > 63 || label[0] == '-' || label[len(label)-1] == '-' {
			return false
		}
		for _, c := range label {
			if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-') {
				return false
			}
		}
	}
	return true
}

func Apply(cfg *config.RawConfig, s Settings, cacheDir string) error {
	if !s.Enabled {
		return nil
	}
	for _, domain := range append(append([]string{}, s.DirectDomains...), s.ProxyDomains...) {
		if !ValidDomain(domain) {
			return fmt.Errorf("invalid exception domain: %q", domain)
		}
	}
	available := map[string]bool{"DIRECT": true, "REJECT": true, MainGroup: true}
	for _, p := range cfg.Proxy {
		if n, ok := p["name"].(string); ok {
			available[n] = true
			if n == MainGroup {
				return fmt.Errorf("reserved managed name: %s", n)
			}
		}
	}
	for _, g := range cfg.ProxyGroup {
		if n, ok := g["name"].(string); ok {
			available[n] = true
			if n == MainGroup {
				return fmt.Errorf("reserved managed name: %s", n)
			}
		}
	}
	target := func(category string) string {
		if t := s.Targets[category]; available[t] && !strings.Contains(t, ",") {
			return t
		}
		if category == "cn" || category == "private" {
			return "DIRECT"
		}
		if category == "ads" {
			return "REJECT"
		}
		return MainGroup
	}
	cfg.ProxyGroup = append(cfg.ProxyGroup, map[string]any{"name": MainGroup, "type": "select", "include-all-proxies": true, "include-all-providers": true})
	// Only routing is replaced. Nodes, providers, DNS, listeners and the source YAML remain intact.
	if cfg.RuleProvider == nil {
		cfg.RuleProvider = map[string]map[string]any{}
	}
	rules := []string{}
	for _, d := range s.DirectDomains {
		rules = append(rules, "DOMAIN-SUFFIX,"+d+",DIRECT")
	}
	for _, d := range s.ProxyDomains {
		rules = append(rules, "DOMAIN-SUFFIX,"+d+","+MainGroup)
	}
	for _, r := range Sets {
		if r.Category == "ads" && !s.BlockAds {
			continue
		}
		name := Prefix + r.Name
		if _, exists := cfg.RuleProvider[name]; exists {
			return fmt.Errorf("reserved managed provider: %s", name)
		}
		cfg.RuleProvider[name] = map[string]any{"type": "http", "format": "mrs", "behavior": r.Behavior, "url": Source + r.Path, "path": filepath.Join(cacheDir, r.Name+".mrs"), "interval": 86400, "proxy": MainGroup}
		rule := "RULE-SET," + name + "," + target(r.Category)
		if r.Behavior == "ipcidr" {
			rule += ",no-resolve"
		}
		rules = append(rules, rule)
	}
	cfg.Rule = append(rules, "MATCH,"+target("fallback"))
	cfg.SubRules = nil
	cfg.Mode = T.Rule
	if s.DomesticDNS {
		if cfg.DNS.NameServerPolicy == nil {
			cfg.DNS.NameServerPolicy = orderedmap.New[string, any]()
		}
		cfg.DNS.NameServerPolicy.Set("rule-set,live-cn", []string{"https://dns.alidns.com/dns-query#DIRECT", "https://doh.pub/dns-query#DIRECT"})
	}
	// DNS policies and group filters may still reference original providers.
	old := map[string]map[string]any{}
	for k, v := range cfg.RuleProvider {
		if !strings.HasPrefix(k, Prefix) {
			old[k] = v
			delete(cfg.RuleProvider, k)
		}
	}
	data, _ := json.Marshal(cfg)
	var value any
	_ = json.Unmarshal(data, &value)
	keep := map[string]bool{}
	var scan func(any)
	scan = func(v any) {
		switch x := v.(type) {
		case string:
			if _, ok := old[x]; ok {
				keep[x] = true
			}
			if strings.Contains(strings.ToLower(x), "rule-set,") {
				for _, token := range strings.Split(x, ",") {
					if _, ok := old[strings.TrimSpace(token)]; ok {
						keep[strings.TrimSpace(token)] = true
					}
				}
			}
		case []any:
			for _, item := range x {
				scan(item)
			}
		case map[string]any:
			for k, item := range x {
				scan(k)
				scan(item)
			}
		}
	}
	scan(value)
	for k := range keep {
		cfg.RuleProvider[k] = old[k]
	}

	return nil
}
