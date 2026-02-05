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

    companion object {
        var context: android.content.Context? = null
    }

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

    // ================= LINK NORMALIZER (FIX ERROR) =================

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episode/") -> {
                var title = uri.substringAfter("/episode/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1)
                    ?: title.substringBefore("/")
                "$mainUrl/tvseries/$title"
            }

            uri.contains("/season/") -> {
                var title = uri.substringAfter("/season/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1)
                    ?: title.substringBefore("/")
                "$mainUrl/tvseries/$title"
            }

            else -> uri
        }
    }

    // ================= POSTER HELPER =================

    private fun extractPoster(el: Element): String? {
        el.selectFirst("img")?.let {
            val src = it.attr("data-src")
                .ifBlank { it.attr("data-lazy-src") }
                .ifBlank { it.attr("src") }
            if (src.isNotBlank()) return fixUrl(src)
        }

        val style = el.attr("style")
        Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)?.let {
            return fixUrl(it)
        }

        return null
    }

    // ================= HOMEPAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        context?.let { StarPopupHelper.showStarPopupIfNeeded(it) }

        val req = if (page == 1) {
            app.get(request.data)
        } else {
            app.get("${request.data}$page/")
        }

        mainUrl = getBaseUrl(req.url)

        val home = req.document
            .select("div#archive-content article.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("img")?.attr("alt")?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = extractPoster(this)
        val quality = getQualityFromString(selectFirst("span.quality")?.text())

        return newMovieSearchResponse(
            title,
            getProperLink(href),
            if (href.contains("/tv")) TvType.TvSeries else TvType.Movie
        ) {
            posterUrl = poster
            this.quality = quality
        }
    }

    // ================= SEARCH =================

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val req = app.get("$mainUrl/search/$query/page/$page")
        mainUrl = getBaseUrl(req.url)

        val results = req.document.select("div.result-item").mapNotNull {
            val titleEl = it.selectFirst("div.title > a") ?: return@mapNotNull null
            val title = titleEl.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(titleEl.attr("href"))
            val poster = extractPoster(it)

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

        val title = doc.selectFirst("div.data > h1")
            ?.text()
            ?.replace(Regex("\\(\\d{4}\\)"), "")
            ?.trim()
            ?: "Unknown"

        val poster = extractPoster(doc)
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year = Regex("\\b(19\\d{2}|20\\d{2})\\b")
            .find(doc.text())?.value?.toIntOrNull()

        val tvType =
            if (doc.select("ul#section").text().contains("Episodes"))
                TvType.TvSeries else TvType.Movie

        val plot = doc.selectFirst("div.wp-content > p, div.content p")?.text()?.trim()
        val trailer = doc.selectFirst("iframe")?.attr("src")

        val actors = doc.select("div.persons div[itemprop=actor]").map {
            Actor(
                it.select("meta[itemprop=name]").attr("content"),
                it.select("img").attr("src")
            )
        }

        return if (tvType == TvType.TvSeries) {

            val episodes = doc.select("ul.episodios > li").map {
                val href = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst("div.episodiotitle")?.text()
                val image = extractPoster(it)

                newEpisode(href) {
                    this.name = name
                    this.posterUrl = image
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                addActors(actors)
                addTrailer(trailer)
            }

        } else {

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                addActors(actors)
                addTrailer(trailer)
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

        val document = app.get(data).document

        document.select("ul#playeroptionsul > li")
            .map {
                Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
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
                )?.fixBloat() ?: return@amap

                if (!decrypted.contains("youtube")) {
                    loadExtractor(decrypted, directUrl, subtitleCallback, callback)
                }
            }

        return true
    }

    private fun generateKey(r: String, m: String): String {
        val rList = r.split("\\x")
        var n = ""
        val decoded = safeBase64Decode(m.reversed())
        for (s in decoded.split("|")) {
            n += "\\x" + rList[s.toInt() + 1]
        }
        return n
    }

    private fun safeBase64Decode(input: String): String {
        var padded = input
        val rem = input.length % 4
        if (rem != 0) padded += "=".repeat(4 - rem)
        return base64Decode(padded)
    }

    private fun String.fixBloat(): String =
        replace("\"", "").replace("\\", "")

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String
    )

    data class AesData(
        @JsonProperty("m") val m: String
    )
}
