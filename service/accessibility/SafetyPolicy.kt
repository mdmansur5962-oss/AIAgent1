package com.aiagent.assistant.service.accessibility

/**
 * নিরাপত্তার নিয়ম, যা কোডে জোর করে প্রয়োগ হয় (AI-এর ইচ্ছার ওপর নির্ভর করে না):
 *  • ব্যাংকিং/payment/authenticator/password-manager অ্যাপে কিছুই পড়া বা করা হয় না
 *  • password ঘর, OTP/কোড ঘর, কার্ড নম্বর দেখা গেলে কাজ থামে
 *  • OTP, কার্ড নম্বর AI-কে পাঠানো snapshot-এ ঢেকে (••••) যায়
 *  • ঝুঁকির কাজ (payment, পাঠানো, মোছা...) ব্যবহারকারীর confirmation ছাড়া হয় না
 */
object SafetyPolicy {

    private val blockedPackageKeywords = listOf(
        "bank", "bkash", "nagad", "rocket", "upay", "dbbl", "wallet", "paypal", "paytm", "phonepe",
        "paisa", "venmo", "revolut", "coinbase", "binance", "payment", "mobilebanking",
        "authenticator", "authy", "freeotp", "otp", "bitwarden", "lastpass", "1password", "onepassword",
        "dashlane", "keepass", "keepersecurity"
    )

    private val otpKeywords = Regex(
        "\\b(otp|one[- ]?time (password|passcode|code)|verification code|security code|auth(entication)? code|" +
            "passcode|cvv|cvc|(enter|your|confirm|new|wrong|incorrect)\\s+pin|pin (code|number)|upi pin)\\b",
        RegexOption.IGNORE_CASE
    )
    private val otpKeywordsBangla = listOf("ওটিপি", "ভেরিফিকেশন কোড", "যাচাইকরণ কোড", "নিরাপত্তা কোড")

    private val shortDigits = Regex("\\b\\d{4,8}\\b")
    private val digitsOnly = Regex("^[\\d\\s-]{4,10}$")
    private val cardLike = Regex("\\b(?:\\d[ -]?){13,19}\\b")

    private val riskyWords = Regex(
        "\\b(pay|payment|send money|transfer|purchase|buy now|buy|place order|checkout|confirm order|" +
            "delete|remove|uninstall|erase|factory reset|reset|format|sign out|log out|logout|send|call)\\b",
        RegexOption.IGNORE_CASE
    )
    private val riskyWordsBangla = listOf("পেমেন্ট", "পাঠান", "মুছুন", "ডিলিট", "কিনুন", "ট্রান্সফার", "কল করুন")

    /** সুরক্ষিত অ্যাপ হলে কারণ, নইলে null */
    fun blockedPackage(packageName: String): ScreenSafety? {
        val name = packageName.lowercase()
        val hit = blockedPackageKeywords.firstOrNull { name.contains(it) } ?: return null
        return ScreenSafety(
            SafetyLevel.BLOCKED,
            "Protected app ($hit) · সুরক্ষিত অ্যাপ: ব্যাংকিং/payment/security অ্যাপে assistant কাজ করে না"
        )
    }

    /** ব্যাংকিং/payment ওয়েবসাইটের ঠিকানা হলে কারণ, নইলে null (assistant এসব সাইট খুলবে না) */
    fun blockedHost(host: String): ScreenSafety? {
        val name = host.lowercase()
        val hit = blockedPackageKeywords.firstOrNull { name.contains(it) } ?: return null
        return ScreenSafety(
            SafetyLevel.BLOCKED,
            "Protected website ($hit) · সুরক্ষিত ওয়েবসাইট: ব্যাংকিং/payment সাইট assistant খোলে না"
        )
    }

    private fun hasOtpKeyword(text: String): Boolean =
        text.isNotBlank() && (otpKeywords.containsMatchIn(text) || otpKeywordsBangla.any { text.contains(it) })

    private fun redactOtp(text: String, otpContext: Boolean): String {
        if (text.isBlank()) return text
        return when {
            hasOtpKeyword(text) -> shortDigits.replace(text, "••••")
            otpContext && digitsOnly.matches(text.trim()) -> "••••"
            else -> text
        }
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    /** কার্ড নম্বর (Luhn মিললে) ঢেকে দেয়; ঢাকা হলে true */
    private fun redactCards(text: String): Pair<String, Boolean> {
        if (text.length < 13) return text to false
        var found = false
        val result = cardLike.replace(text) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && luhnValid(digits)) {
                found = true
                "[card number]"
            } else {
                match.value
            }
        }
        return result to found
    }

    /** OCR বা অন্য লেখায় OTP/কার্ড ঢাকার জন্য */
    fun redactText(text: String, otpContext: Boolean): String {
        val (noCards, _) = redactCards(text)
        return redactOtp(noCards, otpContext)
    }

    /**
     * কাঁচা উপাদান থেকে নিরাপদ তালিকা ও স্ক্রিনের নিরাপত্তা-স্তর বের করে।
     * সুরক্ষিত অ্যাপে কোনো উপাদানই ফেরত দেয় না।
     */
    fun process(
        packageName: String,
        ownPackage: String,
        raw: List<UiElement>
    ): Pair<List<UiElement>, ScreenSafety> {
        blockedPackage(packageName)?.let { return emptyList<UiElement>() to it }

        val otpContext = raw.any { hasOtpKeyword(it.text) || hasOtpKeyword(it.description) }
        val hasPassword = raw.any { it.password }
        val hasEditable = raw.any { it.editable }
        var cardFound = false

        val cleaned = raw.map { element ->
            var text = element.text
            var description = element.description
            if (element.password) text = "••••"
            text = redactOtp(text, otpContext)
            description = redactOtp(description, otpContext)
            val (t, c1) = redactCards(text)
            val (d, c2) = redactCards(description)
            if (c1 || c2) cardFound = true
            element.copy(text = t, description = d)
        }

        val safety = when {
            packageName == ownPackage ->
                ScreenSafety(SafetyLevel.SENSITIVE, "MYRA's own screen · নিজের অ্যাপ: শুধু পড়া যাবে")
            hasPassword ->
                ScreenSafety(SafetyLevel.SENSITIVE, "Password field · password-এর ঘর আছে")
            otpContext && hasEditable ->
                ScreenSafety(SafetyLevel.SENSITIVE, "OTP / code entry · OTP বা কোডের ঘর আছে")
            cardFound ->
                ScreenSafety(SafetyLevel.SENSITIVE, "Card number visible · কার্ড নম্বর দেখা যাচ্ছে")
            else -> ScreenSafety(SafetyLevel.SAFE)
        }
        return cleaned to safety
    }

    /** ক্লিকের লক্ষ্য ঝুঁকিপূর্ণ হলে কারণ, নইলে null */
    fun clickRisk(element: UiElement): String? {
        val label = (element.text + " " + element.description + " " + element.viewId.replace('_', ' ')).trim()
        if (label.isBlank()) return null
        if (riskyWords.containsMatchIn(label) || riskyWordsBangla.any { label.contains(it) }) {
            return "\"${element.label.take(40)}\" looks like a sensitive action (pay/send/delete/call) · " +
                "এটা স্পর্শকাতর কাজ মনে হচ্ছে"
        }
        return null
    }

    /** সুরক্ষিত স্তরে কোন কাজ চলতে পারে? অনুমতি না থাকলে কারণ ফেরত দেয় */
    fun denyReason(action: UiAction, safety: ScreenSafety?): String? {
        val level = safety?.level ?: SafetyLevel.SAFE
        val harmless = action is UiAction.Back ||
            action is UiAction.Home ||
            action is UiAction.Recents ||
            action is UiAction.Wait ||
            action is UiAction.OpenNotifications ||
            action is UiAction.OpenApp
        return when (level) {
            SafetyLevel.SAFE -> null
            SafetyLevel.SENSITIVE ->
                if (harmless || action is UiAction.Scroll) null else "Safety stop · ${safety?.reason}"
            SafetyLevel.BLOCKED ->
                if (harmless) null else "Safety stop · ${safety?.reason}"
        }
    }
}
