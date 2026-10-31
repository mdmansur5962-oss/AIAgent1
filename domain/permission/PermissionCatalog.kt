package com.aiagent.assistant.domain.permission

import android.Manifest

object PermissionCatalog {

    private fun always(vararg names: String): (Int) -> List<String> = { _ -> names.toList() }

    val all: List<PermissionDef> = listOf(

        // ───────── চারটি বিশেষ permission (highlighted) ─────────
        PermissionDef(
            id = PermissionId.ACCESSIBILITY,
            kind = PermissionKind.SPECIAL,
            nameEn = "Accessibility Service",
            nameBn = "অ্যাক্সেসিবিলিটি সার্ভিস",
            whyEn = "Lets the assistant read other apps' screens and tap, type, scroll and swipe for you. " +
                "Needed for cross-app automation (Chrome, YouTube, CapCut, WhatsApp...). " +
                "It never works inside banking apps, OTP screens or password fields.",
            whyBn = "অন্য অ্যাপের screen পড়তে এবং আপনার হয়ে ক্লিক, টাইপ, scroll ও swipe করতে লাগে। " +
                "Chrome, YouTube, CapCut, WhatsApp-এর মতো অ্যাপে cross-app automation-এর জন্য জরুরি। " +
                "ব্যাংকিং অ্যাপ, OTP screen বা password field-এ এটি কখনো কাজ করবে না।",
            required = true,
            highlighted = true
        ),
        PermissionDef(
            id = PermissionId.OVERLAY,
            kind = PermissionKind.SPECIAL,
            nameEn = "Display over other apps",
            nameBn = "অন্য অ্যাপের উপরে দেখানো",
            whyEn = "Shows the floating assistant bubble, quick action panel and live controls on top of other apps.",
            whyBn = "অন্য অ্যাপ ব্যবহারের সময়ও floating assistant bubble, quick action panel ও live control দেখাতে লাগে।",
            required = true,
            highlighted = true
        ),
        PermissionDef(
            id = PermissionId.NOTIFICATION_LISTENER,
            kind = PermissionKind.SPECIAL,
            nameEn = "Notification Access",
            nameBn = "নোটিফিকেশন অ্যাক্সেস",
            whyEn = "Reads notifications and replies to them (WhatsApp, Messenger, Instagram) and powers " +
                "notification-based automation. OTP messages are never read or stored.",
            whyBn = "নোটিফিকেশন পড়তে ও reply করতে (WhatsApp, Messenger, Instagram) এবং notification-ভিত্তিক " +
                "automation চালাতে লাগে। OTP কখনো পড়া বা সংরক্ষণ করা হবে না।",
            required = true,
            highlighted = true
        ),
        PermissionDef(
            id = PermissionId.BATTERY_OPTIMIZATION,
            kind = PermissionKind.SPECIAL,
            nameEn = "Battery Optimization Exemption",
            nameBn = "ব্যাটারি অপ্টিমাইজেশন ছাড়",
            whyEn = "Keeps the background agent, alarms and voice listening alive, even in Doze mode.",
            whyBn = "Doze mode-এও যাতে background agent, alarm ও voice listening চালু থাকে।",
            required = true,
            highlighted = true
        ),

        // ───────── অন্যান্য প্রয়োজনীয় ─────────
        PermissionDef(
            id = PermissionId.MICROPHONE,
            kind = PermissionKind.RUNTIME,
            nameEn = "Microphone",
            nameBn = "মাইক্রোফোন",
            whyEn = "Voice commands, live voice conversation and wake word detection.",
            whyBn = "Voice command, live voice কথোপকথন ও wake word শোনার জন্য।",
            required = true,
            runtime = always(Manifest.permission.RECORD_AUDIO)
        ),
        PermissionDef(
            id = PermissionId.SPEECH_RECOGNITION,
            kind = PermissionKind.RUNTIME,
            nameEn = "Speech Recognition",
            nameBn = "স্পিচ রিকগনিশন",
            whyEn = "Converts your speech to text in Bangla, English and other languages with auto language " +
                "detection. Uses the microphone permission and your phone's speech service.",
            whyBn = "বাংলা, ইংরেজি ও অন্যান্য ভাষায় আপনার কথা লেখায় রূপান্তর করে। মাইক্রোফোনের অনুমতি ও " +
                "ফোনের speech service ব্যবহার করে।",
            required = true,
            runtime = always(Manifest.permission.RECORD_AUDIO)
        ),
        PermissionDef(
            id = PermissionId.STORAGE,
            kind = PermissionKind.RUNTIME,
            nameEn = "Storage / Media",
            nameBn = "স্টোরেজ / মিডিয়া",
            whyEn = "Reads gallery photos, videos and audio, and finds downloaded files so the assistant can " +
                "edit, share or save media.",
            whyBn = "Gallery-র ছবি, ভিডিও, অডিও ও ডাউনলোড করা ফাইল পড়তে লাগে, যাতে edit, share বা save করা যায়।",
            required = true,
            runtime = { sdk ->
                when {
                    sdk >= 33 -> listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                    sdk >= 29 -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    else -> listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }
        ),
        PermissionDef(
            id = PermissionId.BACKGROUND_SERVICE,
            kind = PermissionKind.RUNTIME,
            nameEn = "Background Service & Notifications",
            nameBn = "ব্যাকগ্রাউন্ড সার্ভিস ও নোটিফিকেশন",
            whyEn = "Runs the persistent background agent with its always-on notification. " +
                "Android 13+ needs the notification permission for this.",
            whyBn = "সবসময়-চালু notification-সহ persistent background agent চালাতে। " +
                "Android 13+ এ এর জন্য notification permission লাগে।",
            required = true,
            runtime = { sdk ->
                if (sdk >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            }
        ),

        // ───────── ঐচ্ছিক ─────────
        PermissionDef(
            id = PermissionId.CAMERA,
            kind = PermissionKind.RUNTIME,
            nameEn = "Camera",
            nameBn = "ক্যামেরা",
            whyEn = "Photo/video capture, QR/barcode scan, document scan and vision help.",
            whyBn = "ছবি/ভিডিও তোলা, QR/barcode scan, document scan ও vision-ভিত্তিক সাহায্যের জন্য।",
            runtime = always(Manifest.permission.CAMERA)
        ),
        PermissionDef(
            id = PermissionId.CONTACTS,
            kind = PermissionKind.RUNTIME,
            nameEn = "Contacts",
            nameBn = "কন্টাক্ট",
            whyEn = "Searches your contacts so you can say \"call Rahim\".",
            whyBn = "\"রহিমকে কল করো\" বললে contact খুঁজে বের করতে।",
            runtime = always(Manifest.permission.READ_CONTACTS)
        ),
        PermissionDef(
            id = PermissionId.PHONE,
            kind = PermissionKind.RUNTIME,
            nameEn = "Phone",
            nameBn = "ফোন",
            whyEn = "Starts calls (always after your confirmation) and detects call state.",
            whyBn = "কল শুরু করতে (সবসময় আপনার confirmation-এর পর) ও call state বুঝতে।",
            runtime = always(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        ),
        PermissionDef(
            id = PermissionId.SMS,
            kind = PermissionKind.RUNTIME,
            nameEn = "SMS",
            nameBn = "এসএমএস",
            whyEn = "Reads SMS, drafts messages and sends them only after your confirmation. " +
                "OTP messages are never read or stored.",
            whyBn = "SMS পড়তে ও draft করতে, আর আপনার confirmation-এর পরেই পাঠাতে। " +
                "OTP কখনো পড়া বা সংরক্ষণ করা হবে না।",
            runtime = always(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
        ),
        PermissionDef(
            id = PermissionId.CALENDAR,
            kind = PermissionKind.RUNTIME,
            nameEn = "Calendar",
            nameBn = "ক্যালেন্ডার",
            whyEn = "Creates, reads, updates and deletes events and reminders.",
            whyBn = "Event ও reminder তৈরি, দেখা, বদলানো ও মুছতে।",
            runtime = always(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        ),
        PermissionDef(
            id = PermissionId.ALARM_REMINDER,
            kind = PermissionKind.SPECIAL,
            nameEn = "Alarms & Reminders (exact alarm)",
            nameBn = "অ্যালার্ম ও রিমাইন্ডার",
            whyEn = "Schedules exact alarms, reminders and repeating tasks so they ring on time.",
            whyBn = "ঠিক সময়ে বাজার জন্য exact alarm, reminder ও repeating task schedule করতে।"
        ),
        PermissionDef(
            id = PermissionId.LOCATION,
            kind = PermissionKind.RUNTIME,
            nameEn = "Location",
            nameBn = "লোকেশন",
            whyEn = "Current location, nearby search and Google Maps navigation.",
            whyBn = "বর্তমান অবস্থান, কাছাকাছি খোঁজা ও Google Maps navigation-এর জন্য।",
            runtime = always(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ),
        PermissionDef(
            id = PermissionId.BACKGROUND_LOCATION,
            kind = PermissionKind.RUNTIME,
            nameEn = "Background Location",
            nameBn = "ব্যাকগ্রাউন্ড লোকেশন",
            whyEn = "Location-based automation and geofencing while the app is in the background. " +
                "Enable Location first; Android then opens a settings page where you choose \"Allow all the time\".",
            whyBn = "App ব্যাকগ্রাউন্ডে থাকলেও location-ভিত্তিক automation ও geofencing-এর জন্য। " +
                "আগে Location চালু করুন; তারপর Android settings page খুলবে, সেখানে \"Allow all the time\" বেছে নিন।",
            runtime = { sdk ->
                if (sdk >= 29) listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyList()
            }
        ),
        PermissionDef(
            id = PermissionId.BLUETOOTH,
            kind = PermissionKind.RUNTIME,
            nameEn = "Bluetooth",
            nameBn = "ব্লুটুথ",
            whyEn = "Scans, connects and disconnects Bluetooth devices (runtime permission on Android 12+).",
            whyBn = "Bluetooth device খুঁজতে, connect ও disconnect করতে (Android 12+ এ runtime permission)।",
            runtime = { sdk ->
                if (sdk >= 31) {
                    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    emptyList()
                }
            }
        ),
        PermissionDef(
            id = PermissionId.WIFI_NETWORK,
            kind = PermissionKind.NORMAL,
            nameEn = "Wi-Fi / Network State",
            nameBn = "ওয়াই-ফাই / নেটওয়ার্ক",
            whyEn = "Checks internet and Wi-Fi status. Given automatically at install. " +
                "Turning Wi-Fi on/off is done through the Settings screens.",
            whyBn = "ইন্টারনেট ও Wi-Fi অবস্থা দেখতে। Install-এর সময় স্বয়ংক্রিয়ভাবে দেওয়া হয়। " +
                "Wi-Fi চালু/বন্ধ Settings screen-এর মাধ্যমে হবে।",
            normal = listOf(
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        ),
        PermissionDef(
            id = PermissionId.MEDIA_CONTROL,
            kind = PermissionKind.SPECIAL,
            nameEn = "Media Control",
            nameBn = "মিডিয়া কন্ট্রোল",
            whyEn = "Play, pause, next, previous, volume and \"now playing\" info for music and video apps. " +
                "Uses Notification Access.",
            whyBn = "Music/video অ্যাপে play, pause, next, previous, volume ও now playing তথ্যের জন্য। " +
                "Notification Access ব্যবহার করে।"
        ),
        PermissionDef(
            id = PermissionId.SCREEN_CAPTURE,
            kind = PermissionKind.ON_DEMAND,
            nameEn = "Screen Capture (MediaProjection)",
            nameBn = "স্ক্রিন ক্যাপচার",
            whyEn = "Screenshots, visual screen analysis, OCR fallback and optional screen recording. " +
                "Android asks for permission each time it is used, so there is nothing to enable here.",
            whyBn = "Screenshot, screen-এর visual বিশ্লেষণ, OCR fallback ও ঐচ্ছিক screen recording-এর জন্য। " +
                "প্রতিবার ব্যবহারের সময় Android নিজে অনুমতি চায়, তাই এখানে কিছু চালু করতে হবে না।"
        ),
        PermissionDef(
            id = PermissionId.CLIPBOARD,
            kind = PermissionKind.INFO,
            nameEn = "Clipboard",
            nameBn = "ক্লিপবোর্ড",
            whyEn = "Copy and paste automation. No permission is needed, but Android only lets an app " +
                "read the clipboard while it is in front.",
            whyBn = "Copy/paste automation-এর জন্য। আলাদা permission লাগে না, তবে Android শুধু সামনে থাকা " +
                "অ্যাপকে clipboard পড়তে দেয়।"
        ),
        PermissionDef(
            id = PermissionId.APP_USAGE,
            kind = PermissionKind.SPECIAL,
            nameEn = "App Usage Access",
            nameBn = "অ্যাপ ব্যবহারের তথ্য",
            whyEn = "Installed apps list, app usage information and screen time analysis.",
            whyBn = "Install করা অ্যাপের তালিকা, app usage তথ্য ও screen time বিশ্লেষণের জন্য।"
        ),
        PermissionDef(
            id = PermissionId.INSTALL_UNKNOWN_APPS,
            kind = PermissionKind.SPECIAL,
            nameEn = "Install Unknown Apps",
            nameBn = "অজানা উৎস থেকে অ্যাপ ইনস্টল",
            whyEn = "Only to guide an APK install when you ask for it. Installation always needs your approval.",
            whyBn = "আপনি চাইলে শুধু APK install প্রক্রিয়ায় গাইড করতে। Install সবসময় আপনার অনুমোদনেই হবে।"
        ),
        PermissionDef(
            id = PermissionId.FILE_SHARING,
            kind = PermissionKind.INFO,
            nameEn = "File Sharing",
            nameBn = "ফাইল শেয়ার",
            whyEn = "Share sheet and cross-app file transfer. No permission needed.",
            whyBn = "Share sheet ও এক অ্যাপ থেকে আরেক অ্যাপে ফাইল পাঠাতে। আলাদা permission লাগে না।"
        ),
        PermissionDef(
            id = PermissionId.NFC,
            kind = PermissionKind.NORMAL,
            nameEn = "NFC",
            nameBn = "এনএফসি",
            whyEn = "NFC status check and NFC-based automation, if your phone supports it.",
            whyBn = "ফোনে NFC থাকলে NFC অবস্থা দেখতে ও NFC-ভিত্তিক automation-এর জন্য।",
            normal = listOf(Manifest.permission.NFC)
        ),
        PermissionDef(
            id = PermissionId.BIOMETRIC,
            kind = PermissionKind.SPECIAL,
            nameEn = "Fingerprint / Biometric",
            nameBn = "ফিঙ্গারপ্রিন্ট / বায়োমেট্রিক",
            whyEn = "Only for unlocking this app. The assistant never uses banking credentials.",
            whyBn = "শুধু এই অ্যাপ আনলক করার জন্য। Assistant কখনো banking credential ব্যবহার করবে না।"
        ),
        PermissionDef(
            id = PermissionId.VIBRATE,
            kind = PermissionKind.NORMAL,
            nameEn = "Vibrate",
            nameBn = "ভাইব্রেশন",
            whyEn = "Haptic feedback for task completion and voice replies.",
            whyBn = "Task শেষ ও voice উত্তরের সময় haptic feedback দিতে।",
            normal = listOf(Manifest.permission.VIBRATE)
        ),
        PermissionDef(
            id = PermissionId.WAKE_LOCK,
            kind = PermissionKind.NORMAL,
            nameEn = "Wake Lock",
            nameBn = "ওয়েক লক",
            whyEn = "Keeps the phone awake during long downloads and processing.",
            whyBn = "দীর্ঘ download ও processing-এর সময় ফোন ঘুমিয়ে পড়া ঠেকাতে।",
            normal = listOf(Manifest.permission.WAKE_LOCK)
        ),
        PermissionDef(
            id = PermissionId.WRITE_SETTINGS,
            kind = PermissionKind.SPECIAL,
            nameEn = "Modify System Settings",
            nameBn = "সিস্টেম সেটিংস পরিবর্তন",
            whyEn = "Changes brightness and some system settings on your command.",
            whyBn = "আপনার কথায় brightness ও কিছু system setting বদলাতে।"
        ),
        PermissionDef(
            id = PermissionId.DND_ACCESS,
            kind = PermissionKind.SPECIAL,
            nameEn = "Do Not Disturb Access",
            nameBn = "ডু নট ডিস্টার্ব অ্যাক্সেস",
            whyEn = "Turns Do Not Disturb on or off on your command.",
            whyBn = "আপনার কথায় Do Not Disturb চালু/বন্ধ করতে।"
        )
    )

    private val byIdMap: Map<PermissionId, PermissionDef> = all.associateBy { it.id }

    fun byId(id: PermissionId): PermissionDef = byIdMap.getValue(id)
}
