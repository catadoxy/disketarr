package io.github.catadoxy.disketarr.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.catadoxy.disketarr.R
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.network.ApiClient
import io.github.catadoxy.disketarr.viewmodel.asUpcomingItems
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class ShowWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<SearchResult>()

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        items.clear()
        val settings = SettingsStore(context)
        if (!settings.isConfigured) return

        val today = LocalDate.now()
        val fetched = runCatching {
            runBlocking {
                ApiClient.getService(settings)
                    .upcomingCalendar(month = today.monthValue, year = today.year, limit = 20)
                    .asUpcomingItems()
            }
        }.getOrDefault(emptyList())

        val soon = fetched.filter { item ->
            val air = item.nextEpisode?.airDate?.take(10) ?: return@filter false
            val date = runCatching { LocalDate.parse(air) }.getOrNull() ?: return@filter false
            !date.isBefore(today) && !date.isAfter(today.plusDays(7))
        }.sortedBy { it.nextEpisode?.airDate }.take(8)

        items.addAll(soon)
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_list_item)
        views.setTextViewText(R.id.item_title, item.title ?: "Untitled")
        val subtitle = listOfNotNull(
            item.nextEpisode?.let { "S${it.seasonNumber ?: 1}E${it.episodeNumber}" },
            item.nextEpisode?.airDate?.take(10),
        ).joinToString(" · ")
        views.setTextViewText(R.id.item_subtitle, subtitle)

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