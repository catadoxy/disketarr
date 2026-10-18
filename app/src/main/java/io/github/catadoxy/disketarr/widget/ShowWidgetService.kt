package io.github.catadoxy.disketarr.widget

import android.content.Intent
import android.widget.RemoteViewsService

class ShowWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ShowWidgetFactory(applicationContext, intent)
}