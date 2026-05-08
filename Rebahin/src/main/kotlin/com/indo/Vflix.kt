package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class VFlix : MainAPI() {

    override var mainUrl = "https://vflix.42web.io"
    override var name = "VFlix"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val hasDownloadSupport = true

    // =========================
    // MAIN PAGE
    // =========================

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Movies",
        "$mainUrl/category/action/" to "Action",
        "$mainUrl/category/comedy/" to "Comedy",
        "$mainUrl/category/horror/" to "Horror",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/romance/" to "Romance",
        "$mainUrl/category/thriller/" to "Thriller",
    )

    // =========================
    // MAIN PAGE
    // =========================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            if (page == 1)
                request.data
            else
                "${request.data}page/$page/"

        val doc = app.get(url).document

        val items = doc.select("article, div.item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = true
        )
    }

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val doc = app.get(
            "$mainUrl/?s=${query.replace(" ", "+")}"
        ).document

        return doc.select("article, div.item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    // =========================
    // SEARCH RESULT PARSER
    // =========================

    private fun toSearchResult(
        element: Element
    ): SearchResponse? {

        val a =
            element.selectFirst("a[href]")
                ?: return null

        val href =
            a.attr("href")
                .ifBlank { return null }

        val title =
            element.selectFirst("h2, h3")
                ?.text()
                ?.trim()
                ?: a.attr("title")
                    .replace("Permalink to:", "")
                    .trim()

        if (title.isBlank())
            return null

        var poster =
            element.selectFirst("img")
                ?.attr("data-src")

        if (poster.isNullOrBlank()) {
            poster =
                element.selectFirst("img")
                    ?.attr("src")
        }

        val isSeries =
            href.contains("/tv/")

        return if (isSeries) {

            newTvSeriesSearchResponse(
                title,
                href,
                TvType.TvSeries
            ) {
                this.posterUrl = poster
            }

        } else {

            newMovieSearchResponse(
                title,
                href,
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
    }

    // =========================
    // LOAD
    // =========================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val doc = app.get(url).document

        val title =
            doc.selectFirst("h1")
                ?.text()
                ?.trim()
                ?: throw ErrorLoadingException("No title")

        var poster =
            doc.selectFirst("img")
                ?.attr("src")

        if (poster.isNullOrBlank()) {
            poster =
                doc.selectFirst("img")
                    ?.attr("data-src")
        }

        val plot =
            doc.selectFirst(
                "div.entry-content p, div.description p"
            )?.text()?.trim()

        val tags =
            doc.select("a[rel=category tag]")
                .map { it.text() }

        val year =
            Regex("""(19|20)\d{2}""")
                .find(doc.text())
                ?.value
                ?.toIntOrNull()

        val isSeries =
            url.contains("/tv/")

        return if (isSeries) {

            val episodes =
                doc.select("a[href*=/episode/]").mapIndexed { index, ep ->

                    newEpisode(
                        ep.attr("href")
                    ) {
                        this.name = ep.text()
                        this.episode = index + 1
                    }
                }

            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }

        } else {

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
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

        val doc = app.get(data).document

        // =========================
        // Cari API video
        // =========================

        val api =
            Regex("""https:\/\/moviexstream\.strp2p\.live\/api\/v1\/video\?id=[^"' ]+""")
                .find(doc.html())
                ?.value

        if (api == null) {
            return false
        }

        // =========================
        // Request API
        // =========================

        val apiResponse = app.get(
            api,
            referer = mainUrl,
            headers = mapOf(
                "Origin" to "https://moviexstream.strp2p.live",
                "Referer" to "https://moviexstream.strp2p.live/"
            )
        ).text

        // =========================
        // Cari m3u8
        // =========================

        val m3u8 =
            Regex("""https?:\/\/[^"' ]+\.m3u8[^"' ]*""")
                .find(apiResponse)
                ?.value

        if (m3u8 != null) {

            callback.invoke(
                ExtractorLink(
                    source = "VFlix",
                    name = "VFlix HLS",
                    url = m3u8,
                    referer = "https://moviexstream.strp2p.live/",
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8,
                    isM3u8 = true
                )
            )

            return true
        }

        return false
    }
}