package tunnel

import "cfa/native/mobile"

// Suspend maintenance, never the packet tunnel: push/voice traffic must keep working.
func Suspend(s bool) { mobile.SetInteractive(!s) }
