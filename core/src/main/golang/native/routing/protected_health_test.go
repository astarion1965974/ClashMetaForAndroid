package routing

import "testing"

func TestProtectedUnavailableUsesObservedProbes(t *testing.T) {
	cases := []struct {
		states []ProbeState
		want   bool
	}{
		{nil, true},
		{[]ProbeState{{Candidate: false}}, true},
		{[]ProbeState{{Candidate: true}}, false},
		{[]ProbeState{{Candidate: true, Tested: true, Alive: false}}, true},
		{[]ProbeState{{Candidate: true, Tested: true}, {Candidate: true, Tested: true, Alive: true}}, false},
		{[]ProbeState{{Candidate: true, Tested: true}, {Candidate: true, Tested: false}}, false},
	}
	for _, c := range cases {
		if got := ProtectedUnavailable(c.states); got != c.want {
			t.Fatalf("%+v -> %v", c.states, got)
		}
	}
}
