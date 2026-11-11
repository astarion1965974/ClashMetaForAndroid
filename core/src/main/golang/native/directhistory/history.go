// Package directhistory stores only a bounded, in-memory sample of successful
// DIRECT connections that entered Mihomo. It cannot observe bypassed traffic.
package directhistory

import (
	"sync"
	"time"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

type Entry struct {
	Time    time.Time `json:"time"`
	Target  string    `json:"target"`
	Rule    string    `json:"rule"`
	Payload string    `json:"payload"`
}

const limit = 100

var mu sync.Mutex
var entries []Entry

func record(i *statistic.TrackerInfo) {
	if i == nil || i.Metadata == nil || len(i.Chain) == 0 {
		return
	}
	name := i.Chain[0]
	direct := name == "DIRECT"
	if p := tunnel.Proxies()[name]; p != nil && p.Type() == C.Direct {
		direct = true
	}
	if !direct {
		return
	}
	target := i.Metadata.Host
	if target == "" {
		target = i.Metadata.SniffHost
	}
	if target == "" && i.Metadata.DstIP.IsValid() {
		target = i.Metadata.DstIP.String()
	}
	if target == "" {
		target = i.Metadata.RemoteDst
	}
	e := Entry{Time: i.Start, Target: target, Rule: i.Rule, Payload: i.RulePayload}
	mu.Lock()
	defer mu.Unlock()
	if len(entries) >= limit {
		copy(entries, entries[1:])
		entries = entries[:limit-1]
	}
	entries = append(entries, e)
}
func Clear() {
	mu.Lock()
	defer mu.Unlock()
	entries = nil
}

func Snapshot() []Entry {
	mu.Lock()
	defer mu.Unlock()
	result := make([]Entry, len(entries))
	for i := range entries {
		result[i] = entries[len(entries)-1-i]
	}
	return result
}
func init() { statistic.TrackOpened = record }
