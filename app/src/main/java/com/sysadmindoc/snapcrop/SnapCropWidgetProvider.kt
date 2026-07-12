package com.sysadmindoc.snapcrop

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SnapCropWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_snapcrop).apply {
            setOnClickPendingIntent(
                R.id.widget_latest,
                activityIntent(context, REQUEST_LATEST, ACTION_EDIT_LATEST)
            )
            setOnClickPendingIntent(
                R.id.widget_quick_crop,
                activityIntent(context, REQUEST_QUICK_CROP, ACTION_QUICK_CROP)
            )
            setOnClickPendingIntent(
                R.id.widget_gallery,
                activityIntent(context, REQUEST_GALLERY, ACTION_GALLERY)
            )
        }

    private fun activityIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        internal const val ACTION_EDIT_LATEST = "com.sysadmindoc.snapcrop.WIDGET_EDIT_LATEST"
        internal const val ACTION_QUICK_CROP = "com.sysadmindoc.snapcrop.WIDGET_QUICK_CROP"
        internal const val ACTION_GALLERY = "com.sysadmindoc.snapcrop.WIDGET_GALLERY"
        private const val REQUEST_LATEST = 41
        private const val REQUEST_QUICK_CROP = 42
        private const val REQUEST_GALLERY = 43
    }
}
