package com.avow.app.enforcement

import android.content.Context

/** Lite flavor: device-owner powers are unavailable (no-op capabilities). */
object CapabilitiesFactory {
    fun create(context: Context): EnforcementCapabilities = LiteEnforcementCapabilities(context)
}
