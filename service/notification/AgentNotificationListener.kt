package com.aiagent.assistant.service.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Step 2: শুধু registration stub, যাতে Settings > Notification access-এ অ্যাপটা দেখা যায়।
 * Step 10-এ notification read/reply আর notification-ভিত্তিক automation যোগ হবে।
 * OTP কখনো পড়া বা সংরক্ষণ করা হবে না।
 */
class AgentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Step 10
    }
}
