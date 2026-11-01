package com.github.kr328.clash.service.model
import org.junit.Assert.*
import org.junit.Test
class MobilePolicyTest {
 @Test fun unknownWifiNeverBypasses() { assertFalse(MobilePolicy.pauseAtHome(true,true,null,listOf("Home"),false));assertFalse(MobilePolicy.pauseAtHome(true,true,"<unknown ssid>",listOf("<unknown ssid>"),false)) }
 @Test fun onlyBoundWifiPauses() { assertTrue(MobilePolicy.pauseAtHome(true,true,"Home",listOf("Home"),false));assertFalse(MobilePolicy.pauseAtHome(true,true,"Cafe",listOf("Home"),false)) }
 @Test fun lockdownAndDisabledKeepVpn() { assertFalse(MobilePolicy.pauseAtHome(true,true,"Home",listOf("Home"),true));assertFalse(MobilePolicy.pauseAtHome(false,true,"Home",listOf("Home"),false));assertFalse(MobilePolicy.pauseAtHome(true,false,"Home",listOf("Home"),false)) }
 @Test fun freshCellUpdateDeferred() { assertFalse(MobilePolicy.allowUpdate(true,true,true,true,false,1000)) }
 @Test fun expiryChargingAndWifiAllowUpdate() { assertTrue(MobilePolicy.allowUpdate(true,true,true,true,false,72*3600000L));assertTrue(MobilePolicy.allowUpdate(true,true,true,true,true,0));assertTrue(MobilePolicy.allowUpdate(true,true,true,false,false,0)) }
 @Test fun offlineNeverUpdatesInPreset() { assertFalse(MobilePolicy.allowUpdate(true,true,false,false,true,99*3600000L)) }
 @Test fun legacyCanOptOut() { assertTrue(MobilePolicy.allowUpdate(false,true,true,true,false,0)) }
 @Test fun stoppedPageDoesNotPoll() { assertFalse(MobilePolicy.shouldRefreshHome(false,true));assertFalse(MobilePolicy.shouldRefreshHome(true,false));assertTrue(MobilePolicy.shouldRefreshHome(true,true)) }
}
