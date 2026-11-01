package mobile

import (
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/component/resource"
	"sync/atomic"
	"time"
)

var enabled atomic.Bool
var wifiUpdates atomic.Bool
var networkState atomic.Uint32

const (
	ready       uint32 = 1
	connected   uint32 = 2
	interactive uint32 = 4
	metered     uint32 = 8
	charging    uint32 = 16
	paused      uint32 = 32
)

func SetState(online, screen, costly, charge, pause bool) {
	s := ready
	if online {
		s |= connected
	}
	if screen {
		s |= interactive
	}
	if costly {
		s |= metered
	}
	if charge {
		s |= charging
	}
	if pause {
		s |= paused
	}
	networkState.Store(s)
}
func HealthAllowed() bool {
	if !enabled.Load() {
		return true
	}
	s := networkState.Load()
	return s&connected != 0 && s&interactive != 0 && s&paused == 0
}
func UpdateAllowed(updated time.Time) bool {
	if !enabled.Load() {
		return true
	}
	s := networkState.Load()
	if s&connected == 0 {
		return false
	}
	return !wifiUpdates.Load() || s&metered == 0 || s&charging != 0 || time.Since(updated) >= 72*time.Hour
}
func DialCheckAllowed() bool {
	if !enabled.Load() {
		return true
	}
	s := networkState.Load()
	return s&connected != 0 && s&paused == 0
}
func init() {
	enabled.Store(true)
	wifiUpdates.Store(true)
	provider.BackgroundHealthCheckAllowed = HealthAllowed
	outboundgroup.BackgroundDialCheckAllowed = DialCheckAllowed
	resource.BackgroundUpdateAllowed = UpdateAllowed
}

func SetInteractive(screen bool) {
	for {
		old := networkState.Load()
		next := old &^ interactive
		if screen {
			next |= interactive
		}
		if networkState.CompareAndSwap(old, next) {
			return
		}
	}
}
