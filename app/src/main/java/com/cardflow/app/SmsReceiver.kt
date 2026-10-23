package com.cardflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
        val messages=Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if(messages.isEmpty())return
        val groups=messages.groupBy { "${it.originatingAddress}|${it.timestampMillis}" }
        groups.values.forEach { group ->
            val sender=group.first().originatingAddress?:""
            val body=group.joinToString(""){it.messageBody?:""}
            SmsParser.parse(sender,body,group.first().timestampMillis)?.let { SmsParser.enqueue(context,it) }
        }
    }
}
