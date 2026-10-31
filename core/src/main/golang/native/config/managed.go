package config

import (
	"encoding/json"
	"os"
	"path/filepath"

	"cfa/native/routing"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
)

func patchManagedRouting(cfg *config.RawConfig, _ string) error {
	data, err := os.ReadFile(C.Path.Resolve("managed-routing.json"))
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	var settings routing.Settings
	if err = json.Unmarshal(data, &settings); err != nil {
		return err
	}
	return routing.Apply(cfg, settings, filepath.Join(C.Path.HomeDir(), "managed-rules"))
}
