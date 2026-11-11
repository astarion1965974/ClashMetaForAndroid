package routing

import (
	"strings"
	"testing"
)

func TestClaudeReferenceCoverage(t *testing.T) {
	rules := claudeRules("AI exit")
	expected := []string{
		"DOMAIN-SUFFIX,anthropic.com,AI exit",
		"DOMAIN-SUFFIX,claude.ai,AI exit",
		"DOMAIN-SUFFIX,claude.com,AI exit",
		"DOMAIN-SUFFIX,clau.de,AI exit",
		"DOMAIN-SUFFIX,claudemcpclient.com,AI exit",
		"DOMAIN-SUFFIX,claudemcpcontent.com,AI exit",
		"DOMAIN-SUFFIX,claudeusercontent.com,AI exit",
		"DOMAIN,servd-anthropic-website.b-cdn.net,AI exit",
		"DOMAIN,anthropic.com.cdn.cloudflare.net,AI exit",
		"DOMAIN,anthropic.auth0.com,AI exit",
		"DOMAIN,anthropic-com.ghost.io,AI exit",
		"DOMAIN-SUFFIX,sentry.io,AI exit",
		"DOMAIN-SUFFIX,statsigapi.net,AI exit",
		"DOMAIN,browser-intake-us5-datadoghq.com,AI exit",
		"DOMAIN-KEYWORD,datadog,AI exit",
		"DOMAIN-KEYWORD,sift,AI exit",
		"DOMAIN-KEYWORD,sentry,AI exit",
		"DOMAIN-SUFFIX,intercom.io,AI exit",
		"DOMAIN-SUFFIX,intercomcdn.com,AI exit",
		"DOMAIN,cdn.usefathom.com,AI exit",
		"IP-CIDR,160.79.104.0/21,AI exit,no-resolve",
		"IP-CIDR6,2607:6bc0::/32,AI exit,no-resolve",
		"IP-ASN,399358,AI exit,no-resolve",
	}
	if len(rules) != len(expected) {
		t.Fatalf("got %d rules, want %d", len(rules), len(expected))
	}
	seen := make(map[string]bool, len(rules))
	for _, rule := range rules {
		if seen[rule] {
			t.Fatalf("duplicate Claude rule %q", rule)
		}
		seen[rule] = true
	}
	for _, rule := range expected {
		if !seen[rule] {
			t.Errorf("missing Claude rule %q", rule)
		}
	}
	for _, rule := range rules {
		if strings.Contains(strings.ToLower(rule), "category-ntp") {
			t.Fatal("global NTP interception must not be implicit")
		}
	}
}

func TestClaudeRulesPriorityAndConfigurableAIExit(t *testing.T) {
	cfg := original()
	settings := Settings{
		Enabled: true,
		DirectDomains: []string{"example.org"},
		ProxyDomains: []string{"other.example"},
		Targets: map[string]string{"ai": "Subscription group"},
	}
	if err := Apply(cfg, settings, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	index := func(rule string) int {
		for i, r := range cfg.Rule {
			if r == rule {
				return i
			}
		}
		return -1
	}
	direct := index("DOMAIN-SUFFIX,example.org,DIRECT")
	proxy := index("DOMAIN-SUFFIX,other.example," + MainGroup)
	claudeDomain := index("DOMAIN-SUFFIX,claude.ai,Subscription group")
	claudeIP := index("IP-CIDR,160.79.104.0/21,Subscription group,no-resolve")
	generalProvider := index("RULE-SET,live-private-domain,DIRECT")
	anthropicProvider := index("RULE-SET,live-anthropic,Subscription group")
	if direct != 0 || proxy != 1 || claudeDomain <= proxy ||
		claudeIP <= claudeDomain || generalProvider <= claudeIP ||
		anthropicProvider <= generalProvider {
		t.Fatalf("unexpected rule priority: %v", cfg.Rule)
	}
	if cfg.Rule[len(cfg.Rule)-1] != "MATCH,"+MainGroup {
		t.Fatal("fallback unexpectedly changed")
	}
}

func TestClaudeRulesOnlyUnderManagedRouting(t *testing.T) {
	cfg := original()
	if err := Apply(cfg, Settings{}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	if len(cfg.Rule) != 1 || cfg.Rule[0] != "MATCH,Subscription group" {
		t.Fatalf("disabled managed mode modified subscription: %v", cfg.Rule)
	}
}
