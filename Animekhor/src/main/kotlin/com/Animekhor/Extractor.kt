package com.Animekhor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class embedwish : StreamWishExtractor() {
    override var mainUrl = "https://embedwish.com"
}

class P2pstream : VidStack() {
    override var mainUrl = "https://animekhor.p2pstream.vip"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://filelions.live"
}

class Swhoi : StreamWishExtractor() {
    override var mainUrl = "https://swhoi.com"
    override val requiresReferer = true
}

class VidHidePro5 : VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
    override val requiresReferer = true
}

class PlayerDonghuaworld : Rumble() {
    override var mainUrl = "https://player.donghuaworld.in"
    override val requiresReferer = true
}

open class Rumble : ExtractorApi() {

    override var name = "Rumble"

    override var mainUrl = "https://rumble.com"

    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val response = app.get(
            url,
            referer = referer ?: "$mainUrl/"
        )

        val document = response.document

        val playerScript = document
            .selectFirst("script:containsData(jwplayer)")
            ?.data()
            ?: return

        val sourceRegex =
            """"file"\s*:\s*"(https:[^"]+\.(?:mp4|m3u8)[^"]*)""""
                .toRegex()

        val sources = sourceRegex.findAll(playerScript)

        var counter = 1

        for (source in sources) {

            val fileUrl = source.groupValues[1]
                .replace("\\/", "/")

            if (fileUrl.contains(".mp4")) {

                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name Video Server $counter",
                        fileUrl,
                        INFER_TYPE
                    ) {
                        this.referer = ""
                        this.quality = getQualityFromName("")
                    }
                )

            } else {

                val m3u8Links =
                    M3u8Helper.generateM3u8(
                        name,
                        fileUrl,
                        mainUrl
                    )

                m3u8Links.forEach {
                    callback.invoke(it)
                }
            }

            counter++
        }

        val rumbleId = url
            .substringAfter("/embed/v")
            .substringBefore("/")

        if (rumbleId.isNotBlank()) {

            val fallback =
                "$mainUrl/hls-vod/$rumbleId/playlist.m3u8?u=0&b=0"

            val fallbackLinks =
                M3u8Helper.generateM3u8(
                    name,
                    fallback,
                    mainUrl
                )

            fallbackLinks.forEach {
                callback.invoke(it)
            }
        }

        val trackRegex =
            """"file"\s*:\s*"(https:[^"]+\.vtt[^"]*)"\s*,\s*"label"\s*:\s*"([^"]+)""""
                .toRegex()

        val tracks = trackRegex.findAll(playerScript)

        for (track in tracks) {

            val fileUrl = track.groupValues[1]
                .replace("\\/", "/")

            val label = track.groupValues[2]

            subtitleCallback.invoke(
                newSubtitleFile(
                    label,
                    fileUrl
                )
            )
        }
    }
}