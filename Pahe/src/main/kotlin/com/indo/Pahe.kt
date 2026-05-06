package com.indo

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

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

        "$mainUrl/wp-json/wp/v2/posts?categories=254&per_page=20&page=" to "🔥 Terbaru",

        "$mainUrl/wp-json/wp/v2/posts?categories=37&per_page=20&page=" to "🎬 Action",
        "$mainUrl/wp-json/wp/v2/posts?categories=48&per_page=20&page=" to "🗺 Adventure",
        "$mainUrl/wp-json/wp/v2/posts?categories=38&per_page=20&page=" to "🧸 Animation",
        "$mainUrl/wp-json/wp/v2/posts?categories=39&per_page=20&page=" to "😂 Comedy",
        "$mainUrl/wp-json/wp/v2/posts?categories=50&per_page=20&page=" to "🕵 Crime",
        "$mainUrl/wp-json/wp/v2/posts?categories=51&per_page=20&page=" to "🎭 Drama",
        "$mainUrl/wp-json/wp/v2/posts?categories=52&per_page=20&page=" to "🧙 Fantasy",
        "$mainUrl/wp-json/wp/v2/posts?categories=43&per_page=20&page=" to "👻 Horror",
        "$mainUrl/wp-json/wp/v2/posts?categories=55&per_page=20&page=" to "❓ Mystery",
        "$mainUrl/wp-json/wp/v2/posts?categories=45&per_page=20&page=" to "❤️ Romance",
        "$mainUrl/wp-json/wp/v2/posts?categories=56&per_page=20&page=" to "🚀 Sci-Fi",
        "$mainUrl/wp-json/wp/v2/posts?categories=57&per_page=20&page=" to "🔪 Thriller",

        "$mainUrl/wp-json/wp/v2/posts?categories=439&per_page=20&page=" to "📺 TV Shows",
        "$mainUrl/wp-json/wp/v2/posts?categories=872&per_page=20&page=" to "📡 Ongoing TV"
    )

    data class WPPost(
        val id: Int? = null,
        val link: String? = null,
        val title: Rendered? = null
    )

    data class Rendered(
        val rendered: String? = null
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = request.data + page

        val response = app.get(
            url,
            headers = headers
        ).text

        val posts = try {

            mapper.readValue<List<WPPost>>(response)

        } catch (_: Exception) {

            emptyList()
        }

        val home = posts.mapNotNull { post ->

            try {

                val link = post.link ?: return@mapNotNull null

                val title = Jsoup
                    .parse(post.title?.rendered ?: "")
                    .text()

                if (title.isBlank()) return@mapNotNull null

                val detailDoc = app.get(
                    link,
                    headers = headers
                ).document

                var poster = detailDoc.selectFirst(
                    "meta[property=og:image]"
                )?.attr("content")

                if (poster.isNullOrBlank()) {
                    poster = detailDoc.selectFirst(
                        ".entry-content img"
                    )?.attr("src")
                }

                if (poster.isNullOrBlank()) {
                    poster = detailDoc.selectFirst(
                        "img"
                    )?.attr("src")
                }

                val type = if (
                    title.contains("Season", true) ||
                    title.contains("Episode", true) ||
                    title.contains("S0", true)
                ) {
                    TvType.TvSeries
                } else {
                    TvType.Movie
                }

                newMovieSearchResponse(
                    title,
                    link,
                    type
                ) {
                    posterUrl = poster
                }

            } catch (_: Exception) {
                null
            }
        }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val url =
            "$mainUrl/wp-json/wp/v2/posts?search=${query}&per_page=20"

        val response = app.get(
            url,
            headers = headers
        ).text

        val posts = try {

            mapper.readValue<List<WPPost>>(response)

        } catch (_: Exception) {

            emptyList()
        }

        return posts.mapNotNull { post ->

            try {

                val link = post.link ?: return@mapNotNull null

                val title = Jsoup
                    .parse(post.title?.rendered ?: "")
                    .text()

                if (title.isBlank()) return@mapNotNull null

                val detailDoc = app.get(
                    link,
                    headers = headers
                ).document

                var poster = detailDoc.selectFirst(
                    "meta[property=og:image]"
                )?.attr("content")

                if (poster.isNullOrBlank()) {
                    poster = detailDoc.selectFirst(
                        ".entry-content img"
                    )?.attr("src")
                }

                val type = if (
                    title.contains("Season", true) ||
                    title.contains("Episode", true) ||
                    title.contains("S0", true)
                ) {
                    TvType.TvSeries
                } else {
                    TvType.Movie
                }

                newMovieSearchResponse(
                    title,
                    link,
                    type
                ) {
                    posterUrl = poster
                }

            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun load(
        url: String
    ): LoadResponse {

        val doc = app.get(
            url,
            headers = headers
        ).document

        val title = doc.selectFirst(
            "h1.entry-title"
        )?.text()?.trim()
            ?: "Unknown"

        var poster = doc.selectFirst(
            "meta[property=og:image]"
        )?.attr("content")

        if (poster.isNullOrBlank()) {
            poster = doc.selectFirst(
                ".entry-content img"
            )?.attr("src")
        }

        val plot = doc.selectFirst(
            ".entry-content p"
        )?.text()

        val tags = doc.select(
            ".meta-single-cats a"
        ).map {
            it.text()
        }

        val year = Regex("(19|20)\\d{2}")
            .find(title)
            ?.value
            ?.toIntOrNull()

        val type = if (
            title.contains("Season", true) ||
            title.contains("Episode", true)
        ) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieLoadResponse(
            title,
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

        val selectors = listOf(

            "a.shortc-button",
            "a.su-button",
            ".su-button-content a",
            ".entry-content a[href]",
            "div.entry-content a[href]"
        )

        val allowedHosts = listOf(
            "pixeldrain",
            "krakenfiles",
            "buzzheavier",
            "mediafire",
            "gofile",
            "streamwish",
            "filelions",
            "streamtape",
            "dood",
            "vidoza",
            "mixdrop",
            "mp4upload",
            "mega.nz",
            "1fichier",
            "drive.google"
        )

        val links = mutableSetOf<String>()

        selectors.forEach { selector ->

            doc.select(selector).forEach { el ->

                val href = el.attr("href").trim()

                if (
                    href.startsWith("http") &&
                    allowedHosts.any {
                        href.contains(it, true)
                    }
                ) {

                    links.add(href)
                }
            }
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

        return links.isNotEmpty()
    }
}