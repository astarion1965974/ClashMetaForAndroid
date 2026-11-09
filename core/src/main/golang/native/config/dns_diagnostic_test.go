package config

import (
    "strings"
    "testing"

    mihomoConfig "github.com/metacubex/mihomo/config"
    "github.com/metacubex/mihomo/dns"
)

func TestDNSDiagnosticPerDomainPolicy(t *testing.T) {
    cfg := &mihomoConfig.DNS{
        Enable: true,
        NameServer: []dns.NameServer{{Net:"",Addr:"223.5.5.5:53"}},
        NameServerPolicy: []dns.Policy{{
            Domain: "+.chatgpt.com",
            NameServers: []dns.NameServer{{
                Net:"https",Addr:"https://dns.google/dns-query",ProxyName:"AI-Auto",
            }},
        }},
    }
    direct := inspectDNSPolicy(cfg,"baidu.com")
    if direct.Route!="direct" || direct.Source!="main" {
        t.Fatalf("CN should use default direct DNS: %+v",direct)
    }
    overseas:= inspectDNSPolicy(cfg,"chatgpt.com")
    if overseas.Route!="proxy" || overseas.Proxy!="AI-Auto" || overseas.Source!="policy" {
        t.Fatalf("ChatGPT should use configured proxy DNS: %+v",overseas)
    }
    if !strings.Contains(overseas.Upstream,"dns.google") {
        t.Fatalf("upstream host missing: %+v",overseas)
    }
    subdomain:=inspectDNSPolicy(cfg,"api.chatgpt.com")
    if subdomain.Route!="proxy" {
        t.Fatalf("suffix policy should include subdomains: %+v",subdomain)
    }
}

func TestDNSDiagnosticDoesNotOverstateMixedAndRULES(t *testing.T) {
    direct:=dns.NameServer{Net:"udp",Addr:"1.1.1.1:53"}
    rules:=dns.NameServer{Net:"https",Addr:"https://dns.google/dns-query",ProxyName:dns.RespectRules}
    cfg:=&mihomoConfig.DNS{Enable:true,NameServer:[]dns.NameServer{direct,rules}}
    mixed:=inspectDNSPolicy(cfg,"claude.ai")
    if mixed.Route!="mixed" {t.Fatalf("mixed routes cannot be declared proxy: %+v",mixed)}
    cfg.NameServer=[]dns.NameServer{rules}
    unresolved:=inspectDNSPolicy(cfg,"grok.com")
    if unresolved.Route!="rules" {t.Fatalf("RULES must be reported as unresolved: %+v",unresolved)}
    cfg.Fallback=[]dns.NameServer{direct}
    fallback:=inspectDNSPolicy(cfg,"google.com")
    if fallback.Route!="mixed" || fallback.Source!="main+fallback" {
        t.Fatalf("fallback candidates cannot be labelled definite: %+v",fallback)
    }
}

func TestDNSDiagnosticNoLoadedConfiguration(t *testing.T) {
    result:=inspectDNSPolicy(nil,"baidu.com")
    if result.Route!="unknown" || result.Source!="unknown" {
        t.Fatalf("unknown config must not invent a route: %+v",result)
    }
}
