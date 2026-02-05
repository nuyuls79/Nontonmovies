package com.midasxxi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class Midasxxi : MainAPI() {

    override var mainUrl = "https://ssstik.tv"
    private var directUrl = mainUrl

    override var name = "Midasxxi 🍡"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest Update",
        "$mainUrl/tvshows/page/" to "TV Series",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/comedy/page/" to "Comedy",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/fantasy/page/" to "Fantasy",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/mystery/page/" to "Mystery",
        "$mainUrl/country/china/page/" to "China",
        "$mainUrl/country/japan/page/" to "Japan",
        "$mainUrl/country/philippines/page/" to "Philippines",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    private fun getBaseUrl(url: String): String =
        URI(url).let { "${it.scheme}://${it.host}" }

    private fun String.fixUrl(): String {
        if (startsWith("http")) return this
        if (startsWith("//")) return "https:$this"
        return mainUrl + this
    }

    private fun extractPoster(el: Element): String? =
        el.selectFirst("img")?.attr("data-src")?.ifBlank { el.selectFirst("img")?.attr("src") }?.fixUrl()

    private fun extractQuality(el: Element): SearchQuality? {
        val q = el.selectFirst("span.quality")?.text()?.uppercase() ?: return null
        return when {
            q.contains("HD") -> SearchQuality.HD
            else -> SearchQuality.SD
        }
    }

    private fun extractRating(el: Element): Double? {
        val r = el.selectFirst("span.rating")?.text()?.trim()
        return r?.toDoubleOrNull()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = if (page == 1) app.get(request.data) else app.get("${request.data}$page/")
        mainUrl = getBaseUrl(res.url)
        val items = res.document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.data h3 a")?.text() ?: selectFirst("img")?.attr("alt") ?: return null
        val href = selectFirst("a")?.attr("href")?.fixUrl() ?: return null
        val poster = extractPoster(this)
        val quality = extractQuality(this)
        val rating = extractRating(this)

        val type = if (href.contains("/tvshows/")) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newAnimeSearchResponse(title.trim(), href, TvType.TvSeries) {
                posterUrl = poster
                this.score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title.trim(), href, TvType.Movie) {
                posterUrl = poster
                this.quality = quality
                this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val res = app.get("$mainUrl/search/$query/page/$page")
        mainUrl = getBaseUrl(res.url)
        val items = res.document.select("article.item").mapNotNull { it.toSearchResult() }
        return newSearchResponseList(items)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        directUrl = getBaseUrl(res.url)
        val doc = res.document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = extractPoster(doc)
        val plot = doc.selectFirst("div.wp-content p")?.text()
        val tags = doc.select("div.sgeneros a").map { it.text() }
        val isSeries = doc.select("ul.episodios").isNotEmpty()

        return if (isSeries) {
            val episodes = doc.select("ul.episodios li").mapNotNull {
                val link = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst(".episodiotitle")?.text()
                newEpisode(link) { this.name = name }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("ul#playeroptionsul li").amap {
            val json = app.post(
                "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to it.attr("data-post"),
                    "nume" to it.attr("data-nume"),
                    "type" to it.attr("data-type")
                ),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseHash>() ?: return@amap

            val aes = AppUtils.parseJson<AesData>(json.embed_url)
            val key = generateKey(json.key, aes.m)
            val decrypted = AesHelper.cryptoAESHandler(json.embed_url, key.toByteArray(), false)
                ?.replace("\"", "")?.replace("\\", "") ?: return@amap

            loadExtractor(decrypted, directUrl, subtitleCallback, callback)
        }
        return true
    }

    private fun generateKey(r: String, m: String): String {
        val rList = r.split("\\x")
        val decoded = base64Decode(m.reversed())
        var n = ""
        for (s in decoded.split("|")) n += "\\x" + rList[s.toInt() + 1]
        return n
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String
    )

    data class AesData(
        @JsonProperty("m") val m: String
    )
}
