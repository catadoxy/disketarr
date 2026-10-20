package io.github.catadoxy.disketarr.data.model

/**
 * The Floppy library a tracked entry lives in.
 *
 * Floppy keys a tracked entry by the *library* it was filed in, not only by the
 * item: the same TMDB show can sit in the TV library and in the Anime library
 * at the same time, each with its own status, progress and score. The library
 * is therefore part of every read and write - without it Floppy answers with
 * the TV library's copy, and a write lands in the wrong place.
 *
 * The libraries are `tv` (TV Shows), `anime` (Anime), `movie` (Movies) and
 * `season` (TV Seasons, where per-season tracking lives).
 *
 * Note that the library is *not* the item's media type: an anime tracked from
 * TMDB is a `tv` item that lives in the `anime` library.
 */
object LibraryType {

    const val TV = "tv"
    const val ANIME = "anime"
    const val MOVIE = "movie"
    const val SEASON = "season"
    const val EPISODE = "episode"

    private val ALL = setOf(TV, ANIME, MOVIE, SEASON, EPISODE)

    /**
     * The library a title belongs in when the user browses it by media type.
     * Anime is its own library even though a TMDB anime is a "tv" item.
     */
    fun forMediaType(mediaType: String): String = when (mediaType.lowercase()) {
        ANIME -> ANIME
        MOVIE -> MOVIE
        else -> TV
    }

    /** Floppy only understands these as a library selector. */
    fun isSupported(library: String?): Boolean = library?.lowercase() in ALL

    /**
     * The value to send as `library_media_type`.
     *
     * The detail routes answer with the entry of that library, and the answer
     * says whether the title is tracked there at all - so the library is named
     * explicitly. An unknown library is dropped rather than sent, which leaves
     * Floppy with its default.
     */
    fun apiParam(library: String?): String? =
        library?.lowercase()?.takeIf { it in ALL }

    /**
     * The library a show's seasons live in.
     *
     * Floppy files a tracked title in the library it was added to, and its
     * seasons in the matching place: a TV show's seasons are in the TV Seasons
     * library, while an anime keeps its seasons in the Anime library.
     */
    fun seasonLibrary(library: String?): String? = when (library?.lowercase()) {
        ANIME -> ANIME
        TV -> SEASON
        else -> null
    }

    /**
     * The library a show's episodes live in.
     *
     * The episodes of a TV show are individual entries, so they are in the
     * episode library, while an anime's stay in the Anime library with the rest
     * of the show.
     */
    fun episodeLibrary(library: String?): String? = when (library?.lowercase()) {
        ANIME -> ANIME
        TV -> EPISODE
        else -> null
    }
}
