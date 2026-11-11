package routing

// ProbeState distinguishes a failed probe from a node that has not yet been tested.
type ProbeState struct {
	Candidate bool
	Tested    bool
	Alive     bool
}

// ProtectedUnavailable is true only for an empty group, or when every real
// candidate has a recorded failed probe. Untested nodes remain unknown.
func ProtectedUnavailable(states []ProbeState) bool {
	for _, s := range states {
		if !s.Candidate {
			continue
		}
		if !s.Tested || s.Alive {
			return false
		}
	}
	return true
}
