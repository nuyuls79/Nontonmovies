package com.midasxxi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.toNewSearchResponseList
import org.jsoup.nodes.Element
import java.net.URI

class Midasxxi : MainAPI() {

    override var mainUrl = "https://ssstik.tv"
    private var directUrl = mainUrl

    override var name = "Midasxxi🍡"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // ================= MAIN PAGE =================

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest Update",
        "$mainUrl/tvshows/page/" to "TV Series",

        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/anime/page/" to "Anime",
        "$mainUrl/genre/comedy/page/" to "Comedy",
        "$mainUrl/genre/crime/page/" to "Crime",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/fantasy/page/" to "Fantasy",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/mystery/page/" to "Mystery",

        "$mainUrl/country/china/page/" to "China",
        "$mainUrl/country/japan/page/" to "Japan",
        "$mainUrl/country/philippines/page/" to "Philippines",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    // ================= HELPERS =================

    private fun getBaseUrl(url: String): String =
        URI(url).let { "${it.scheme}://${it.host}" }

    private fun String.fixUrlSelf(): String {
        if (startsWith("http")) return this
        if (startsWith("//")) return "https:$this"
        return mainUrl + this
    }

    private fun getProperLink(url: String): String {
        return when {
            url.contains("/episode/") || url.contains("/season/") -> {
                val slug = url.substringAfterLast("/").substringBefore("-season")
                "$mainUrl/tvseries/$slug"
            }
            else -> url
        }
    }

    // ================= POSTER =================

    private fun extractPoster(el: Element): String? {
        el.selectFirst("img")?.let {
            val src = it.attr("data-src")
                .ifBlank { it.attr("data-lazy-src") }
                .ifBlank { it.attr("src") }
            if (src.isNotBlank()) return src.fixUrlSelf()
        }

        el.selectFirst("div.poster, div.image")?.attr("style")?.let { style ->
            Regex("url\\(['\"]?(.*?)['\"]?\\)")
                .find(style)
                ?.groupValues
                ?.get(1)
                ?.let { return it.fixUrlSelf() }
        }

        return null
    }

    // ================= HOMEPAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val req = if (page == 1)
            app.get(request.data)
        else
            app.get("${request.data}$page/")

        mainUrl = getBaseUrl(req.url)

        val items = req.document
            .select("article.item, div.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val title =
            selectFirst("h3, h2, .title")?.text()?.trim()
                ?: selectFirst("img")?.attr("alt")?.trim()
                ?: return null

        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = extractPoster(this)

        val type =
            if (href.contains("/tv", true)) TvType.TvSeries
            else TvType.Movie

        return newMovieSearchResponse(
            title,
            getProperLink(href),
            type
        ) {
            posterUrl = poster
        }
    }

    // ================= SEARCH =================

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val req = app.get("$mainUrl/search/$query/page/$page")
        mainUrl = getBaseUrl(req.url)

        val results = req.document.select("div.result-item").mapNotNull {
            val a = it.selectFirst("div.title a") ?: return@mapNotNull null
            val title = a.text().trim()
            val href = getProperLink(a.attr("href"))
            val poster = extractPoster(it)

            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
        }

        return results.toNewSearchResponseList()
    }

    // ================= LOAD =================

    override suspend fun load(url: String): LoadResponse {
        val req = app.get(url)
        directUrl = getBaseUrl(req.url)
        val doc = req.document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = extractPoster(doc)
        val plot = doc.selectFirst("div.wp-content p")?.text()
        val tags = doc.select("div.sgeneros a").map { it.text() }

        val tvType =
            if (doc.select("ul.episodios").isNotEmpty())
                TvType.TvSeries
            else
                TvType.Movie

        return if (tvType == TvType.TvSeries) {

            val episodes = doc.select("ul.episodios li").map {
                val link = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst(".episodiotitle")?.text()
                val img = extractPoster(it)

                newEpisode(link) {
                    this.name = name
                    this.posterUrl = img
                }
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

    // ================= LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        doc.select("ul#playeroptionsul li")
            .map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->

                val json = app.post(
                    "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsedSafe<ResponseHash>() ?: return@amap

                val aes = AppUtils.parseJson<AesData>(json.embed_url)
                val key = generateKey(json.key, aes.m)

                val decrypted = AesHelper.cryptoAESHandler(
                    json.embed_url,
                    key.toByteArray(),
                    false
                )?.replace("\"", "")?.replace("\\", "")
                    ?: return@amap

                loadExtractor(decrypted, directUrl, subtitleCallback, callback)
            }

        return true
    }

    private fun generateKey(r: String, m: String): String {
        val rList = r.split("\\x")
        val decoded = base64Decode(m.reversed())
        var n = ""
        for (s in decoded.split("|")) {
            n += "\\x" + rList[s.toInt() + 1]
        }
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
