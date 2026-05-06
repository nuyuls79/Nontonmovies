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

        "$mainUrl/page/" to "🔥 Terbaru",

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

        "$mainUrl/tv-shows/" to "📺 TV Shows",

        "$mainUrl/korean-drama/" to "🇰🇷 Korean Drama",
        "$mainUrl/japanese-drama/" to "🇯🇵 Japanese Drama",
        "$mainUrl/chinese-drama/" to "🇨🇳 Chinese Drama",
        "$mainUrl/thai-drama/" to "🇹🇭 Thai Drama",
        "$mainUrl/indian-drama/" to "🇮🇳 Indian Drama"
    )

    private fun parseResults(document: org.jsoup.nodes.Document): List<SearchResponse> {

        val results = mutableListOf<SearchResponse>()

        val containers = document.select(
            "article, li, div.post, div.item, div.type-post"
        )

        for (item in containers) {

            val aTag = item.selectFirst("a[href]") ?: continue

            val href = aTag.attr("href")
            if (!href.startsWith(mainUrl)) continue

            val title =
                item.selectFirst("h1, h2, h3, h4, .entry-title, .post-title")
                    ?.text()
                    ?.trim()
                    ?: aTag.attr("title")
                        .ifBlank { aTag.text().trim() }

            if (title.isBlank()) continue

            val img = item.selectFirst("img")

            val poster = when {
                img == null -> null

                img.attr("data-src").isNotBlank() ->
                    img.attr("data-src")

                img.attr("data-lazy-src").isNotBlank() ->
                    img.attr("data-lazy-src")

                img.attr("data-lazy-loaded").isNotBlank() ->
                    img.attr("data-lazy-loaded")

                img.attr("srcset").isNotBlank() ->
                    img.attr("srcset")
                        .split(",")
                        .firstOrNull()
                        ?.trim()
                        ?.split(" ")
                        ?.firstOrNull()

                img.attr("src").isNotBlank() ->
                    img.attr("src")

                else -> null
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

            results.add(
                newMovieSearchResponse(
                    title,
                    href,
                    type
                ) {
                    posterUrl = fixUrlNull(poster)
                }
            )
        }

        return results.distinctBy { it.url }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (
            request.data.contains("/page/")
        ) {
            request.data + page
        } else {
            request.data
        }

        val doc = app.get(
            url,
            headers = headers
        ).document

        val home = parseResults(doc)

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

        return parseResults(doc)
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

        val posterImg = doc.selectFirst("img")

        val poster = when {
            posterImg == null -> null

            posterImg.attr("data-src").isNotBlank() ->
                posterImg.attr("data-src")

            posterImg.attr("data-lazy-src").isNotBlank() ->
                posterImg.attr("data-lazy-src")

            posterImg.attr("src").isNotBlank() ->
                posterImg.attr("src")

            else -> null
        }

        val plot = doc.selectFirst(
            ".entry-content p, p"
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
            posterUrl = fixUrlNull(poster)
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