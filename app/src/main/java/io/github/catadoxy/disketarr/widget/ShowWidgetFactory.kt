package io.github.catadoxy.disketarr.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.catadoxy.disketarr.R
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.network.ApiClient
import io.github.catadoxy.disketarr.viewmodel.asHistoryItems
import io.github.catadoxy.disketarr.viewmodel.asUpcomingItems
import io.github.catadoxy.disketarr.viewmodel.extractRowsSafely
import io.github.catadoxy.disketarr.viewmodel.toSearchResults
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class ShowWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
    private val items = mutableListOf<SearchResult>()

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        items.clear()
        val settings = SettingsStore(context)
        if (!settings.isConfigured) return

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val type = prefs.getString("type_$appWidgetId", "upcoming") ?: "upcoming"
        val sort = prefs.getString("sort_$appWidgetId", "latest") ?: "latest"

        val today = LocalDate.now()
        val fetched: List<SearchResult> = runCatching {
            runBlocking {
                val service = ApiClient.getService(settings)
                when (type) {
                    "recent" -> service.listHistoryRaw(limit = 30, flat = "1")
                        .asHistoryItems(setOf("tv", "season", "episode"))
                    "shows" -> service.listTrackedMediaRaw(mediaType = "tv", limit = 50)
                        .extractRowsSafely().toSearchResults()
                    else -> service.upcomingCalendar(month = today.monthValue, year = today.year, limit = 20)
                        .asUpcomingItems()
                        .filter { item ->
                            val air = item.nextEpisode?.airDate?.take(10) ?: return@filter false
                            val date = runCatching { LocalDate.parse(air) }.getOrNull() ?: return@filter false
                            !date.isBefore(today) && !date.isAfter(today.plusDays(7))
                        }
                }
            }
        }.getOrDefault(emptyList())

        val sorted = when (sort) {
            "title" -> fetched.sortedBy { it.title.orEmpty() }
            "oldest" -> fetched.sortedBy { it.nextEpisode?.airDate.orEmpty() }
            "lastwatched" -> fetched.sortedByDescending { it.lastWatchedAt.orEmpty() }
            "remaining" -> fetched.sortedByDescending { it.episodesLeft ?: 0 }
            else -> fetched.sortedBy { it.nextEpisode?.airDate.orEmpty() }
        }

        items.addAll(sorted.take(10))
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_list_item)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val largeFont = prefs.getBoolean("largefont_$appWidgetId", false)
        val titleSize = if (largeFont) 16f else 13f
        val subtitleSize = if (largeFont) 13f else 11f
        views.setTextViewText(R.id.item_title, item.title ?: "Untitled")
        views.setFloat(R.id.item_title, "setTextSize", titleSize)
        val subtitle = listOfNotNull(
            item.nextEpisode?.let { "S${it.seasonNumber ?: 1}E${it.episodeNumber}" },
            item.nextEpisode?.airDate?.take(10),
        ).joinToString(" · ")
        views.setTextViewText(R.id.item_subtitle, subtitle)
        views.setFloat(R.id.item_subtitle, "setTextSize", subtitleSize)

        val fillInIntent = Intent().apply {
            putExtra("source", item.source)
            putExtra("media_type", item.mediaType)
            putExtra("media_id", item.mediaId)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun onDestroy() {
        items.clear()
    }
}