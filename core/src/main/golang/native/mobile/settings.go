package mobile

import (
	"encoding/json"
	"fmt"
	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/config"
	"os"
	"regexp"
	"strings"
)

const StableGroup = "Live · 移动稳定"
const AIGroup = "Live · AI稳定"

type Settings struct {
	Enabled            bool     `json:"enabled"`
	SecureDNS          bool     `json:"secureDns"`
	WifiUpdates        bool     `json:"wifiUpdates"`
	StableNodes        []string `json:"stableNodes"`
	AINodes            []string `json:"aiNodes"`
	TrustedWifiEnabled bool     `json:"trustedWifiEnabled"`
	TrustedSSIDs       []string `json:"trustedSSIDs"`
}

func Read(path string) (Settings, error) {
	s := Settings{Enabled: true, SecureDNS: true, WifiUpdates: true}
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return s, nil
	}
	if err != nil {
		return s, err
	}
	err = json.Unmarshal(data, &s)
	return s, err
}
func Apply(cfg *config.RawConfig, s Settings) error {
	enabled.Store(s.Enabled)
	wifiUpdates.Store(s.WifiUpdates)
	if !s.Enabled {
		return nil
	}
	for _, g := range cfg.ProxyGroup {
		if g["name"] == StableGroup || g["name"] == AIGroup {
			return fmt.Errorf("reserved mobile group name: %v", g["name"])
		}
	}
	addGroup := func(name string, nodes []string) (bool, error) {
		if len(nodes) == 0 {
			return false, nil
		}
		if len(nodes) > 5 {
			return false, fmt.Errorf("select at most five nodes")
		}
		raw := map[string]bool{}
		for _, p := range cfg.Proxy {
			if n, ok := p["name"].(string); ok {
				raw[n] = true
			}
		}
		list := []string{}
		filters := []string{}
		seen := map[string]bool{}
		for _, n := range nodes {
			if n == "" || strings.ContainsAny(n, "\n\r") || n == "DIRECT" || n == "REJECT" {
				return false, fmt.Errorf("invalid mobile node")
			}
			if seen[n] {
				continue
			}
			seen[n] = true
			if raw[n] {
				list = append(list, n)
			} else {
				filters = append(filters, regexp.QuoteMeta(n))
			}
		}
		g := map[string]any{"name": name, "type": "fallback", "proxies": list, "url": "https://www.gstatic.com/generate_204", "expected-status": "204", "interval": 600, "lazy": true, "timeout": 5000, "max-failed-times": 3, "empty-fallback": "REJECT"}
		if len(filters) > 0 && len(cfg.ProxyProvider) > 0 {
			g["include-all-providers"] = true
			g["filter"] = "^(?:" + strings.Join(filters, "|") + ")$"
		}
		if len(list) == 0 && len(filters) > 0 && len(cfg.ProxyProvider) == 0 {
			return false, fmt.Errorf("mobile nodes no longer exist; update your selection")
		}
		cfg.ProxyGroup = append(cfg.ProxyGroup, g)
		return true, nil
	}
	stable, err := addGroup(StableGroup, s.StableNodes)
	if err != nil {
		return err
	}
	ai, err := addGroup(AIGroup, s.AINodes)
	if err != nil {
		return err
	}
	// Managed exits are changed only when they still use the default group.
	for i, r := range cfg.Rule {
		target := ""
		if strings.HasSuffix(r, ",Live · 默认代理") {
			if ai && (strings.Contains(r, ",live-openai,") || strings.Contains(r, ",live-anthropic,") || strings.Contains(r, ",live-gemini,")) {
				target = AIGroup
			} else if stable {
				target = StableGroup
			}
		}
		if target != "" {
			cfg.Rule[i] = strings.TrimSuffix(r, "Live · 默认代理") + target
		}
	}
	for _, g := range cfg.ProxyGroup {
		if g["name"] == "Live · 默认代理" && stable {
			g["proxies"] = []string{StableGroup}
			g["include-all-proxies"] = false
			g["include-all-providers"] = false
		}
		// Cap aggressive subscription checks; keep slower user intervals intact.
		if t, _ := g["type"].(string); t == "url-test" || t == "fallback" || t == "load-balance" {
			if n := number(g["interval"]); n < 600 {
				g["interval"] = 600
			}
			g["lazy"] = true
		}
	}
	for _, p := range cfg.ProxyProvider {
		if hc, ok := p["health-check"].(map[string]any); ok {
			if n := number(hc["interval"]); n > 0 && n < 600 {
				hc["interval"] = 600
			}
			hc["lazy"] = true
		}
	}
	if s.WifiUpdates {
		for _, providers := range []map[string]map[string]any{cfg.ProxyProvider, cfg.RuleProvider} {
			for _, p := range providers {
				if p["type"] == "http" {
					if n := number(p["interval"]); n > 0 && n < 86400 {
						p["interval"] = 86400
					}
				}
			}
		}
	}
	if s.SecureDNS {
		// Keep custom DNS policies; supply independent bootstrap and node resolution.
		if len(cfg.DNS.DefaultNameserver) == 0 {
			cfg.DNS.DefaultNameserver = []string{"223.5.5.5", "119.29.29.29"}
		}
		if len(cfg.DNS.ProxyServerNameserver) == 0 {
			cfg.DNS.ProxyServerNameserver = []string{"https://dns.alidns.com/dns-query#DIRECT"}
		}
		cfg.DNS.PreferH3 = false
		if cfg.DNS.NameServerPolicy == nil {
			cfg.DNS.NameServerPolicy = orderedmap.New[string, any]()
		}
		if _, ok := cfg.RuleProvider["live-cn"]; ok {
			cfg.DNS.NameServerPolicy.Set("rule-set,live-cn", []string{"https://dns.alidns.com/dns-query#DIRECT"})
			exit := "Live · 默认代理"
			if stable {
				exit = StableGroup
			}
			cfg.DNS.NameServer = []string{"https://1.1.1.1/dns-query#" + exit}
			if ai {
				for _, name := range []string{"live-openai", "live-anthropic", "live-gemini"} {
					if _, exists := cfg.RuleProvider[name]; exists {
						cfg.DNS.NameServerPolicy.Set("rule-set,"+name, []string{"https://1.1.1.1/dns-query#" + AIGroup})
					}
				}
			}
			cfg.DNS.Fallback = nil
		}
	}
	return nil
}
func number(v any) int {
	switch n := v.(type) {
	case int:
		return n
	case int64:
		return int(n)
	case uint:
		return int(n)
	case float64:
		return int(n)
	}
	return 0
}
