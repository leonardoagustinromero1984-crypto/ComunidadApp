package com.comunidapp.app.domain.onboarding.onb02

/**
 * Tutorial-progress keys that are not part of the T00–T20 catalog.
 * Used to persist ONB-02 completion across reinstall without a new table.
 */
object Onb02RemoteKeys {
    const val FLOW = "onb02_flow"
    const val FLOW_VERSION = "1"
}
