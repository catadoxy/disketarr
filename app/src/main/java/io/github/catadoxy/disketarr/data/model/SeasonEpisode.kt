package io.github.catadoxy.disketarr.data.model

/**
 * One episode of a season, from
 * `GET /api/v1/media/{media_type}/{source}/{media_id}/{season}/episodes/`.
 *
 * That endpoint returns tracked-media rows, so an episode's own `status` doubles
 * as its watched state. Built by hand from the raw envelope rather than through
 * Moshi, because the row nests everything under `item`.
 */
data class SeasonEpisode(
    val seasonNumber: Int? = null,
    val episodeNumber: Int,
    val title: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
    val status: Int? = null,
    val watched: Boolean = false,
) {
    /** "1x01" */
    val code: String
        get() = "${seasonNumber ?: 1}x${episodeNumber.toString().padStart(2, '0')}"
}

/**
 * One season of a show, from `.../seasons/`. The tracked row carries the watched
 * count and what is left, which together give "2/8" and the progress bar.
 */
data class SeasonSummary(
    val seasonNumber: Int,
    val status: Int? = null,
    /** Episodes watched in this season. */
    val progress: Int? = null,
    /** Aired but unwatched. */
    val episodesLeft: Int? = null,
    /**
     * Unwatched including episodes that have not aired yet. Disketarr often leaves
     * this null, so it cannot be relied on for the season's size.
     */
    val totalEpisodesLeft: Int? = null,
    /** Next episode to watch in this season, when Floppy reports one. */
    val nextEpisode: NextEpisode? = null,
    /**
     * The Floppy library this season is tracked in. Floppy reports the library
     * on a tracked season and leaves it empty on an untracked one, which is how
     * the app tells "not watched yet" from "watched in another library".
     */
    val libraryMediaType: String? = null,
) {
    /** Watched + aired-but-unwatched: the episodes that actually exist to watch. */
    val airedEpisodes: Int?
        get() {
            if (progress == null && episodesLeft == null) return null
            return (progress ?: 0) + (episodesLeft ?: 0)
        }

    /** Aired + announced, when the server reports it. Otherwise just the aired ones. */
    val totalEpisodes: Int?
        get() = totalEpisodesLeft?.let { (progress ?: 0) + it } ?: airedEpisodes

    /** Not yet aired: everything left minus what is already available. */
    val unreleased: Int?
        get() {
            val total = totalEpisodesLeft ?: return null
            return (total - (episodesLeft ?: 0)).takeIf { it > 0 }
        }
}
