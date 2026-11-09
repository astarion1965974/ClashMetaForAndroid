package config

import "cfa/native/diagnostic"

// This wrapper reads the atomically published effective DNS configuration;
// the policy matcher remains independently testable without Android build tags.
func InspectDNSPolicy(domain string) diagnostic.DNSDiagnosticPolicy {
    return diagnostic.InspectDNSPolicy(activeDNSConfig.Load(),domain)
}
