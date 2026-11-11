// Package startup supplies the fail-closed configuration used until a profile loads.
package startup

const SafeYAML = "mode: rule\nrules:\n  - MATCH,REJECT\n"
