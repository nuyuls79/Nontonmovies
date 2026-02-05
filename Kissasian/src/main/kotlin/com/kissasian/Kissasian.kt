package com.kissasian

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Kissasian : MainAPI() {

    override var mainUrl = "https://kissasian.cam"
    override var name = "Kissasian🥯"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        var context: android.content.Context? = null

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "series/?order=latest" to "Baru ditambahkan",
        "series/?status=&type=&order=update" to "Update Terbaru",
        "series/?status=&type=Movie&order=latest" to "Movie Terbaru",
        "series/?status=&type=&order=popular" to "Terpopuler",
        "series/?status=&type=Series&order=latest" to "TV Show Terbaru",
        "series/?status=&type=Special&order=latest" to "Special Movie",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        context?.let { StarPopupHelper.showStarPopupIfNeeded(it) }
        val url = "$mainUrl/${request.data}&page=$page"
        val document = app.get(url).document
        val items = document.select("div.listupd article.bs")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = items.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("a") ?: return null
        val href = fixUrl(link.attr("href"))
        val title = link.attr("title").ifBlank {
            selectFirst("div.tt")?.text()
        } ?: return null
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }

        val isSeries = href.contains("/series/", true) || href.contains("drama", true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", timeout = 50L).document
        return document.select("div.listupd article.bs")
            .mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("div.tt")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster = document.selectFirst("div.bigcontent img")
            ?.getImageAttr()?.let { fixUrlNull(it) }

        val description = document.select("div.entry-content p")
            .joinToString("\n") { it.text() }.trim()

        val year = document.selectFirst("span:matchesOwn(Dirilis:)")
            ?.ownText()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

        val duration = document.selectFirst("div.spe span:contains(Durasi:)")
            ?.ownText()?.let {
                val h = Regex("(\\d+)\\s*hr").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val m = Regex("(\\d+)\\s*min").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                h * 60 + m
            }

        val tags = document.select("div.genxed a").map { it.text() }

        val actors = document.select("span:has(b:matchesOwn(Artis:)) a")
            .map { it.text().trim() }

        val rating = document.selectFirst("div.rating strong")
            ?.text()?.replace("Rating", "")?.trim()?.toDoubleOrNull()

        val trailer = document.selectFirst("div.bixbox.trailer iframe")?.attr("src")

        val status = getStatus(
            document.selectFirst("div.info-content div.spe span")
                ?.ownText()?.replace(":", "")?.trim() ?: ""
        )

        val recommendations = document.select("div.listupd article.bs")
            .mapNotNull { it.toRecommendResult() }

        val episodes = document.select("div.eplister ul li a")
            .reversed()
            .mapIndexed { index, a ->
                newEpisode(fixUrl(a.attr("href"))) {
                    name = "Episode ${index + 1}"
                    episode = index + 1
                }
            }

        return if (episodes.size > 1) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                showStatus = status
                this.recommendations = recommendations
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                episodes.firstOrNull()?.data ?: url
            ) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                this.recommendations = recommendations
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.selectFirst("div.player-embed iframe")
            ?.attr("src")?.takeIf { it.isNotBlank() }?.let {
                loadExtractor(httpsify(it), data, subtitleCallback, callback)
            }

        document.select("select.mirror option[value]").forEach { opt ->
            val mirrorUrl = fixUrl(opt.attr("value"))
            if (mirrorUrl.isBlank()) return@forEach
            try {
                val mirrorDoc = app.get(mirrorUrl).document
                mirrorDoc.selectFirst("div.player-embed iframe")
                    ?.attr("src")?.takeIf { it.isNotBlank() }?.let {
                        loadExtractor(httpsify(it), mirrorUrl, subtitleCallback, callback)
                    }
            } catch (_: Throwable) {
            }
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }
}