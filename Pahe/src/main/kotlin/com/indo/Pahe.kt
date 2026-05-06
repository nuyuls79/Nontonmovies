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
        "$mainUrl/category/action/page/" to "🎬 Action",
        "$mainUrl/category/adventure/page/" to "🗺 Adventure",
        "$mainUrl/category/animation/page/" to "🧸 Animation",
        "$mainUrl/category/comedy/page/" to "😂 Comedy",
        "$mainUrl/category/crime/page/" to "🕵 Crime",
        "$mainUrl/category/drama/page/" to "🎭 Drama",
        "$mainUrl/category/fantasy/page/" to "🧙 Fantasy",
        "$mainUrl/category/horror/page/" to "👻 Horror",
        "$mainUrl/category/mystery/page/" to "❓ Mystery",
        "$mainUrl/category/romance/page/" to "❤️ Romance",
        "$mainUrl/category/sci-fi/page/" to "🚀 Sci-Fi",
        "$mainUrl/category/thriller/page/" to "🔪 Thriller",

        // TV
        "$mainUrl/category/tv-shows/page/" to "📺 TV Shows",

        // DRAMA
        "$mainUrl/category/korean-drama/page/" to "🇰🇷 Korean Drama",
        "$mainUrl/category/japanese-drama/page/" to "🇯🇵 Japanese Drama",
        "$mainUrl/category/chinese-drama/page/" to "🇨🇳 Chinese Drama",
        "$mainUrl/category/thai-drama/page/" to "🇹🇭 Thai Drama",

        // ANIME
        "$mainUrl/category/anime/page/" to "🎌 Anime"
    )

    private fun Element.toSearchResult(): SearchResponse? {

        val title = selectFirst(
            ".tt, h1 a, h2 a, h3 a, .entry-title"
        )?.text()?.trim()
            ?: return null

        val href = selectFirst("a")
            ?.attr("href")
            ?.trim()
            ?: return null

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
            title.contains("S01", true) ||
            title.contains("S02", true) ||
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

        val url = request.data + page

        val doc = app.get(
            url,
            headers = headers
        ).document

        val home = doc.select(
            "div.bsx, article, div.post-item, div.item, div.grid-item"
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
            "div.bsx, article, div.post-item, div.item, div.grid-item"
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
            "div.entry-content img, .thumb img, img"
        )?.attr("src")

        val plot = doc.selectFirst(
            "div.entry-content p, .entry-content p"
        )?.text()?.trim()

        val tags = doc.select(
            "a[rel=category tag], a[href*=category]"
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