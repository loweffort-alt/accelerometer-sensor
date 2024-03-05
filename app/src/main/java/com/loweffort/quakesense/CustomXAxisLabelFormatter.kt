package com.loweffort.quakesense

import com.jjoe64.graphview.DefaultLabelFormatter
import java.text.SimpleDateFormat
import java.util.*

class CustomXAxisLabelFormatter : DefaultLabelFormatter() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    override fun formatLabel(value: Double, isValueX: Boolean): String {
        if (isValueX) {
            return timeFormat.format(Date(value.toLong()))
        }
        return super.formatLabel(value, isValueX)
    }
}