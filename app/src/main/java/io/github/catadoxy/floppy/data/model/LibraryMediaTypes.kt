package io.github.catadoxy.floppy.data.model

/**
 * Canonical list of media types Floppy can return, plus human-readable labels
 * used when parsing payloads (e.g. discover rows).
 */
object LibraryMediaTypes {

    val ALL: List<String> = listOf(
        "movie",
        "tv",
        "anime",
        "manga",
        "game",
        "book",
        "comic",
        "boardgame",
        "music",
        "podcast",
        "season",
    )

    private val KNOWN: Set<String> = ALL.toSet()

    /** Ordered for the settings list, with the most common four first. */
    val options: List<Pair<String, String>> = listOf(
        "movie" to "Movies",
        "tv" to "TV",
        "anime" to "Anime",
        "manga" to "Manga",
        "game" to "Games",
        "book" to "Books",
        "comic" to "Comics",
        "boardgame" to "Board Games",
        "music" to "Music",
        "podcast" to "Podcasts",
        "season" to "Seasons",
    )

    val default: Set<String> = KNOWN

    /** Drops stale or unknown entries and preserves [ALL]'s ordering. */
    fun sanitize(selection: Collection<String>?): Set<String> {
        val chosen = selection.orEmpty().filter { it in KNOWN }.toSet()
        return chosen.ifEmpty { default }
    }

    fun ordered(selection: Collection<String>?): List<String> = ALL.filter { it in sanitize(selection) }
}
