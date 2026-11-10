package com.github.kr328.clash.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test fun obscuresSubscriptionPathAndQuery() {
        val source = "Get https://yyds.sbyun.org/sby/secret123?token=abc: EOF"
        val report = DiagnosticRedactor.clean(source)
        assertTrue(report.contains("yyds.sbyun.org"))
        assertFalse(report.contains("secret123"))
        assertFalse(report.contains("token=abc"))
        assertTrue(report.contains("[REDACTED]"))
    }

    @Test fun obscuresCredentialsAndUuid() {
        val source = "password: abc123 uuid: 123e4567-e89b-12d3-a456-426614174000"
        val report = DiagnosticRedactor.clean(source)
        assertFalse(report.contains("abc123"))
        assertFalse(report.contains("123e4567"))
        assertTrue(report.contains("[REDACTED]"))
    }

    @Test fun retainsUsableErrorType() {
        val source = "SSLHandshakeException: Connection closed"
        assertTrue(DiagnosticRedactor.clean(source).contains("SSLHandshakeException"))
    }
}
