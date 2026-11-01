package com.example.kronos.notifications

interface AlarmGateway {
    fun schedule(
        requestCode: Int,
        triggerAtMillis: Long,
        itemId: Long,
        occurrenceAtMillis: Long,
        title: String,
        channelId: String
    )

    fun cancel(requestCode: Int)

    fun scheduleWidget(requestCode: Int, triggerAtMillis: Long) = Unit

    fun cancelWidget(requestCode: Int) = Unit
}
