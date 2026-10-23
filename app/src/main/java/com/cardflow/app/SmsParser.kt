package com.cardflow.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object SmsParser {
    private val cardPattern = Regex("(?i)(?:card|cc|credit card)[^\\n]{0,45}?(?:xx+|x+|\\*+)?\\s*([0-9]{4})(?![0-9])")
    private val amountPattern = Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val paymentWords = Regex("(?i)\\b(payment received|payment of.{0,35}received|payment credited|thank you for (?:your )?payment|payment towards|payment made towards)\\b")
    private val chargeWords = Regex("(?i)\\b(spent|purchase|purchased|transaction of|charged|debited|used for)\\b")
    private val ignoreWords = Regex("(?i)\\b(otp|one.time password|verification code|minimum amount|statement generated|bill due|available limit|payment due|autopay scheduled|failed|declined|reversed|unsuccessful)\\b")
    fun parse(sender: String, body: String, timestamp: Long): JSONObject? {
        if (ignoreWords.containsMatchIn(body)) return null
        val card = cardPattern.find(body)?.groupValues?.get(1) ?: return null
        val amt = amountPattern.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amt <= 0 || amt > 100000000) return null
        val type = when {
            paymentWords.containsMatchIn(body) -> "payment"
            chargeWords.containsMatchIn(body) && Regex("(?i)credit card|card").containsMatchIn(body) -> "charge"
            else -> return null
        }
        val raw = "$sender|$body|$timestamp"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return JSONObject().put("id",hash).put("sender",sender.take(50)).put("last4",card).put("amount",amt).put("type",type).put("timestamp",timestamp).put("preview",body.take(180))
    }
    fun enqueue(context: Context, item: JSONObject) {
        val prefs=context.getSharedPreferences("sms_queue",Context.MODE_PRIVATE)
        synchronized(this) {
            val arr=JSONArray(prefs.getString("items","[]"))
            for(i in 0 until arr.length()) if(arr.getJSONObject(i).optString("id")==item.optString("id"))return
            arr.put(item)
            // Bound the queue to 300 pending items.
            val out=JSONArray()
            for(i in maxOf(0,arr.length()-300) until arr.length())out.put(arr.get(i))
            prefs.edit().putString("items",out.toString()).apply()
        }
    }
    fun pending(context:Context):String=context.getSharedPreferences("sms_queue",Context.MODE_PRIVATE).getString("items","[]")?:"[]"
    fun remove(context:Context,id:String) {
        val prefs=context.getSharedPreferences("sms_queue",Context.MODE_PRIVATE)
        synchronized(this){val arr=JSONArray(prefs.getString("items","[]"));val out=JSONArray();for(i in 0 until arr.length()){val o=arr.getJSONObject(i);if(o.optString("id")!=id)out.put(o)};prefs.edit().putString("items",out.toString()).apply()}
    }
}
