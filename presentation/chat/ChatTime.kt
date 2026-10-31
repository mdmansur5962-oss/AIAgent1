package com.aiagent.assistant.presentation.chat

import android.content.Context
import android.text.format.DateUtils

/** আজকের হলে সময় (10:24), নইলে তারিখ (Oct 25) */
fun formatChatTime(context: Context, millis: Long): String =
    if (DateUtils.isToday(millis)) {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
