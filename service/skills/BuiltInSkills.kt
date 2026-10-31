package com.aiagent.assistant.service.skills

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings

/**
 * ১৮টি অ্যাপের জ্ঞান। প্রতিটিতে: package, নাম/ডাকনাম (বাংলাসহ), কাজের টিপস, বোতামের লেখার ধারণা,
 * আর deep link/intent দিয়ে নির্ভরযোগ্য কাজ। অ্যাপের স্ক্রিন বদলায়, তাই টিপসগুলো "সাধারণত এমন" ধরনের।
 */
object BuiltInSkills {

    private fun action(
        id: String,
        title: String,
        description: String,
        params: List<SkillParam> = emptyList(),
        risk: ActionRisk = ActionRisk.SAFE,
        requiresApp: Boolean = false,
        run: (SkillRuntime, Map<String, String>) -> SkillOutcome
    ) = SkillAction(id, title, description, params, risk, requiresApp, run)

    private fun skill(
        id: String,
        name: String,
        aliases: List<String>,
        packages: List<String>,
        description: String,
        knowledge: String,
        loginRequired: Boolean = false,
        hints: Map<String, List<String>> = emptyMap(),
        actions: List<SkillAction> = emptyList()
    ): AppSkill {
        val open = action("open", "Open $name", "Open the $name app.", requiresApp = true) { rt, _ ->
            SkillIntents.launchApp(rt)
        }
        return AppSkill(
            id = id,
            name = name,
            aliases = aliases,
            packages = packages,
            description = description,
            loginRequired = loginRequired,
            knowledge = knowledge,
            hints = hints,
            actions = listOf(open) + actions,
            builtIn = true
        )
    }

    private fun p(name: String, description: String, required: Boolean = true) = SkillParam(name, description, required)

    // ───────────── Chrome ─────────────
    private val chrome = skill(
        id = "chrome",
        name = "Chrome",
        aliases = listOf("chrome", "browser", "web", "ক্রোম", "ব্রাউজার"),
        packages = listOf("com.android.chrome", "com.chrome.beta"),
        description = "Web browser: open sites, search the web, download files.",
        knowledge = "The address bar is at the top (or bottom); tap it to type a URL or search. The tab switcher shows the tab count; the ⋮ menu has Downloads, History and Settings. " +
            "To download: long-press a link or image and choose \"Download link\"/\"Download image\", or use the site's own Download button. " +
            "Many sites (YouTube, streaming services) do not allow downloads; only download things you own or have the right to use. " +
            "A bank or payment website is never opened by the assistant.",
        hints = mapOf(
            "address" to listOf("Search or type web address", "Search or type URL", "ঠিকানা"),
            "tabs" to listOf("Switch or close tabs", "ট্যাব"),
            "downloads" to listOf("Downloads", "ডাউনলোড")
        ),
        actions = listOf(
            action("open_url", "Open a website", "Open an http/https web address in Chrome.", listOf(p("url", "Web address, e.g. example.com"))) { rt, a ->
                val url = SkillIntents.safeWebUrl(a["url"].orEmpty())
                if (url == null) SkillOutcome(false, "Invalid or protected address · ঠিকানা ঠিক নয় বা সুরক্ষিত")
                else SkillIntents.viewUrl(rt, url)
            },
            action("search", "Search the web", "Search Google for a phrase.", listOf(p("query", "What to search for"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "https://www.google.com/search?q=${SkillIntents.enc(q)}")
            }
        )
    )

    // ───────────── YouTube ─────────────
    private val youtube = skill(
        id = "youtube",
        name = "YouTube",
        aliases = listOf("youtube", "yt", "ইউটিউব"),
        packages = listOf("com.google.android.youtube"),
        description = "Watch and search videos.",
        knowledge = "The search icon (magnifier) is at the top right. Results are a vertical list; tap a video to play. Under the player: Like, Share, Download (only for YouTube Premium / where offered), Save. " +
            "Shorts are in the bottom bar. Subscribing and commenting change the account, so ask the user first. Videos cannot be saved to the gallery from YouTube unless the official Download feature is offered.",
        hints = mapOf(
            "like" to listOf("like this video", "Like", "লাইক"),
            "subscribe" to listOf("Subscribe", "সাবস্ক্রাইব"),
            "download" to listOf("Download", "ডাউনলোড"),
            "save" to listOf("Save", "Save to playlist", "সংরক্ষণ")
        ),
        actions = listOf(
            action("search", "Search videos", "Search YouTube.", listOf(p("query", "What to search for"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "https://www.youtube.com/results?search_query=${SkillIntents.enc(q)}")
            },
            action("open_video", "Open a video", "Open a video by its 11-character id or YouTube link.", listOf(p("video", "Video id or YouTube link"))) { rt, a ->
                val v = a["video"].orEmpty().trim()
                val url = when {
                    Regex("^[A-Za-z0-9_-]{11}$").matches(v) -> "https://www.youtube.com/watch?v=$v"
                    else -> SkillIntents.safeWebUrl(v)?.takeIf { it.contains("youtube.com") || it.contains("youtu.be") }
                }
                if (url == null) SkillOutcome(false, "Invalid video id or link · ভিডিও আইডি বা লিংক ঠিক নয়")
                else SkillIntents.viewUrl(rt, url)
            }
        )
    )

    // ───────────── Facebook ─────────────
    private val facebook = skill(
        id = "facebook",
        name = "Facebook",
        aliases = listOf("facebook", "fb", "ফেসবুক"),
        packages = listOf("com.facebook.katana", "com.facebook.lite"),
        description = "Social network: feed, posts, messages.",
        loginRequired = true,
        knowledge = "Top tabs: Home, Friends, Video, Marketplace, Notifications, Menu. Create a post by tapping \"What's on your mind?\" at the top of Home; the photo icon adds media; the Post button publishes. " +
            "Posting, commenting, sending friend requests and messages are visible to other people, so always confirm with the user first. The user must be logged in.",
        hints = mapOf(
            "create_post" to listOf("What's on your mind?", "Create a post", "আপনার মনে কী আছে?"),
            "post" to listOf("POST", "Post", "পোস্ট"),
            "photo" to listOf("Photo", "Photo/video", "ছবি")
        ),
        actions = listOf(
            action("search", "Search Facebook", "Search people, pages and posts.", listOf(p("query", "What to search for"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "https://www.facebook.com/search/top?q=${SkillIntents.enc(q)}")
            }
        )
    )

    // ───────────── CapCut ─────────────
    private val capcut = skill(
        id = "capcut",
        name = "CapCut",
        aliases = listOf("capcut", "cap cut", "video editor", "ক্যাপকাট"),
        packages = listOf("com.lemon.lvoverseas", "com.lemon.lvoverseaslite"),
        description = "Video editor.",
        knowledge = "Home screen: \"New project\" opens the media picker; select the video(s) then tap \"Add\". The timeline editor has a toolbar at the bottom (Edit, Audio, Text, Stickers, Overlay, Effects, Captions, Filters, Adjust); " +
            "scroll the toolbar sideways for more. Tap a clip on the timeline to select it before editing. \"Export\" is at the top right: before exporting choose resolution (e.g. 1080p or 4K for HD), frame rate and bitrate, then tap Export. " +
            "Some effects are Pro-only and show a crown icon; do not buy or subscribe. CapCut has no public deep links, so everything is done by tapping the screen.",
        loginRequired = false,
        hints = mapOf(
            "new_project" to listOf("New project", "নতুন প্রজেক্ট"),
            "add" to listOf("Add", "যোগ করুন"),
            "export" to listOf("Export", "এক্সপোর্ট"),
            "text" to listOf("Text", "টেক্সট"),
            "audio" to listOf("Audio", "অডিও"),
            "effects" to listOf("Effects", "ইফেক্ট"),
            "captions" to listOf("Captions", "ক্যাপশন"),
            "resolution" to listOf("Resolution", "রেজোলিউশন")
        )
    )

    // ───────────── Gallery ─────────────
    private val gallery = skill(
        id = "gallery",
        name = "Gallery / Photos",
        aliases = listOf("gallery", "photos", "google photos", "গ্যালারি", "ছবি", "ফটো"),
        packages = listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d"),
        description = "Browse photos and videos stored on the phone.",
        knowledge = "Tabs at the bottom: Photos, Collections/Albums, Search. Long-press a photo to start selecting, then tap more photos. The share icon sends the selection to another app. " +
            "Delete moves items to the trash; always confirm deletes with the user. Downloaded files are usually in the \"Download\" album.",
        hints = mapOf(
            "albums" to listOf("Albums", "Collections", "অ্যালবাম"),
            "photos" to listOf("Photos", "ছবি"),
            "select" to listOf("Select", "বাছুন"),
            "download_album" to listOf("Download", "Downloads", "ডাউনলোড")
        ),
        actions = listOf(
            action("view_photos", "Show photos", "Open the photo viewer on all images.") { rt, _ ->
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                if (rt.launch(intent)) SkillOutcome(true, "Opened photos") else SkillOutcome(false, "Could not open the gallery")
            }
        )
    )

    // ───────────── WhatsApp ─────────────
    private val whatsapp = skill(
        id = "whatsapp",
        name = "WhatsApp",
        aliases = listOf("whatsapp", "wa", "হোয়াটসঅ্যাপ", "হোয়াটস অ্যাপ"),
        packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
        description = "Messaging and calls.",
        loginRequired = true,
        knowledge = "Tabs: Chats, Updates, Communities, Calls. The green button at the bottom right starts a new chat; the magnifier searches chats. Inside a chat: the text box says \"Message\", the paperclip attaches files, and the Send arrow appears once text is typed. " +
            "Sending a message is visible to others, so confirm with the user before pressing Send. Phone numbers need the country code (Bangladesh: 880...). The \"open_chat\" action opens a chat with the text already typed but does NOT send it.",
        hints = mapOf(
            "new_chat" to listOf("New chat", "নতুন চ্যাট"),
            "message" to listOf("Message", "বার্তা"),
            "send" to listOf("Send", "পাঠান"),
            "attach" to listOf("Attach", "সংযুক্ত করুন"),
            "calls" to listOf("Calls", "কল")
        ),
        actions = listOf(
            action("open_chat", "Open a chat", "Open a chat with a phone number; optionally pre-fill a message (it is NOT sent).",
                listOf(p("phone", "Phone number with country code, e.g. 8801XXXXXXXXX"), p("message", "Message to pre-fill", required = false))) { rt, a ->
                val digits = a["phone"].orEmpty().filter { it.isDigit() }
                if (digits.length !in 8..15) {
                    SkillOutcome(false, "Phone number needs the country code · দেশের কোডসহ নম্বর দিন")
                } else {
                    val text = a["message"].orEmpty()
                    val url = "https://wa.me/$digits" + if (text.isNotBlank()) "?text=${SkillIntents.enc(text)}" else ""
                    SkillIntents.viewUrl(rt, url)
                }
            }
        )
    )

    // ───────────── Instagram ─────────────
    private val instagram = skill(
        id = "instagram",
        name = "Instagram",
        aliases = listOf("instagram", "insta", "ইনস্টাগ্রাম"),
        packages = listOf("com.instagram.android"),
        description = "Photo and video sharing.",
        loginRequired = true,
        knowledge = "Bottom bar: Home, Search, Create (+), Reels, Profile. Creating a post: tap + → Post/Story/Reel → pick media → Next → (edit) → Next → write a caption → Share. " +
            "\"Share\" publishes publicly, so always confirm with the user first. Likes, comments and follows are also visible to others.",
        hints = mapOf(
            "create" to listOf("Create", "New post", "তৈরি করুন"),
            "next" to listOf("Next", "পরবর্তী"),
            "share" to listOf("Share", "শেয়ার"),
            "caption" to listOf("Write a caption", "ক্যাপশন লিখুন")
        ),
        actions = listOf(
            action("open_profile", "Open a profile", "Open an Instagram profile by username.", listOf(p("username", "Instagram username"))) { rt, a ->
                val u = a["username"].orEmpty().trim().removePrefix("@")
                if (!Regex("^[A-Za-z0-9._]{1,30}$").matches(u)) SkillOutcome(false, "Invalid username · ইউজারনেম ঠিক নয়")
                else SkillIntents.viewUrl(rt, "https://www.instagram.com/$u/")
            }
        )
    )

    // ───────────── TikTok ─────────────
    private val tiktok = skill(
        id = "tiktok",
        name = "TikTok",
        aliases = listOf("tiktok", "tik tok", "টিকটক"),
        packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        description = "Short videos.",
        loginRequired = false,
        knowledge = "Bottom bar: Home, Friends, Create (+), Inbox, Profile. The search icon is at the top right. Swipe up to see the next video. Posting, commenting and following are visible to others, so confirm first.",
        hints = mapOf(
            "create" to listOf("Create", "তৈরি করুন"),
            "inbox" to listOf("Inbox", "ইনবক্স"),
            "profile" to listOf("Profile", "প্রোফাইল")
        ),
        actions = listOf(
            action("search", "Search TikTok", "Search videos and accounts.", listOf(p("query", "What to search for"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "https://www.tiktok.com/search?q=${SkillIntents.enc(q)}")
            }
        )
    )

    // ───────────── Gmail ─────────────
    private val gmail = skill(
        id = "gmail",
        name = "Gmail",
        aliases = listOf("gmail", "email", "mail", "জিমেইল", "ইমেইল", "মেইল"),
        packages = listOf("com.google.android.gm"),
        description = "Email.",
        loginRequired = true,
        knowledge = "The \"Compose\" pill is at the bottom right. The compose screen has To, Subject and the body; the paper-plane icon at the top right sends. Drafts are saved automatically when you go back. " +
            "Sending an email is irreversible, so confirm with the user. The \"compose\" action opens a draft but does NOT send it.",
        hints = mapOf(
            "compose" to listOf("Compose", "লিখুন"),
            "send" to listOf("Send", "পাঠান"),
            "search" to listOf("Search in mail", "মেইলে খুঁজুন"),
            "to" to listOf("To", "প্রাপক")
        ),
        actions = listOf(
            action("compose", "Write an email draft", "Open a new email draft (NOT sent).",
                listOf(p("to", "Recipient email", required = false), p("subject", "Subject", required = false), p("body", "Body text", required = false))) { rt, a ->
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                a["to"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(it.trim())) }
                a["subject"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
                a["body"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
                rt.packageName?.let { intent.setPackage(it) }
                var ok = rt.launch(intent)
                if (!ok) {
                    intent.setPackage(null)
                    ok = rt.launch(intent)
                }
                if (ok) SkillOutcome(true, "Draft opened, press send yourself") else SkillOutcome(false, "No email app found")
            }
        )
    )

    // ───────────── Maps ─────────────
    private val maps = skill(
        id = "maps",
        name = "Google Maps",
        aliases = listOf("maps", "google maps", "map", "navigation", "ম্যাপ", "মানচিত্র", "গুগল ম্যাপ"),
        packages = listOf("com.google.android.apps.maps"),
        description = "Maps, places and navigation.",
        knowledge = "The search bar is at the top. After choosing a place, tap \"Directions\", pick the travel mode (car, bike, walk, transit), then tap \"Start\" for turn-by-turn navigation. " +
            "Location permission is needed for \"your location\". Use the navigate action to start directions directly.",
        hints = mapOf(
            "directions" to listOf("Directions", "দিকনির্দেশ"),
            "start" to listOf("Start", "শুরু"),
            "search" to listOf("Search here", "Search Google Maps", "এখানে খুঁজুন")
        ),
        actions = listOf(
            action("navigate", "Start navigation", "Start turn-by-turn navigation to a place.", listOf(p("destination", "Place name or address"))) { rt, a ->
                val d = a["destination"].orEmpty()
                if (d.isBlank()) SkillIntents.missing("destination")
                else SkillIntents.viewUrl(rt, "google.navigation:q=${SkillIntents.enc(d)}")
            },
            action("search", "Search a place", "Show a place or category on the map.", listOf(p("query", "Place or category"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "geo:0,0?q=${SkillIntents.enc(q)}")
            }
        )
    )

    // ───────────── Play Store ─────────────
    private val playStore = skill(
        id = "play_store",
        name = "Play Store",
        aliases = listOf("play store", "playstore", "google play", "প্লে স্টোর", "প্লে-স্টোর"),
        packages = listOf("com.android.vending"),
        description = "Find, install and update apps.",
        loginRequired = true,
        knowledge = "Search is at the top. An app page shows Install / Update / Open. Installing needs the user's confirmation. Never complete a purchase or subscription (buttons with a price); stop and tell the user. " +
            "Uninstall removes the app and its data, so confirm first.",
        hints = mapOf(
            "install" to listOf("Install", "ইনস্টল"),
            "update" to listOf("Update", "আপডেট"),
            "open" to listOf("Open", "খুলুন"),
            "uninstall" to listOf("Uninstall", "আনইনস্টল")
        ),
        actions = listOf(
            action("search", "Search apps", "Search the Play Store.", listOf(p("query", "App name"))) { rt, a ->
                val q = a["query"].orEmpty()
                if (q.isBlank()) SkillIntents.missing("query")
                else SkillIntents.viewUrl(rt, "market://search?q=${SkillIntents.enc(q)}&c=apps")
            },
            action("open_app_page", "Open an app page", "Open the Play Store page of an app by package name.", listOf(p("package", "Package name, e.g. com.whatsapp"))) { rt, a ->
                val pkg = a["package"].orEmpty().trim()
                if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg)) SkillOutcome(false, "Invalid package name")
                else SkillIntents.viewUrl(rt, "market://details?id=$pkg")
            }
        )
    )

    // ───────────── Settings ─────────────
    private val settingsPages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS,
        "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "nfc" to Settings.ACTION_NFC_SETTINGS,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "main" to Settings.ACTION_SETTINGS
    )

    private val settings = skill(
        id = "settings",
        name = "Settings",
        aliases = listOf("settings", "setting", "সেটিংস", "সেটিং"),
        packages = listOf("com.android.settings"),
        description = "Android system settings.",
        knowledge = "Use the open_page action to jump straight to a settings page (wifi, bluetooth, display, sound, battery, location, date, airplane, nfc, storage, apps, accessibility). " +
            "The search bar at the top finds any setting. Toggles are switches: read the element's checked state before tapping. " +
            "Never change screen lock, passwords, biometrics, account or security settings, and never factory reset.",
        hints = mapOf(
            "wifi" to listOf("Wi-Fi", "Network & internet", "ওয়াই-ফাই"),
            "bluetooth" to listOf("Bluetooth", "ব্লুটুথ"),
            "display" to listOf("Display", "ডিসপ্লে"),
            "battery" to listOf("Battery", "ব্যাটারি"),
            "search" to listOf("Search settings", "সেটিংস খুঁজুন")
        ),
        actions = listOf(
            action("open_page", "Open a settings page", "Open a system settings page. page = ${settingsPages.keys.joinToString("/")}.",
                listOf(p("page", "One of: ${settingsPages.keys.joinToString(", ")}"))) { rt, a ->
                val key = a["page"].orEmpty().trim().lowercase()
                val intentAction = settingsPages[key] ?: Settings.ACTION_SETTINGS
                if (rt.launch(Intent(intentAction))) SkillOutcome(true, "Opened settings: ${if (settingsPages.containsKey(key)) key else "main"}")
                else SkillOutcome(false, "Could not open settings")
            }
        )
    )

    // ───────────── Camera ─────────────
    private val camera = skill(
        id = "camera",
        name = "Camera",
        aliases = listOf("camera", "ক্যামেরা", "photo", "selfie"),
        packages = listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera", "com.android.camera2", "com.mi.android.globalminusscreen"),
        description = "Take photos and videos.",
        knowledge = "The large round button is the shutter. A mode switcher (Photo, Video, Portrait...) is just above it; swipe or tap to change mode. The small round arrows switch front/back camera. " +
            "In Video mode the same button starts and stops recording. Camera permission is needed.",
        hints = mapOf(
            "shutter" to listOf("Shutter", "Take photo", "ছবি তুলুন"),
            "video" to listOf("Video", "ভিডিও"),
            "flip" to listOf("Switch camera", "Flip camera", "ক্যামেরা বদলান")
        ),
        actions = listOf(
            action("photo", "Open camera (photo)", "Open the camera in photo mode.") { rt, _ ->
                if (rt.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) SkillOutcome(true, "Camera opened") else SkillOutcome(false, "No camera app found")
            },
            action("video", "Open camera (video)", "Open the camera in video mode.") { rt, _ ->
                if (rt.launch(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))) SkillOutcome(true, "Video camera opened") else SkillOutcome(false, "No camera app found")
            }
        )
    )

    // ───────────── Phone ─────────────
    private val phone = skill(
        id = "phone",
        name = "Phone",
        aliases = listOf("phone", "dialer", "call", "ফোন", "কল", "ডায়ালার"),
        packages = listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.contacts"),
        description = "Calls and contacts.",
        knowledge = "Tabs: Favorites, Recents, Contacts, Keypad. The green call button places a call. A call costs money and bothers someone, so calling needs the user's confirmation. " +
            "Prefer dial (opens the keypad with the number) when unsure. Emergency numbers are never called automatically.",
        hints = mapOf(
            "call" to listOf("Call", "কল"),
            "keypad" to listOf("Keypad", "কীপ্যাড"),
            "contacts" to listOf("Contacts", "কন্টাক্ট"),
            "recents" to listOf("Recents", "সাম্প্রতিক")
        ),
        actions = listOf(
            action("dial", "Open dialer with a number", "Open the dialer with a number typed in; the user presses call.", listOf(p("number", "Phone number"))) { rt, a ->
                SkillPhone.dial(rt, a["number"].orEmpty())
            },
            action("call", "Call a number", "Place a phone call right away.", listOf(p("number", "Phone number")), risk = ActionRisk.CONFIRM) { rt, a ->
                SkillPhone.call(rt, a["number"].orEmpty())
            },
            action("dial_contact", "Dial a contact", "Find a contact by name and open the dialer with their number.", listOf(p("name", "Contact name"))) { rt, a ->
                val (number, problem) = SkillPhone.resolveContact(rt, a["name"].orEmpty())
                problem ?: SkillPhone.dial(rt, number.orEmpty())
            },
            action("call_contact", "Call a contact", "Find a contact by name and call them right away.", listOf(p("name", "Contact name")), risk = ActionRisk.CONFIRM) { rt, a ->
                val (number, problem) = SkillPhone.resolveContact(rt, a["name"].orEmpty())
                problem ?: SkillPhone.call(rt, number.orEmpty())
            }
        )
    )

    // ───────────── Messages ─────────────
    private val messages = skill(
        id = "messages",
        name = "Messages (SMS)",
        aliases = listOf("messages", "sms", "text", "message", "মেসেজ", "এসএমএস"),
        packages = listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
        description = "SMS and RCS messages.",
        loginRequired = false,
        knowledge = "\"Start chat\" is the button at the bottom right; type or pick the recipient, then the message box, then the send arrow. " +
            "Sending an SMS may cost money and cannot be undone, so it needs the user's confirmation. OTP codes that arrive here must never be read out or copied.",
        hints = mapOf(
            "start_chat" to listOf("Start chat", "New conversation", "নতুন কথোপকথন"),
            "send" to listOf("Send SMS", "Send message", "পাঠান"),
            "message" to listOf("Text message", "বার্তা")
        ),
        actions = listOf(
            action("compose", "Write an SMS draft", "Open a new SMS draft to a number (NOT sent).",
                listOf(p("number", "Phone number"), p("message", "Message text", required = false))) { rt, a ->
                SkillPhone.composeSms(rt, a["number"].orEmpty(), a["message"])
            },
            action("send", "Send an SMS", "Send an SMS right away.",
                listOf(p("number", "Phone number"), p("message", "Message text")), risk = ActionRisk.CONFIRM) { rt, a ->
                SkillPhone.sendSms(rt, a["number"].orEmpty(), a["message"].orEmpty())
            }
        )
    )

    // ───────────── Calendar ─────────────
    private val calendar = skill(
        id = "calendar",
        name = "Calendar",
        aliases = listOf("calendar", "event", "schedule", "ক্যালেন্ডার", "ইভেন্ট"),
        packages = listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar"),
        description = "Events and reminders.",
        knowledge = "The + button creates an event: title, date and time, then Save. Tap an event to see details or edit. Use add_event to open a pre-filled event form; the user (or you) presses Save.",
        hints = mapOf(
            "create" to listOf("Create", "Event", "ইভেন্ট"),
            "save" to listOf("Save", "সংরক্ষণ"),
            "title" to listOf("Add title", "শিরোনাম")
        ),
        actions = listOf(
            action("add_event", "Add an event", "Open a pre-filled new-event form. start format: yyyy-MM-dd HH:mm (24h).",
                listOf(
                    p("title", "Event title"),
                    p("start", "Start time yyyy-MM-dd HH:mm", required = false),
                    p("duration_minutes", "Length in minutes (default 60)", required = false),
                    p("location", "Place", required = false),
                    p("description", "Notes", required = false)
                )) { rt, a ->
                val title = a["title"].orEmpty()
                if (title.isBlank()) {
                    SkillIntents.missing("title")
                } else {
                    val begin = SkillIntents.parseTime(a["start"]) ?: (System.currentTimeMillis() + 3_600_000L)
                    val minutes = a["duration_minutes"]?.toLongOrNull()?.coerceIn(5L, 24L * 60L) ?: 60L
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, title)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000L)
                    a["location"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    a["description"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
                    if (rt.launch(intent)) SkillOutcome(true, "Event form opened") else SkillOutcome(false, "No calendar app found")
                }
            }
        )
    )

    // ───────────── Clock ─────────────
    private val clock = skill(
        id = "clock",
        name = "Clock",
        aliases = listOf("clock", "alarm", "timer", "stopwatch", "ঘড়ি", "অ্যালার্ম", "অ্যালার্ম", "টাইমার"),
        packages = listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"),
        description = "Alarms, timers and stopwatch.",
        knowledge = "Tabs: Alarm, Clock, Timer, Stopwatch. In Alarm, + adds an alarm and each alarm has an on/off switch. Prefer set_alarm and set_timer, which work without touching the screen.",
        hints = mapOf(
            "alarm" to listOf("Alarm", "অ্যালার্ম"),
            "timer" to listOf("Timer", "টাইমার"),
            "add" to listOf("Add alarm", "অ্যালার্ম যোগ করুন")
        ),
        actions = listOf(
            action("set_alarm", "Set an alarm", "Create an alarm at a 24-hour time.",
                listOf(p("hour", "Hour 0-23"), p("minute", "Minute 0-59"), p("label", "Label", required = false))) { rt, a ->
                val h = a["hour"]?.trim()?.toIntOrNull()
                val m = a["minute"]?.trim()?.toIntOrNull()
                if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                    SkillOutcome(false, "Hour must be 0-23 and minute 0-59 · সময় ঠিক নয়")
                } else {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a["label"].orEmpty())
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    if (rt.launch(intent)) SkillOutcome(true, "Alarm set for %02d:%02d".format(h, m)) else SkillOutcome(false, "No clock app found")
                }
            },
            action("set_timer", "Start a timer", "Start a countdown timer.",
                listOf(p("seconds", "Length in seconds"), p("label", "Label", required = false))) { rt, a ->
                val s = a["seconds"]?.trim()?.toIntOrNull()
                if (s == null || s !in 1..86_400) {
                    SkillOutcome(false, "Seconds must be 1-86400 · সময় ঠিক নয়")
                } else {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, s)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a["label"].orEmpty())
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    if (rt.launch(intent)) SkillOutcome(true, "Timer started: $s s") else SkillOutcome(false, "No clock app found")
                }
            },
            action("show_alarms", "Show alarms", "Open the list of alarms.") { rt, _ ->
                if (rt.launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))) SkillOutcome(true, "Alarms opened") else SkillOutcome(false, "No clock app found")
            }
        )
    )

    // ───────────── Notes ─────────────
    private val notes = skill(
        id = "notes",
        name = "Notes",
        aliases = listOf("notes", "note", "keep", "google keep", "নোট", "নোটস"),
        packages = listOf("com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes"),
        description = "Quick notes and lists.",
        knowledge = "In Google Keep, \"Take a note…\" at the bottom starts a new note: title first, then the body; going back saves it automatically. The pin icon keeps a note on top; the checkbox icon makes a list. " +
            "Do not put passwords or OTP codes into notes.",
        hints = mapOf(
            "new_note" to listOf("Take a note", "New note", "নতুন নোট"),
            "title" to listOf("Title", "শিরোনাম"),
            "pin" to listOf("Pin", "পিন")
        ),
        actions = listOf(
            action("new_note", "Create a note", "Create a new note with the given text.",
                listOf(p("text", "Note text"), p("title", "Title", required = false))) { rt, a ->
                val text = a["text"].orEmpty()
                if (text.isBlank()) {
                    SkillIntents.missing("text")
                } else {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    a["title"]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
                    val target = rt.packageName
                    val toSend = if (target != null) intent.setPackage(target) else Intent.createChooser(intent, "Notes")
                    if (rt.launch(toSend)) SkillOutcome(true, "Note screen opened") else SkillOutcome(false, "No notes app found")
                }
            }
        )
    )

    val all: List<AppSkill> = listOf(
        chrome, youtube, facebook, capcut, gallery, whatsapp, instagram, tiktok, gmail,
        maps, playStore, settings, camera, phone, messages, calendar, clock, notes
    )
}
