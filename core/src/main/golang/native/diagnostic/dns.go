package diagnostic

import (
    "net"
    "net/url"
    "strings"

    "github.com/metacubex/mihomo/component/trie"
    mihomoConfig "github.com/metacubex/mihomo/config"
    "github.com/metacubex/mihomo/dns"
)

// DNSDiagnosticPolicy describes the configured upstream candidates, never a
// guaranteed packet path. IP fallback, caching, racing and RULES dialers can
// make a live DNS query use a different endpoint than the first candidate.
type DNSDiagnosticPolicy struct {
    Route string `json:"dnsRoute"`
    Proxy string `json:"dnsProxy"`
    Upstream string `json:"dnsUpstream"`
    Source string `json:"dnsSource"`
}

func InspectDNSPolicy(cfg *mihomoConfig.DNS, domain string) DNSDiagnosticPolicy {
    return inspectDNSPolicy(cfg, domain)
}

func inspectDNSPolicy(cfg *mihomoConfig.DNS, domain string) DNSDiagnosticPolicy {
    info := DNSDiagnosticPolicy{Route:"unknown", Source:"unknown"}
    if cfg == nil || !cfg.Enable || domain == "" { return info }

    servers, source := selectDNSCandidates(cfg, strings.ToLower(strings.TrimSuffix(domain,".")))
    info.Source = source
    if len(servers) == 0 { return info }

    // All candidates must agree before classifying the route as direct/proxy;
    // never label a mixed direct+proxy setup as a confirmed proxy DNS query.
    kinds := map[string]bool{}
    serverNames := make([]string,0,len(servers))
    proxies := make([]string,0,len(servers))
    for _,ns := range servers {
        kind,proxy := dnsProxyRoute(ns)
        kinds[kind] = true
        serverNames = appendDistinct(serverNames, dnsServerName(ns))
        if proxy != "" { proxies = appendDistinct(proxies,proxy) }
    }
    switch {
    case len(kinds) != 1: info.Route = "mixed"
    case kinds["proxy"]: info.Route = "proxy"
    case kinds["direct"]: info.Route = "direct"
    case kinds["rules"]: info.Route = "rules"
    default: info.Route = "unknown"
    }
    info.Proxy = strings.Join(proxies," / ")
    info.Upstream = strings.Join(serverNames," / ")
    return info
}

func selectDNSCandidates(cfg *mihomoConfig.DNS, domain string) ([]dns.NameServer,string) {
    // Mirror the resolver's ordered policy matching: consecutive domain
    // patterns are one trie, with explicit matchers breaking the trie block.
    triePolicy := trie.New[[]dns.NameServer]()
    flush := func() ([]dns.NameServer,bool) {
        node := triePolicy.Search(domain)
        if node != nil && len(node.Data()) > 0 { return node.Data(),true }
        return nil,false
    }
    for _,p := range cfg.NameServerPolicy {
        if p.Matcher != nil {
            if matched,ok := flush();ok { return matched,"policy" }
            triePolicy = trie.New[[]dns.NameServer]()
            if p.Matcher.MatchDomain(domain) && len(p.NameServers)>0 {
                return p.NameServers,"policy"
            }
            continue
        }
        _ = triePolicy.Insert(p.Domain,p.NameServers)
    }
    if matched,ok := flush();ok { return matched,"policy" }

    if len(cfg.Fallback)>0 {
        for _, filter := range cfg.FallbackDomainFilter {
            if filter.MatchDomain(domain) { return cfg.Fallback,"fallback" }
        }
        // A/B queries may race main and fallback, or use fallback after
        // response-based IP filtering. Both are candidates, not proof of use.
        both := make([]dns.NameServer,0,len(cfg.NameServer)+len(cfg.Fallback))
        both = append(both,cfg.NameServer...)
        both = append(both,cfg.Fallback...)
        return both,"main+fallback"
    }
    return cfg.NameServer,"main"
}

func dnsProxyRoute(ns dns.NameServer) (string,string) {
    if ns.ProxyAdapter != nil {
        return "proxy",ns.ProxyAdapter.Name()
    }
    switch ns.ProxyName {
    case "","DIRECT":
        return "direct",""
    case dns.RespectRules:
        return "rules","RULES"
    default:
        return "proxy",ns.ProxyName
    }
}

func dnsServerName(ns dns.NameServer) string {
    switch ns.Net {
    case "system":
        return "system"
    case "rcode":
        return "rcode"
    }
    address := ns.Addr
    if u,err := url.Parse(address);err == nil && u.Host != "" {
        address = u.Hostname()
    } else if host,_,err := net.SplitHostPort(address);err == nil {
        address = host
    }
    if address == "" { address = "unknown" }
    protocol:= ns.Net
    if protocol == "" {protocol = "udp"}
    return protocol+"://"+address
}

func appendDistinct(list []string,item string) []string {
    if item == "" {return list}
    for _, value := range list {if value==item{return list}}
    return append(list,item)
}
