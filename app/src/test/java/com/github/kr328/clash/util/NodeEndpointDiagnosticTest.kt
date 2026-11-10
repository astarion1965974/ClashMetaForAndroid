package com.github.kr328.clash.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NodeEndpointDiagnosticTest {
    @Test fun parsesBlockNodesWithoutReadingSecrets() {
        val yaml = """
            proxies:
              - name: Japan
                type: anytls
                server: node.example.org
                port: 443
                password: secret-not-for-report
                sni: edge.example.org
              - name: UDP
                type: tuic
                server: udp.example.org
                port: 4433
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val node = NodeEndpointDiagnostic.parseProxy(yaml, "Japan")!!
        assertEquals("anytls", node.type)
        assertEquals("node.example.org", node.server)
        assertEquals("edge.example.org", node.sni)
        assertEquals(443, node.port)
        assertNull(NodeEndpointDiagnostic.parseProxy(yaml, "missing"))
        assertFalse(NodeEndpointDiagnostic.isTcp("tuic"))
    }

    @Test fun parsesInlineClashNodes() {
        val yaml = """
            proxies:
              - {name: 'Tokyo A', type: anytls, server: abc.example.net, port: 8443, password: x}
              - {name: 'Tokyo B', type: tuic, server: def.example.net, port: 443}
            proxy-groups:
              - name: select
        """.trimIndent()
        assertEquals(8443, NodeEndpointDiagnostic.parseProxy(yaml,"Tokyo A")!!.port)
        assertEquals("def.example.net",NodeEndpointDiagnostic.parseProxy(yaml,"Tokyo B")!!.server)
    }

    @Test fun parsesProviderInlineYamlWithSpaceAfterBraceAndFindsAnyTlsControl() {
        val yaml = """
            mixed-port: 7890
            proxies:
                - { name: '日本[1.5x]-高端B1', type: tuic, server: udp.example.org, port: 443, uuid: hidden, password: secret, sni: udp.example.org }
                - { name: '香港[20x]-专线A', type: anytls, server: hk.example.org, port: 65535, password: secret }
                - { name: '日本[20x]-联移专线', type: anytls, server: tokyo.example.org, port: 8443, password: secret, sni: tokyo.example.org }
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val selected = NodeEndpointDiagnostic.parseProxy(yaml, "日本[1.5x]-高端B1")!!
        assertEquals("tuic", selected.type)
        assertEquals("udp.example.org", selected.server)
        val control = NodeEndpointDiagnostic.findAnyTlsControl(
            yaml, "日本[1.5x]-高端B1",
        )!!
        assertEquals("日本[20x]-联移专线", control.nodeName)
        assertEquals("anytls", control.type)
        assertEquals(8443, control.port)
        assertFalse(NodeEndpointDiagnostic.isTcp(selected.type))
    }

    @Test fun rejectsMissingAndInvalidAddress() {
        val yaml = """
            proxies:
              - name: invalid
                server: https://example.com/secret
                port: 443
        """.trimIndent()
        assertNull(NodeEndpointDiagnostic.parseProxy(yaml,"invalid"))
    }
}