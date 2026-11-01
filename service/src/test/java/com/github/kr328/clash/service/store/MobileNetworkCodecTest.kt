package com.github.kr328.clash.service.store
import org.junit.Assert.*
import org.junit.Test
class MobileNetworkCodecTest {
 @Test fun explicitSerializerRoundTrip() { val s=MobileNetworkSettings(stableNodes=listOf("东京 [1]","东京 2"),aiNodes=listOf("Tokyo 2"),trustedWifiEnabled=true,trustedSSIDs=listOf("Home\"SSID"));assertEquals(s,MobileNetworkCodec.decode(MobileNetworkCodec.encode(s))) }
 @Test fun futureFieldsAndDefaults() { val s=MobileNetworkCodec.decode("{\"enabled\":false,\"future\":42}");assertFalse(s.enabled);assertTrue(s.secureDns);assertFalse(s.trustedWifiEnabled) }
 @Test fun savedSnapshotDoesNotShareMutableSelection() { val s=MobileNetworkSettings(stableNodes=listOf("A"));val old=s.copy();s.stableNodes=s.stableNodes+"B";assertEquals(listOf("A"),old.stableNodes) }
}
