package directhistory

import (
	"fmt"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
	"testing"
	"time"
)

func TestHistoryIsBoundedAndKeepsOnlyDirect(t *testing.T) {
	mu.Lock()
	entries = nil
	mu.Unlock()
	for i := 0; i < 105; i++ {
		record(&statistic.TrackerInfo{Start: time.Unix(int64(i), 0), Metadata: &C.Metadata{Host: fmt.Sprintf("host-%d", i)}, Chain: C.Chain{"DIRECT"}, Rule: "DomainSuffix", RulePayload: "example.cn"})
	}
	record(&statistic.TrackerInfo{Metadata: &C.Metadata{Host: "proxy.example"}, Chain: C.Chain{"Proxy"}})
	got := Snapshot()
	if len(got) != 100 || got[0].Target != "host-104" || got[99].Target != "host-5" || got[0].Payload != "example.cn" {
		t.Fatalf("unexpected history %#v", got)
	}
}
