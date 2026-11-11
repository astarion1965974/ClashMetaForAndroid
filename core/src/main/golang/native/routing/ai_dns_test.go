package routing

import (
    "testing"

    "github.com/metacubex/mihomo/config"
)

func TestAIProxyDNSRespectsSelectedExitsAndParses(t *testing.T) {
    cfg := original()
    cfg.DNS.Enable = true
    settings := Settings{
        Enabled: true,
        DomesticDNS: true,
        AIProxyDNS: true,
        Targets: map[string]string{
            "ai": "Subscription group",
            "claude": "Test node",
            "muse": "Subscription group",
        },
    }
    if err := Apply(cfg, settings, t.TempDir()); err != nil {
        t.Fatal(err)
    }
    policy := cfg.DNS.NameServerPolicy
    if policy == nil {
        t.Fatal("missing DNS policies")
    }
    for domain, exit := range map[string]string{
        "+.claude.ai": "Test node",
        "+.anthropic.com": "Test node",
        "+.muse.ai": "Subscription group",
        "+.meta.ai": "Subscription group",
    } {
        value, found := policy.Get(domain)
        if !found {
            t.Fatalf("missing policy for %s", domain)
        }
        servers, ok := value.([]string)
        if !ok || len(servers) != 1 || servers[0] != "https://1.1.1.1/dns-query#"+exit {
            t.Fatalf("unexpected policy %s: %#v", domain, value)
        }
    }
    if _, err := config.ParseRawConfig(cfg); err != nil {
        t.Fatalf("Claude / Muse routing and DoH config invalid: %v", err)
    }
}

func TestDirectServiceExitDoesNotForceProxyDNS(t *testing.T) {
    cfg := original()
    if err := Apply(cfg, Settings{Enabled: true, AIProxyDNS: true, Targets: map[string]string{
        "claude": "DIRECT", "muse": "REJECT",
    }}, t.TempDir()); err != nil {
        t.Fatal(err)
    }
    if cfg.DNS.NameServerPolicy != nil {
        if _, ok := cfg.DNS.NameServerPolicy.Get("+.claude.ai"); ok {
            t.Fatal("direct Claude should not use proxy DNS")
        }
        if _, ok := cfg.DNS.NameServerPolicy.Get("+.muse.ai"); ok {
            t.Fatal("blocked Muse should not use proxy DNS")
        }
    }
}
