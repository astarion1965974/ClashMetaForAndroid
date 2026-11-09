package main

//#include "bridge.h"
import "C"

import (
	"context"
	"time"
	"unsafe"

	"cfa/native/app"
	"cfa/native/config"
	"cfa/native/tunnel"
	"github.com/metacubex/mihomo/component/resolver"
	mihomoTunnel "github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
)

//export queryDnsDiagnostic
func queryDnsDiagnostic(domain C.c_string) *C.char {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	host := C.GoString(domain)
	policy := config.InspectDNSPolicy(host)
	if policy.Route=="proxy" && policy.Proxy!="" {
		if proxy:=mihomoTunnel.Proxies()[policy.Proxy];proxy!=nil {
			if group,ok:=proxy.Adapter().(outboundgroup.ProxyGroup);ok {
				policy.Proxy = policy.Proxy+" → "+group.Now()
			}
		}
	}
	start := time.Now()
	ips, err := resolver.LookupIP(ctx, host)
	addresses := make([]string, 0, len(ips))
	for _, ip := range ips {
		addresses = append(addresses, ip.String())
	}
	message := ""
	if err != nil {
		message = err.Error()
	}
	return marshalJson(struct {
		Addresses []string `json:"addresses"`
		Error string `json:"error"`
		Millis int64 `json:"millis"`
		DNSRoute string `json:"dnsRoute"`
		DNSProxy string `json:"dnsProxy"`
		DNSUpstream string `json:"dnsUpstream"`
		DNSSource string `json:"dnsSource"`
	}{addresses, message, time.Since(start).Milliseconds(),
		policy.Route,policy.Proxy,policy.Upstream,policy.Source})
}

//export queryTunnelState
func queryTunnelState() *C.char {
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	return marshalJson(response)
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	return marshalJson(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	return marshalJson(response)
}

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	go func(name string) {
		defer C.release_object(completable)
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}

//export healthCheckAll
func healthCheckAll() {
	tunnel.HealthCheckAll()
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export queryProviders
func queryProviders() *C.char {
	return marshalJson(tunnel.QueryProviders())
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	go func(pType, name string) {
		C.complete(completable, marshalString(tunnel.UpdateProvider(pType, name)))

		C.release_object(completable)
	}(C.GoString(pType), C.GoString(name))
}

//export suspend
func suspend(suspended C.int) {
	tunnel.Suspend(suspended != 0)
}
