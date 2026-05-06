package com.indo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Pahe : MainAPI() {

    override var mainUrl = "https://pahe.in"

    override var name = "Pahe"

    override val hasMainPage = true

    override var lang = "id"

    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(

        // TERBARU
        "$mainUrl/page/" to "🔥 Terbaru",

        // ANIME
        "$mainUrl/category/movie-anime/page/" to "🎌 Anime Movie",
        "$mainUrl/category/tv-anime/page/" to "📺 Anime TV",

        // DRAMA NEGARA
        "$mainUrl/k-drama/page/" to "🇰🇷 Korean Drama",
        "$mainUrl/category/japanese-drama/page/" to "🇯🇵 Japanese Drama",
        "$mainUrl/category/chinese-drama/page/" to "🇨🇳 Chinese Drama",
        "$mainUrl/category/indian-drama/page/" to "🇮🇳 Indian Drama",
        "$mainUrl/category/thai-drama/page/" to "🇹🇭 Thai Drama",
        "$mainUrl/category/turkish-drama/page/" to "🇹🇷 Turkish Drama",

        // MOVIES GENRE
        "$mainUrl/category/action/page/" to "🎬 Action",
        "$mainUrl/category/adventure/page/" to "🗺 Adventure",
        "$mainUrl/category/animation/page/" to "🧸 Animation",
        "$mainUrl/category/biography/page/" to "📖 Biography",
        "$mainUrl/category/comedy/page/" to "😂 Comedy",
        "$mainUrl/category/crime/page/" to "🕵 Crime",
        "$mainUrl/category/documentary/page/" to "🎥 Documentary",
        "$mainUrl/category/drama/page/" to "🎭 Drama",
        "$mainUrl/category/family/page/" to "👨‍👩‍👧 Family",
        "$mainUrl/category/fantasy/page/" to "🧙 Fantasy",
        "$mainUrl/category/film-noir/page/" to "🌑 Film-Noir",
        "$mainUrl/category/history/page/" to "🏺 History",
        "$mainUrl/category/horror/page/" to "👻 Horror",
        "$mainUrl/category/music/page/" to "🎵 Music",
        "$mainUrl/category/musical/page/" to "🎤 Musical",
        "$mainUrl/category/mystery/page/" to "❓ Mystery",
        "$mainUrl/category/reality-tv/page/" to "📡 Reality-TV",
        "$mainUrl/category/romance/page/" to "❤️ Romance",
        "$mainUrl/category/sci-fi/page/" to "🚀 Sci-Fi",
        "$mainUrl/category/sport/page/" to "⚽ Sport",
        "$mainUrl/category/thriller/page/" to "🔪 Thriller",
        "$mainUrl/category/war/page/" to "⚔ War",
        "$mainUrl/category/western/page/" to "🤠 Western",

        // TV SHOW
        "$mainUrl/category/tv-action/page/" to "📺 TV Action",
        "$mainUrl/category/tv-adventure/page/" to "📺 TV Adventure",
        "$mainUrl/category/tv-animation/page/" to "📺 TV Animation",
        "$mainUrl/category/tv-comedy/page/" to "📺 TV Comedy",
        "$mainUrl/category/tv-crime/page/" to "📺 TV Crime",
        "$mainUrl/category/tv-drama/page/" to "📺 TV Drama",
        "$mainUrl/category/tv-fantasy/page/" to "📺 TV Fantasy",
        "$mainUrl/category/tv-horror/page/" to "📺 TV Horror",
        "$mainUrl/category/tv-mystery/page/" to "📺 TV Mystery",
        "$mainUrl/category/tv-romance/page/" to "📺 TV Romance",
        "$mainUrl/category/tv-sci-fi/page/" to "📺 TV Sci-Fi",
        "$mainUrl/category/tv-thriller/page/" to "📺 TV Thriller",
        "$mainUrl/category/ongoing/page/" to "📡 Ongoing TV",

        // SPECIAL
        "$mainUrl/tag/imdb-top-250/page/" to "⭐ IMDb Top 250",
        "$mainUrl/tag/oscar-win/page/" to "🏆 Oscar Win",
        "$mainUrl/tag/ghibli/page/" to "🍃 Studio Ghibli",
        "$mainUrl/tag/non-english/page/" to "🌍 Non-English"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = request.data + page

        val doc = app.get(
            url,
            headers = ua
        ).document

        val home = doc.select(
            "article, div.post-item, div.blog-item, div.grid-item"
        ).mapNotNull { article ->

            val a = article.selectFirst(
                "h1 a, h2 a, h3 a, a[href*=pahe]"
            ) ?: return@mapNotNull null

            val href = fixUrl(a.attr("href"))

            val title = a.text()
                .trim()
                .ifBlank { return@mapNotNull null }

            val poster = article.selectFirst("img")?.let {
                it.attr("data-src").ifBlank {
                    it.attr("data-lazy-src").ifBlank {
                        it.attr("src")
                    }
                }
            }?.ifBlank { null }

            val type = if (
                title.contains("Season", true) ||
                title.contains("Episode", true) ||
                title.contains("S01", true) ||
                title.contains("S02", true) ||
                title.contains("Series", true)
            ) {
                TvType.TvSeries
            } else {
                TvType.Movie
            }

            newMovieSearchResponse(
                title,
                href,
                type
            ) {
                this.posterUrl = poster
            }

        }.distinctBy { it.url }

        if (home.isEmpty()) {
            throw ErrorLoadingException("No content found")
        }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val doc = app.get(
            "$mainUrl/?s=${query.encodeUri()}",
            headers = ua
        ).document

        return doc.select(
            "article, div.post-item, div.blog-item, div.grid-item"
        ).mapNotNull { article ->

            val a = article.selectFirst(
                "h1 a, h2 a, h3 a"
            ) ?: return@mapNotNull null

            val href = fixUrl(a.attr("href"))

            val title = a.text()
                .trim()
                .ifBlank { return@mapNotNull null }

            val poster = article.selectFirst("img")?.let {
                it.attr("data-src").ifBlank {
                    it.attr("data-lazy-src").ifBlank {
                        it.attr("src")
                    }
                }
            }?.ifBlank { null }

            val type = if (
                title.contains("Season", true) ||
                title.contains("Episode", true) ||
                title.contains("S01", true) ||
                title.contains("S02", true) ||
                title.contains("Series", true)
            ) {
                TvType.TvSeries
            } else {
                TvType.Movie
            }

            newMovieSearchResponse(
                title,
                href,
                type
            ) {
                this.posterUrl = poster
            }

        }.distinctBy { it.url }
    }

    override suspend fun load(
        url: String
    ): LoadResponse {

        val doc = app.get(
            url,
            headers = ua
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

        val poster = doc.selectFirst(
            "img[src*=upload], div.entry-content img"
        )?.attr("src")

        val description = doc.selectFirst(
            "div.entry-content > p"
        )?.text()?.trim()

        val tags = doc.select(
            "a[rel=category tag], a[href*=category], a[href*=tag]"
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
            rawTitle.contains("Episode", true) ||
            rawTitle.contains("Series", true)
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
            posterUrl = poster
            plot = description
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
            headers = ua
        ).document

        val links = doc.select("a[href]")
            .mapNotNull {
                it.attr("href").ifBlank { null }
            }
            .filter { href ->

                href.contains("drive.google") ||
                href.contains("mega.nz") ||
                href.contains("pixeldrain") ||
                href.contains("gofile") ||
                href.contains("mediafire") ||
                href.contains("1fichier") ||
                href.contains("racaty") ||
                href.contains("pahe") ||
                href.startsWith("http")
            }
            .distinct()

        links.forEach { link ->

            val fixed = fixUrl(link)

            try {

                loadExtractor(
                    fixed,
                    data,
                    subtitleCallback,
                    callback
                )

            } catch (_: Exception) {

                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        fixed,
                        "",
                        Qualities.Unknown.value,
                        false
                    )
                )
            }
        }

        doc.select("iframe")
            .mapNotNull {
                it.attr("src").ifBlank { null }
            }
            .forEach { src ->

                val fixed = fixUrl(src)

                try {

                    loadExtractor(
                        fixed,
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