package routing

import (
	"strings"
	"testing"
)

func TestMuseScopedRuleCoverage(t *testing.T) {
	want := []string{
		"DOMAIN-SUFFIX,muse.ai,Meta exit",
		"DOMAIN-SUFFIX,meta.ai,Meta exit",
		"DOMAIN,ai.meta.com,Meta exit",
		"DOMAIN,accounts.meta.com,Meta exit",
	}
	got := museRules("Meta exit")
	if len(got) != len(want) {
		t.Fatalf("got %d Muse rules, want %d: %v", len(got), len(want), got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("rule %d: got %q, want %q", i, got[i], want[i])
		}
	}
	for _, rule := range got {
		if strings.Contains(rule, "facebook.com") ||
			strings.Contains(rule, "whatsapp.com") ||
			strings.Contains(rule, "instagram.com") {
			t.Fatal("Muse routing must not change unrelated Meta applications")
		}
	}
}

func TestClaudeMuseIndependentOutbounds(t *testing.T) {
	cfg := original()
	s := Settings{
		Enabled: true,
		Targets: map[string]string{
			"ai":     "Subscription group",
			"claude": "Test node",
			"muse":   "DIRECT",
		},
	}
	if err := Apply(cfg, s, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	joined := strings.Join(cfg.Rule, "\n")
	for _, want := range []string{
		"DOMAIN-SUFFIX,claude.ai,Test node",
		"DOMAIN-SUFFIX,anthropic.com,Test node",
		"DOMAIN-SUFFIX,muse.ai,DIRECT",
		"DOMAIN-SUFFIX,meta.ai,DIRECT",
		"RULE-SET,live-openai,Subscription group",
	} {
		if !strings.Contains(joined, want) {
			t.Fatalf("missing dedicated exit rule %q", want)
		}
	}
	if strings.Contains(joined, "DOMAIN-SUFFIX,facebook.com,") {
		t.Fatal("unrelated facebook traffic was rerouted")
	}
}

func TestMuseInheritsAIExitWithoutDedicatedChoice(t *testing.T) {
	cfg := original()
	if err := Apply(cfg, Settings{Enabled: true, Targets: map[string]string{"ai": "Subscription group"}}, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"DOMAIN-SUFFIX,claude.ai,Subscription group", "DOMAIN-SUFFIX,muse.ai,Subscription group"} {
		if !strings.Contains(strings.Join(cfg.Rule, "\n"), want) {
			t.Fatalf("expected inherited AI target: %s", want)
		}
	}
}
