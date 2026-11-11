package com.github.kr328.clash.service.model

/** Protected profiles must capture both address families and never allow app bypass. */
data class VpnCapturePolicy(
    val mode: AccessControlMode,
    val ipv6: Boolean,
    val bypassPrivate: Boolean,
    val allowBypass: Boolean,
    val hijackDns: Boolean,
) {
    companion object {
        fun resolve(protected: Boolean, mode: AccessControlMode, ipv6: Boolean,
                    bypassPrivate: Boolean, allowBypass: Boolean, hijackDns: Boolean) =
            VpnCapturePolicy(
                mode = if (protected) AccessControlMode.AcceptAll else mode,
                ipv6 = protected || ipv6,
                bypassPrivate = !protected && bypassPrivate,
                allowBypass = !protected && allowBypass,
                hijackDns = protected || hijackDns,
            )
    }
}
