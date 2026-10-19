package com.koodipardik.nordprice.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Entry point the launcher binds to; the metadata lives in `res/xml/nordprice_widget_info.xml`. */
class NordPriceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NordPriceWidget()
}
