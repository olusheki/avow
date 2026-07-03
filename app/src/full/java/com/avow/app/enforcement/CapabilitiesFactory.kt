package com.avow.app.enforcement

import android.content.Context

/** Full flavor: real device-owner capabilities. */
object CapabilitiesFactory {
    fun create(context: Context): EnforcementCapabilities = FullEnforcementCapabilities(context)
}
