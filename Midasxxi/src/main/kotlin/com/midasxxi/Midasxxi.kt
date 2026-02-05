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
    override val hasMainPage = true
    override var lang = "id"
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
        "$mainUrl/tvshows/page/" to "Tv Series",

        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/anime/page/" to "Anime",
        "$mainUrl/genre/comedy/page/" to "Comedy",
        "$mainUrl/genre/crime/page/" to "Crime",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/fantasy/page/" to "Fantasy",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/mystery/page/" to "Mistery",

        "$mainUrl/country/china/page/" to "China",
        "$mainUrl/country/japan/page/" to "Jepang",
        "$mainUrl/country/philippines/page/" to "Philipines",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    private fun getBaseUrl(url: String): String =
        URI(url).let { "${it.scheme}://${it.host}" }

    // ================= LINK FIX =================

    private fun getProperLink(url: String): String = url

    // ================= POSTER =================

    private fun Element.poster(): String? =
        selectFirst("div.poster img")?.attr("src")?.fixUrl()

    // ================= SEARCH RESULT =================

    private fun Element.toSearchResult(): SearchResponse? {

        val title = selectFirst("div.data h3 a")?.text()?.trim()
            ?: return null

        val href = selectFirst("div.poster a")?.attr("href")
            ?: return null

        val poster = poster()
        val quality = getQualityFromString(selectFirst("span.quality")?.text())

        val type =
            if (hasClass("tvshows")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(
            title,
            getProperLink(href),
            type
        ) {
            posterUrl = poster
            this.quality = quality
        }
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

        val home = req.document
            .select("article.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    // ================= SEARCH =================

    override suspend fun search(query: String, page: Int): SearchResponseList? {

        val req = app.get("$mainUrl/search/$query/page/$page")
        mainUrl = getBaseUrl(req.url)

        val results = req.document
            .select("div.result-item")
            .mapNotNull {
                val title = it.selectFirst("div.title a")?.text()?.trim()
                    ?: return@mapNotNull null
                val href = it.selectFirst("div.title a")?.attr("href")
                    ?: return@mapNotNull null
                val poster = it.selectFirst("img")?.attr("src")?.fixUrl()

                newMovieSearchResponse(title, href, TvType.Movie) {
                    posterUrl = poster
                }
            }

        return results.toNewSearchResponseList()
    }

    // ================= LOAD DETAIL =================

    override suspend fun load(url: String): LoadResponse {

        val req = app.get(url)
        directUrl = getBaseUrl(req.url)
        val doc = req.document

        val title = doc.selectFirst("div.data h1")?.text()?.trim() ?: "Unknown"
        val poster = doc.selectFirst("div.poster img")?.attr("src")?.fixUrl()
        val plot = doc.selectFirst("div.wp-content p")?.text()
        val trailer = doc.selectFirst("iframe")?.attr("src")

        val actors = doc.select("div.persons div[itemprop=actor]").map {
            Actor(
                it.select("meta[itemprop=name]").attr("content"),
                it.select("img").attr("src")
            )
        }

        val isTv =
            doc.select("ul.episodios").isNotEmpty()

        return if (isTv) {

            val episodes = doc.select("ul.episodios li").map {
                val href = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst("div.episodiotitle")?.text()

                newEpisode(href) {
                    this.name = name
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
                addActors(actors)
                addTrailer(trailer)
            }

        } else {

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.plot = plot
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    // ================= STREAM =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select("ul#playeroptionsul li").amap {
            val id = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")

            val json = app.post(
                "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                ),
                referer = data
            ).parsedSafe<ResponseHash>() ?: return@amap

            val aes = AppUtils.parseJson<AesData>(json.embed_url)
            val key = generateKey(json.key, aes.m)

            val decrypted = AesHelper.cryptoAESHandler(
                json.embed_url,
                key.toByteArray(),
                false
            )?.replace("\"", "") ?: return@amap

            loadExtractor(decrypted, directUrl, subtitleCallback, callback)
        }

        return true
    }

    private fun generateKey(r: String, m: String): String {
        val rList = r.split("\\x")
        var n = ""
        val decoded = base64Decode(m.reversed())
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
