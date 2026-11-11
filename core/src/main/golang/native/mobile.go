package main

//#include "bridge.h"
import "C"
import (
	"cfa/native/directhistory"
	"cfa/native/mobile"
	nativeTunnel "cfa/native/tunnel"
	"context"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
	"time"
	"unsafe"
)

//export mobileState
func mobileState(online, screen, metered, charge, paused C.int) {
	mobile.SetState(online != 0, screen != 0, metered != 0, charge != 0, paused != 0)
}

//export querySelection
func querySelection(name C.c_string) *C.char {
	p := tunnel.Proxies()[C.GoString(name)]
	if p == nil {
		return nil
	}
	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil
	}
	n := g.Now()
	child := false
	if selected := tunnel.Proxies()[n]; selected != nil {
		_, child = selected.Adapter().(outboundgroup.ProxyGroup)
	}
	return marshalJson(struct {
		Type  string `json:"type"`
		Now   string `json:"now"`
		Child bool   `json:"child"`
	}{g.Type().String(), n, child})
}

//export probeSelection
func probeSelection(completable unsafe.Pointer, name C.c_string) {
	n := C.GoString(name)
	go func() {
		defer C.release_object(completable)
		p := tunnel.Proxies()[n]
		for i := 0; i < 32 && p != nil; i++ {
			if _, group := p.Adapter().(outboundgroup.ProxyGroup); !group {
				break
			}
			if p.Type() == constant.LoadBalance || p.Type() == constant.Relay {
				C.complete(completable, nil)
				return
			}
			p = p.Unwrap(&constant.Metadata{}, false)
		}
		var err error
		if p != nil && p.Type() != constant.Direct && p.Type() != constant.Reject {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			expected, _ := utils.NewUnsignedRanges[uint16]("204")
			_, err = p.URLTest(ctx, "https://www.gstatic.com/generate_204", expected)
		}
		C.complete(completable, marshalString(err))
	}()
}

//export releaseConnections
func releaseConnections() { nativeTunnel.CloseAllConnections() }

//export queryDirectHistory
func queryDirectHistory() *C.char { return marshalJson(directhistory.Snapshot()) }
