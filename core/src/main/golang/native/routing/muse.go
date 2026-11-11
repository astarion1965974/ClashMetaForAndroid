package routing

// Meta Muse (muse.ai) and Muse Code / Meta Model API (api.meta.ai).
// See https://muse.ai/ and https://dev.meta.ai/docs/overview.
//
// Only service-specific hosts are included. Routing all facebook.com,
// instagram.com or whatsapp.com through Muse's selected exit would also
// reroute unrelated Meta traffic and potentially disrupt existing sessions.
var museSuffixes = []string{
	"muse.ai",
	"meta.ai",
}

var museExactDomains = []string{
	"ai.meta.com",
	"accounts.meta.com",
}

func museRules(outbound string) []string {
	rules := make([]string, 0, len(museSuffixes)+len(museExactDomains))
	for _, domain := range museSuffixes {
		rules = append(rules, "DOMAIN-SUFFIX,"+domain+","+outbound)
	}
	for _, domain := range museExactDomains {
		rules = append(rules, "DOMAIN,"+domain+","+outbound)
	}
	return rules
}
