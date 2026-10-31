package com.aiagent.assistant.service.skills

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** কল, SMS ও কন্টাক্টের কাজ। সবচেয়ে স্পর্শকাতর অংশ, তাই এখানে বাড়তি সাবধানতা। */
internal object SkillPhone {

    private val emergencyNumbers = setOf("911", "112", "999", "100", "101", "102", "108", "995")

    fun cleanNumber(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 3..15) return null
        return (if (trimmed.startsWith("+")) "+" else "") + digits
    }

    private fun granted(rt: SkillRuntime, permission: String): Boolean =
        ContextCompat.checkSelfPermission(rt.context, permission) == PackageManager.PERMISSION_GRANTED

    /** dialer খোলে, নম্বর বসানো থাকে; কল করতে ব্যবহারকারীকে নিজে চাপতে হয় */
    fun dial(rt: SkillRuntime, number: String): SkillOutcome {
        val n = cleanNumber(number) ?: return SkillOutcome(false, "Invalid phone number · নম্বর ঠিক নয়")
        val ok = rt.launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null)))
        return if (ok) SkillOutcome(true, "Dialer opened with $n") else SkillOutcome(false, "Could not open the dialer")
    }

    /** সরাসরি কল করে। জরুরি নম্বরে কখনো সরাসরি কল নয়, আর অনুমতি না থাকলে dialer খোলে। */
    fun call(rt: SkillRuntime, number: String): SkillOutcome {
        val n = cleanNumber(number) ?: return SkillOutcome(false, "Invalid phone number · নম্বর ঠিক নয়")
        val plain = n.removePrefix("+")
        if (plain in emergencyNumbers) {
            return dial(rt, n).copy(message = "Emergency number: dialer opened, press call yourself · জরুরি নম্বর: নিজে কল চাপুন")
        }
        if (!granted(rt, Manifest.permission.CALL_PHONE)) {
            return dial(rt, n).copy(message = "Call permission is off, dialer opened instead · কলের অনুমতি নেই, dialer খোলা হয়েছে")
        }
        val ok = rt.launch(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)))
        return if (ok) SkillOutcome(true, "Calling $n") else SkillOutcome(false, "Could not place the call")
    }

    data class ContactMatch(val name: String, val number: String)

    fun findContacts(rt: SkillRuntime, name: String): List<ContactMatch>? {
        if (!granted(rt, Manifest.permission.READ_CONTACTS)) return null
        val matches = mutableListOf<ContactMatch>()
        val cursor = rt.context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${name.trim()}%"),
            null
        ) ?: return matches
        cursor.use {
            while (it.moveToNext() && matches.size < 10) {
                matches.add(ContactMatch(it.getString(0).orEmpty(), it.getString(1).orEmpty()))
            }
        }
        return matches
    }

    /** নাম দিয়ে কন্টাক্ট খুঁজে নম্বর বের করে; একাধিক ভিন্ন নম্বর মিললে জিজ্ঞেস করতে বলে */
    fun resolveContact(rt: SkillRuntime, name: String): Pair<String?, SkillOutcome?> {
        val matches = findContacts(rt, name)
            ?: return null to SkillOutcome(false, "Contacts permission is needed · কন্টাক্টের অনুমতি লাগবে")
        if (matches.isEmpty()) return null to SkillOutcome(false, "No contact named \"$name\" · এই নামে কন্টাক্ট নেই")
        val numbers = matches.map { cleanNumber(it.number) }.filterNotNull().distinct()
        if (numbers.size == 1) return numbers.first() to null
        val list = matches.take(5).joinToString("; ") { "${it.name} (…${it.number.filter { c -> c.isDigit() }.takeLast(3)})" }
        return null to SkillOutcome(false, "Several contacts match: $list. Ask which one · কোনটা, জিজ্ঞেস করুন")
    }

    fun composeSms(rt: SkillRuntime, number: String, message: String?): SkillOutcome {
        val n = cleanNumber(number) ?: return SkillOutcome(false, "Invalid phone number · নম্বর ঠিক নয়")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(n)}"))
        if (!message.isNullOrBlank()) intent.putExtra("sms_body", message.take(1000))
        return if (rt.launch(intent)) SkillOutcome(true, "Message draft opened, press send yourself") else SkillOutcome(false, "Could not open messages")
    }

    /** সরাসরি SMS পাঠায় (confirmation ছাড়া কখনো চলে না) */
    @Suppress("DEPRECATION")
    fun sendSms(rt: SkillRuntime, number: String, message: String): SkillOutcome {
        val n = cleanNumber(number) ?: return SkillOutcome(false, "Invalid phone number · নম্বর ঠিক নয়")
        if (message.isBlank() || message.length > 600) {
            return SkillOutcome(false, "Message must be 1–600 characters · বার্তা ১–৬০০ অক্ষরের হতে হবে")
        }
        if (!granted(rt, Manifest.permission.SEND_SMS)) {
            return composeSms(rt, n, message).copy(message = "SMS permission is off, draft opened instead · SMS-এর অনুমতি নেই, খসড়া খোলা হয়েছে")
        }
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                rt.context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(n, null, parts, null, null)
            SkillOutcome(true, "SMS sent to $n")
        } catch (e: Exception) {
            SkillOutcome(false, "Could not send the SMS · SMS পাঠানো যায়নি")
        }
    }
}
