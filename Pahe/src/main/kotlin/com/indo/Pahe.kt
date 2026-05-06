package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Pahe : MainAPI() {

    override var mainUrl = "https://pahe.ink"
    override var name = "Pahe"
    override val hasMainPage = true
    override var lang = "en"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val headers = mapOf(
        "User-Agent" to
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    // =========================
    // MAIN PAGE
    // =========================

    override val mainPage = mainPageOf(
        "$mainUrl/category/action/" to "🎬 Action",
        "$mainUrl/category/adventure/" to "🗺 Adventure",
        "$mainUrl/category/animation/" to "🧸 Animation",
        "$mainUrl/category/comedy/" to "😂 Comedy",
        "$mainUrl/category/crime/" to "🕵 Crime",
        "$mainUrl/category/drama/" to "🎭 Drama",
        "$mainUrl/category/fantasy/" to "🧙 Fantasy",
        "$mainUrl/category/horror/" to "👻 Horror",
        "$mainUrl/category/mystery/" to "🔎 Mystery",
        "$mainUrl/category/romance/" to "❤️ Romance",
        "$mainUrl/category/sci-fi/" to "🚀 Sci-Fi",
        "$mainUrl/category/thriller/" to "🔪 Thriller",
        "$mainUrl/category/tv-drama/" to "📺 TV Drama",
        "$mainUrl/category/tv-action/" to "📺 TV Action",
        "$mainUrl/category/tv-comedy/" to "📺 TV Comedy",
    )

    // =========================
    // MAIN PAGE LOADER
    // =========================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val genre = request.data
            .substringAfter("/category/")
            .substringBefore("/")

        val items = getGenrePosts(genre, page)

        return newHomePageResponse(
            listOf(
                HomePageList(
                    request.name,
                    items,
                    isHorizontalImages = true
                )
            ),
            hasNext = true
        )
    }

    // =========================
    // GET GENRE POSTS
    // =========================

private suspend fun getGenrePosts(
    genre: String,
    page: Int = 1
): List<SearchResponse> {

    val url =
        if (page == 1)
            "$mainUrl/category/$genre/"
        else
            "$mainUrl/category/$genre/page/$page/"

    val doc = app.get(
        url,
        headers = headers
    ).document

    val items = mutableListOf<SearchResponse>()

    // selector baru lebih stabil
    val posts = doc.select(
        "article.post, div.post, div.tie-col-md-11, li.post"
    )

    println("GENRE => $genre")
    println("TOTAL POSTS => ${posts.size}")

    posts.forEach { article ->

        val a = article.selectFirst("h2 a, h3 a, a[rel=bookmark]")
            ?: return@forEach

        val title = a.text().trim()

        val link = a.attr("href").trim()

        if (
            title.isBlank() ||
            link.isBlank() ||
            !link.startsWith(mainUrl)
        ) return@forEach

        // =========================
        // POSTER
        // =========================

        var poster: String? = null

        // ambil poster langsung dari halaman category dulu
        article.select("img").forEach { img ->

            val src = img.attr("data-src")
                .ifBlank { img.attr("src") }

            if (
                src.contains("/wp-content/uploads/") &&
                !src.contains("gravatar") &&
                !src.contains("amazon") &&
                !src.contains("transparent") &&
                !src.contains("logo")
            ) {

                poster = src
                    .replace("-110x153", "")
                    .replace("-150x150", "")
                    .replace("-75x75", "")
                    .replace("-75x106", "")
                    .replace("-220x150", "")

                return@forEach
            }
        }

        // fallback ambil dari detail page
        if (poster == null) {

            try {

                val detailDoc = app.get(
                    link,
                    headers = headers
                ).document

                detailDoc.select("img").forEach { img ->

                    val src = img.attr("data-src")
                        .ifBlank { img.attr("src") }

                    if (
                        src.contains("/wp-content/uploads/") &&
                        !src.contains("gravatar") &&
                        !src.contains("amazon") &&
                        !src.contains("transparent") &&
                        !src.contains("logo")
                    ) {

                        poster = src
                            .replace("-110x153", "")
                            .replace("-150x150", "")
                            .replace("-75x75", "")
                            .replace("-75x106", "")
                            .replace("-220x150", "")

                        return@forEach
                    }
                }

            } catch (_: Exception) {
            }
        }

        println("TITLE => $title")
        println("POSTER => $poster")

        val type = if (
            title.contains("Season", true) ||
            title.contains("Episode", true) ||
            title.contains("S01", true)
        ) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        items.add(
            newMovieSearchResponse(
                title,
                link,
                type
            ) {
                this.posterUrl = poster
            }
        )
    }

    return items
        .distinctBy { it.url }
        .take(30)
}

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val url =
            "$mainUrl/?s=${query.replace(" ", "+")}"

        val doc = app.get(
            url,
            headers = headers
        ).document

        val items = mutableListOf<SearchResponse>()

        doc.select("article").forEach { article ->

            val title = article
                .selectFirst("h2 a, h3 a")
                ?.text()
                ?.trim()
                ?: return@forEach

            val link = article
                .selectFirst("h2 a, h3 a")
                ?.attr("href")
                ?: return@forEach

            val detailDoc = app.get(
                link,
                headers = headers
            ).document

            var poster: String? = null

            detailDoc.select("img").forEach { img ->

                val src = img.attr("src")

                if (
                    src.contains("/wp-content/uploads/") &&
                    !src.contains("gravatar") &&
                    !src.contains("amazon") &&
                    !src.contains("transparent") &&
                    !src.contains("logo") &&
                    !src.contains("icon")
                ) {

                    poster = src
                        .replace("-110x153", "")
                        .replace("-150x150", "")
                        .replace("-75x75", "")

                    return@forEach
                }
            }

            val type = if (
                title.contains("Season", true) ||
                title.contains("Episode", true) ||
                title.contains("S01", true)
            ) {
                TvType.TvSeries
            } else {
                TvType.Movie
            }

            items.add(
                newMovieSearchResponse(
                    title,
                    link,
                    type
                ) {
                    this.posterUrl = poster
                }
            )
        }

        return items.distinctBy { it.url }
    }

    // =========================
    // LOAD
    // =========================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val doc = app.get(
            url,
            headers = headers
        ).document

        val title = doc
            .selectFirst("h1")
            ?.text()
            ?.trim()
            ?: "Unknown"

        val plot = doc
            .selectFirst("meta[name=description]")
            ?.attr("content")

        var poster: String? = null

        doc.select("img").forEach { img ->

            val src = img.attr("src")

            if (
                src.contains("/wp-content/uploads/") &&
                !src.contains("gravatar") &&
                !src.contains("amazon") &&
                !src.contains("transparent") &&
                !src.contains("logo") &&
                !src.contains("icon")
            ) {

                poster = src
                    .replace("-110x153", "")
                    .replace("-150x150", "")
                    .replace("-75x75", "")

                return@forEach
            }
        }

        val links = mutableListOf<String>()

        doc.select("a[href]").forEach { a ->

            val href = a.attr("href")

            if (
                href.contains("drive", true) ||
                href.contains("gdflix", true) ||
                href.contains("pixeldrain", true) ||
                href.contains("hubcloud", true) ||
                href.contains("krakenfiles", true) ||
                href.contains("mediafire", true)
            ) {
                links.add(href)
            }
        }

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
            links.joinToString("\n")
        ) {
            posterUrl = poster
            this.plot = plot
        }
    }

    // =========================
    // LOAD LINKS
    // =========================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        data.lines().forEach { link ->

            if (link.isNotBlank()) {

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Pahe",
                        url = link,
                        type = INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}