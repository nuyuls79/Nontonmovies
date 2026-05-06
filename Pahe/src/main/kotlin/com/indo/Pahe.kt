package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class Pahe : MainAPI() {

    override var mainUrl = "https://pahe.ink"
    override var name = "Pahe"
    override val hasMainPage = true
    override var lang = "en"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
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

        return HomePageResponse(
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

        val doc = app.get(url).document

        val items = mutableListOf<SearchResponse>()

        doc.select("article").forEach { article ->

            val title =
                article.selectFirst("h2 a, h3 a")
                    ?.text()
                    ?.trim()
                    ?: return@forEach

            val link =
                article.selectFirst("h2 a, h3 a")
                    ?.attr("href")
                    ?: return@forEach

            if (!link.startsWith(mainUrl))
                return@forEach

            // =========================
            // DETAIL PAGE
            // =========================

            val detailDoc = app.get(link).document

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

            println("TITLE => $title")
            println("POSTER => $poster")

            items.add(
                newMovieSearchResponse(
                    title,
                    link,
                    TvType.Movie
                ) {
                    this.posterUrl = poster
                }
            )
        }

        return items.distinctBy { it.url }
    }

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val url =
            "$mainUrl/?s=${query.replace(" ", "+")}"

        val doc = app.get(url).document

        val items = mutableListOf<SearchResponse>()

        doc.select("article").forEach { article ->

            val title =
                article.selectFirst("h2 a, h3 a")
                    ?.text()
                    ?.trim()
                    ?: return@forEach

            val link =
                article.selectFirst("h2 a, h3 a")
                    ?.attr("href")
                    ?: return@forEach

            val detailDoc = app.get(link).document

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

            items.add(
                newMovieSearchResponse(
                    title,
                    link,
                    TvType.Movie
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

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title =
            doc.selectFirst("h1")
                ?.text()
                ?.trim()
                ?: "Unknown"

        val plot =
            doc.selectFirst("meta[name=description]")
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

        doc.select("a").forEach { a ->

            val href = a.attr("href")

            if (
                href.contains("drive") ||
                href.contains("gdflix") ||
                href.contains("pixeldrain") ||
                href.contains("hubcloud") ||
                href.contains("pahe")
            ) {
                links.add(href)
            }
        }

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
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
                    ExtractorLink(
                        source = name,
                        name = "Pahe",
                        url = link,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        type = INFER_TYPE
                    )
                )
            }
        }

        return true
    }
}