package routing

// Claude-specific rules supplement the upstream Anthropic geosite provider.
//
// The provider can update independently; these explicit rules cover CDN,
// authentication, telemetry and literal-IP requests from the Net.Coffee guide:
// https://ip.net.coffee/claude/site.html
//
// Shared telemetry and widget domains are intentionally routed to the same
// configured AI exit. They can also be used by unrelated services.
var claudeSuffixes = []string{
	"anthropic.com",
	"claude.ai",
	"claude.com",
	"clau.de",
	"claudemcpclient.com",
	"claudemcpcontent.com",
	"claudeusercontent.com",
	"sentry.io",
	"statsigapi.net",
	"intercom.io",
	"intercomcdn.com",
}

var claudeExactDomains = []string{
	"servd-anthropic-website.b-cdn.net",
	"anthropic.com.cdn.cloudflare.net",
	"anthropic.auth0.com",
	"anthropic-com.ghost.io",
	"browser-intake-us5-datadoghq.com",
	"cdn.usefathom.com",
}

var claudeKeywords = []string{"datadog", "sentry", "sift"}

var claudeIPRules = []string{
	"IP-CIDR,160.79.104.0/21",
	"IP-CIDR6,2607:6bc0::/32",
	"IP-ASN,399358",
}

func claudeRules(outbound string) []string {
	rules := make([]string, 0, len(claudeSuffixes)+len(claudeExactDomains)+len(claudeKeywords)+len(claudeIPRules))
	for _, domain := range claudeSuffixes {
		rules = append(rules, "DOMAIN-SUFFIX,"+domain+","+outbound)
	}
	for _, domain := range claudeExactDomains {
		rules = append(rules, "DOMAIN,"+domain+","+outbound)
	}
	for _, keyword := range claudeKeywords {
		rules = append(rules, "DOMAIN-KEYWORD,"+keyword+","+outbound)
	}
	for _, rule := range claudeIPRules {
		rules = append(rules, rule+","+outbound+",no-resolve")
	}
	// Deliberately do not proxy every NTP server. NTP synchronizes UTC time,
	// not the device timezone; a global NTP rule would alter unrelated traffic.
	return rules
}
