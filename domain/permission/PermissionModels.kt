package com.aiagent.assistant.domain.permission

enum class PermissionKind {
    /** অ্যাপের ভেতরে dialog দিয়ে চাওয়া হয় */
    RUNTIME,

    /** Settings-এর আলাদা page থেকে user-কে চালু করতে হয় */
    SPECIAL,

    /** Install-এর সময় নিজে থেকে দেওয়া হয় */
    NORMAL,

    /** প্রতিবার ব্যবহারের সময় Android নিজে চায় (যেমন screen capture) */
    ON_DEMAND,

    /** আলাদা permission লাগে না */
    INFO
}

enum class PermissionStatus(val isSettled: Boolean) {
    GRANTED(true),
    DENIED(false),
    NOT_SUPPORTED(true),
    ON_DEMAND(true),
    NOT_NEEDED(true)
}

enum class PermissionId {
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATION_LISTENER,
    MICROPHONE,
    SPEECH_RECOGNITION,
    STORAGE,
    CAMERA,
    CONTACTS,
    PHONE,
    SMS,
    CALENDAR,
    ALARM_REMINDER,
    LOCATION,
    BACKGROUND_LOCATION,
    BLUETOOTH,
    WIFI_NETWORK,
    MEDIA_CONTROL,
    SCREEN_CAPTURE,
    CLIPBOARD,
    APP_USAGE,
    BATTERY_OPTIMIZATION,
    BACKGROUND_SERVICE,
    INSTALL_UNKNOWN_APPS,
    FILE_SHARING,
    NFC,
    BIOMETRIC,
    VIBRATE,
    WAKE_LOCK,
    WRITE_SETTINGS,
    DND_ACCESS
}

data class PermissionDef(
    val id: PermissionId,
    val kind: PermissionKind,
    val nameEn: String,
    val nameBn: String,
    val whyEn: String,
    val whyBn: String,
    val required: Boolean = false,
    val highlighted: Boolean = false,
    /** Android version (SDK_INT) অনুযায়ী runtime permission-এর তালিকা */
    val runtime: (Int) -> List<String> = { emptyList() },
    /** NORMAL permission-এর নাম (status যাচাইয়ের জন্য) */
    val normal: List<String> = emptyList()
)
