package io.github.catadoxy.floppy.data.model

/**
 * Floppy's numeric media-status codes.
 *
 * Mirrors Floppy's `MEDIA_STATUS_MAP` (`src/api/helpers.py`) and
 * `MEDIA_LIST_STATUS_BY_CODE` (`src/app/media_list_filters.py`).
 *
 * Note the order: **Paused is 2 and Completed is 3**. It is neither alphabetical
 * nor the order the Floppy web UI lists them in, and assuming Completed is 2
 * silently marks items as Paused.
 */
object MediaStatus {
    const val PLANNING = 0
    const val IN_PROGRESS = 1
    const val PAUSED = 2
    const val COMPLETED = 3
    const val DROPPED = 4

    /** Code + label pairs in Floppy's own wording, for filter chips. */
    val options: List<Pair<Int, String>> = listOf(
        PLANNING to "Planning",
        IN_PROGRESS to "In progress",
        PAUSED to "Paused",
        COMPLETED to "Completed",
        DROPPED to "Dropped",
    )
}
