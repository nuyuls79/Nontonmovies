package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Pahe : MainAPI() {

    override var mainUrl = "https://pahe.ink"

    override var name = "Pahe"

    override val hasMainPage = true

    override var lang = "id"

    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val headers = mapOf(
        "User-Agent" to
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(

        // TERBARU
        "$mainUrl/page/" to "🔥 Terbaru",

        // MOVIES
        "$mainUrl/action/" to "🎬 Action",
        "$mainUrl/adventure/" to "🗺 Adventure",
        "$mainUrl/animation/" to "🧸 Animation",
        "$mainUrl/comedy/" to "😂 Comedy",
        "$mainUrl/crime/" to "🕵 Crime",
        "$mainUrl/drama/" to "🎭 Drama",
        "$mainUrl/fantasy/" to "🧙 Fantasy",
        "$mainUrl/horror/" to "👻 Horror",
        "$mainUrl/mystery/" to "❓ Mystery",
        "$mainUrl/romance/" to "❤️ Romance",
        "$mainUrl/sci-fi/" to "🚀 Sci-Fi",
        "$mainUrl/thriller/" to "🔪 Thriller",

        // TV
        "$mainUrl/tv-shows/" to "📺 TV Shows",

        // DRAMA
        "$mainUrl/korean-drama/" to "🇰🇷 Korean Drama",
        "$mainUrl/chinese-drama/" to "🇨🇳 Chinese Drama",
        "$mainUrl/japanese-drama/" to "🇯🇵 Japanese Drama",
        "$mainUrl/thai-drama/" to "🇹🇭 Thai Drama",
        "$mainUrl/indian-drama/" to "🇮🇳 Indian Drama",

        // ANIME
        "$mainUrl/anime/" to "🎌 Anime"
    )

    private fun Element.toSearchResult(): SearchResponse? {

        val linkElement = selectFirst("a[href]")
            ?: return null

        val href = linkElement.attr("href")
            .trim()

        if (!href.startsWith(mainUrl))
            return null

        val title = selectFirst(
            """
            .tt,
            .entry-title,
            h1,
            h2,
            h3,
            img
            """.trimIndent()
        )?.let {

            when (it.tagName()) {
                "img" -> it.attr("alt")
                else -> it.text()
            }

        }?.trim()
            ?.replace("\n", " ")
            ?: return null

        if (title.length < 2)
            return null

        val poster = selectFirst("img")
            ?.let {
                it.attr("data-src").ifBlank {
                    it.attr("data-lazy-src").ifBlank {
                        it.attr("src").ifBlank {
                            it.attr("data-cfsrc")
                        }
                    }
                }
            }

        val type = if (
            title.contains("Season", true) ||
            title.contains("Episode", true) ||
            title.contains("TV", true)
        ) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieSearchResponse(
            title,
            href,
            type
        ) {
            posterUrl = poster
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (request.data.contains("/page/")) {
            request.data + page
        } else {
            if (page == 1) {
                request.data
            } else {
                request.data + "page/$page/"
            }
        }

        val doc = app.get(
            url,
            headers = headers
        ).document

        val home = doc.select(
            """
            article,
            article.post,
            div.post,
            div.bs,
            div.bsx,
            div.result-item,
            div.listupd article,
            li
            """.trimIndent()
        ).mapNotNull {
            it.toSearchResult()
        }.distinctBy {
            it.url
        }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val fixedQuery = query.replace(" ", "+")

        val doc = app.get(
            "$mainUrl/?s=$fixedQuery",
            headers = headers
        ).document

        return doc.select(
            """
            article,
            article.post,
            div.post,
            div.bs,
            div.bsx
            """.trimIndent()
        ).mapNotNull {
            it.toSearchResult()
        }.distinctBy {
            it.url
        }
    }

    override suspend fun load(
        url: String
    ): LoadResponse {

        val doc = app.get(
            url,
            headers = headers
        ).document

        val rawTitle = doc.selectFirst(
            "h1.entry-title, h1"
        )?.text()?.trim()
            ?: throw ErrorLoadingException("Title not found")

        val cleanTitle = rawTitle
            .replace(
                Regex(
                    "\\b(480p|720p|1080p|2160p|BluRay|WEB-DL|WEBRip|HDRip)\\b.*",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        val poster = doc.selectFirst(
            "div.entry-content img, img"
        )?.attr("src")

        val plot = doc.selectFirst(
            "div.entry-content p"
        )?.text()?.trim()

        val tags = doc.select(
            "a[rel=category tag], a[href*=genre]"
        ).map {
            it.text()
        }.filter {
            it.isNotBlank()
        }

        val year = Regex("(19|20)\\d{2}")
            .find(rawTitle)
            ?.value
            ?.toIntOrNull()

        val type = if (
            rawTitle.contains("Season", true) ||
            rawTitle.contains("Episode", true)
        ) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieLoadResponse(
            cleanTitle,
            url,
            type,
            url
        ) {
            posterUrl = poster
            this.plot = plot
            this.tags = tags
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(
            data,
            headers = headers
        ).document

        doc.select("a[href]").forEach { element ->

            val link = element.attr("href")

            if (
                link.contains("pixeldrain") ||
                link.contains("mega.nz") ||
                link.contains("mediafire") ||
                link.contains("gofile") ||
                link.contains("1fichier") ||
                link.contains("streamwish") ||
                link.contains("filelions") ||
                link.contains("vidhide") ||
                link.contains("streamtape") ||
                link.contains("drive.google")
            ) {

                loadExtractor(
                    link,
                    data,
                    subtitleCallback,
                    callback
                )
            }
        }

        doc.select("iframe").forEach {

            val src = it.attr("src")

            if (src.isNotBlank()) {

                loadExtractor(
                    fixUrl(src),
                    data,
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
}