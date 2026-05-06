package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

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

    private fun parseResults(document: Document): List<SearchResponse> {

        val results = mutableListOf<SearchResponse>()

        val containers = document.select(
            "article, li, div.post, div.item, div.type-post"
        )

        for (item in containers) {

            val aTag = item.selectFirst("a[href]") ?: continue

            val href = aTag.attr("href").trim()

            if (!href.startsWith(mainUrl))
                continue

            val title =
                item.selectFirst(
                    "h1, h2, h3, h4, .entry-title, .post-title"
                )?.text()?.trim()
                    ?: aTag.attr("title")
                        .ifBlank {
                            aTag.text().trim()
                        }

            if (title.isBlank())
                continue

            val img = item.selectFirst("img")

            var poster: String? = null

            if (img != null) {

                poster = when {

                    img.attr("data-src").isNotBlank() ->
                        img.attr("data-src")

                    img.attr("data-lazy-src").isNotBlank() ->
                        img.attr("data-lazy-src")

                    img.attr("data-lazy-loaded").isNotBlank() ->
                        img.attr("data-lazy-loaded")

                    img.attr("data-original").isNotBlank() ->
                        img.attr("data-original")

                    img.attr("srcset").isNotBlank() ->
                        img.attr("srcset")
                            .split(",")
                            .firstOrNull()
                            ?.trim()
                            ?.split(" ")
                            ?.firstOrNull()

                    img.attr("src").isNotBlank() &&
                            !img.attr("src").contains("data:image") ->
                        img.attr("src")

                    else -> null
                }
            }

            // fallback background-image
            if (poster.isNullOrBlank()) {

                val style = item.attr("style")

                val match = Regex(
                    """url\((.*?)\)"""
                ).find(style)

                poster = match
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace("\"", "")
                    ?.replace("'", "")
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

        return results.distinctBy {
            it.url
        }
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

        val links = mutableSetOf<String>()

        doc.select("a[href]").forEach { element ->

            val href = element.attr("href").trim()

            if (
                href.contains("pixeldrain", true) ||
                href.contains("mega.nz", true) ||
                href.contains("mediafire", true) ||
                href.contains("gofile", true) ||
                href.contains("1fichier", true) ||
                href.contains("streamwish", true) ||
                href.contains("filelions", true) ||
                href.contains("vidhide", true) ||
                href.contains("streamtape", true) ||
                href.contains("drive.google", true) ||
                href.contains("dood", true) ||
                href.contains("streamsb", true) ||
                href.contains("sbplay", true) ||
                href.contains("watchsb", true) ||
                href.contains("mixdrop", true) ||
                href.contains("mp4upload", true)
            ) {
                links.add(
                    fixUrl(href)
                )
            }
        }

        doc.select("iframe[src]").forEach {

            val src = it.attr("src").trim()

            if (src.isNotBlank()) {
                links.add(
                    fixUrl(src)
                )
            }
        }

        doc.select(
            "button[onclick], div[onclick]"
        ).forEach {

            val onclick = it.attr("onclick")

            Regex(
                """https?:\/\/[^\s'"]+"""
            ).findAll(onclick)
                .forEach { match ->
                    links.add(match.value)
                }
        }

        if (links.isEmpty()) {
            return false
        }

        links.forEach { link ->

            try {

                loadExtractor(
                    link,
                    data,
                    subtitleCallback,
                    callback
                )

            } catch (_: Exception) {
            }
        }

        return true
    }
}