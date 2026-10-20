package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.LibraryType
import io.github.catadoxy.disketarr.data.network.DisketarrApiService

/**
 * Where Floppy files the anime.
 *
 * The user decides whether anime belongs in the TV library or in the Anime
 * library; Floppy keeps that in the `anime_library_mode` preference. It decides
 * whether there is an Anime library at all - and with it, whether the app has
 * an Anime tab.
 */
internal object AnimeLibraryMode {

    /** Anime has its own library. */
    const val ANIME = "anime"

    /** Anime is filed in the TV library, so there is no Anime library. */
    const val TV = "tv"

    /** Anime can be in either library. */
    const val BOTH = "both"

    /**
     * The mode Floppy is set to, or null when it does not say.
     *
     * The answer comes from the user preferences, which need the `user:read`
     * scope - a token without it leaves the app guessing.
     */
    suspend fun read(service: DisketarrApiService): String? {
        val response = runCatching { service.getUserPreferences() }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        val preferences = response.body()
            ?.get("preferences")
            ?.toStringKeyMapOrNull()
            ?: return null
        return preferences["anime_library_mode"]
            ?.toString()
            ?.lowercase()
            ?.takeIf { it == ANIME || it == TV || it == BOTH }
    }

    /**
     * True when anime has a library of its own.
     *
     * When anime is filed in the TV library, the Shows tab lists it and there
     * is nothing for an Anime tab to show.
     */
    fun hasAnimeLibrary(mode: String?): Boolean = mode != TV

    /** The library the anime of a title belong in, given the mode. */
    fun animeLibrary(mode: String?): String = when (mode) {
        TV -> LibraryType.TV
        else -> LibraryType.ANIME
    }

    /**
     * The categories the app shows, given the user's selection and Floppy's
     * anime setting.
     *
     * With the anime in the TV library there is no Anime library to show, so
     * the Anime tab is taken off - and it comes back when the setting changes
     * again.
     */
    fun effectiveCategories(selected: Set<String>, mode: String?): Set<String> =
        if (hasAnimeLibrary(mode)) selected else selected - "anime"
}
