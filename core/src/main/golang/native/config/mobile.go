package config

import (
	"cfa/native/mobile"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
)

func patchMobile(cfg *config.RawConfig, _ string) error {
	s, err := mobile.Read(C.Path.Resolve("mobile-network.json"))
	if err != nil {
		return err
	}
	return mobile.Apply(cfg, s)
}
