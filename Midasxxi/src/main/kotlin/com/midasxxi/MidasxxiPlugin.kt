package com.midasxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.registerMainAPI
import com.lagradost.cloudstream3.plugins.registerExtractorAPI

@CloudstreamPlugin
class MidasxxiPlugin : Plugin() {

    override fun load() {
        registerMainAPI(Midasxxi())
        registerExtractorAPI(Playcinematic())
    }
}
