package startup

import (
	"github.com/metacubex/mihomo/config"
	_ "github.com/metacubex/mihomo/hub/executor"
	T "github.com/metacubex/mihomo/tunnel"
	"testing"
)

func TestStartupBlocksUntilProfileLoads(t *testing.T) {
	cfg, err := config.Parse([]byte(SafeYAML))
	if err != nil {
		t.Fatal(err)
	}
	if cfg.General.Mode != T.Rule || len(cfg.Rules) != 1 || cfg.Rules[0].Adapter() != "REJECT" {
		t.Fatal("startup must reject traffic before profile load")
	}
}
