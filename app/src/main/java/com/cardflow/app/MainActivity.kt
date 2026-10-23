package com.cardflow.app

import android.Manifest
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray

class MainActivity:Activity(){
    private lateinit var web:WebView
    private var firstLaunch=true
    private val PDF_PICKER=312
    private fun biometricGate(){
        if(Build.VERSION.SDK_INT<28)return
        val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if(!km.isDeviceSecure)return
        val prompt=BiometricPrompt.Builder(this).setTitle("Unlock CardFlow")
            .setSubtitle("Verify your identity to access card data")
            .setNegativeButton("Cancel",mainExecutor){_,_->finish()}
            .build()
        prompt.authenticate(CancellationSignal(),mainExecutor,object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){super.onAuthenticationSucceeded(result);web.visibility=android.view.View.VISIBLE}
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence){super.onAuthenticationError(errorCode,errString);finish()}
        })
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        web=WebView(this)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.setSupportMultipleWindows(false)
        web.webChromeClient=WebChromeClient()
        web.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){web.evaluateJavascript("window.refreshSmsQueue && window.refreshSmsQueue()",null)}}
        web.addJavascriptInterface(Bridge(),"CardFlowNative")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        if(getSharedPreferences("cardflow_security",MODE_PRIVATE).getBoolean("biometric",false)){
            web.visibility=android.view.View.INVISIBLE
            biometricGate()
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),43)
        if(checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS),42)
        }
    }
    inner class Bridge{
        @JavascriptInterface fun setBiometricEnabled(enabled:Boolean):Boolean{
            if(Build.VERSION.SDK_INT<28)return false
            val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if(!km.isDeviceSecure)return false
            getSharedPreferences("cardflow_security",MODE_PRIVATE).edit().putBoolean("biometric",enabled).apply()
            return true
        }
        @JavascriptInterface fun pickPdf(){
            runOnUiThread {
                val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                    addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf"
                }
                startActivityForResult(intent,PDF_PICKER)
            }
        }
        @JavascriptInterface fun notifyDue(message:String){
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
            val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("due","Card payment reminders",NotificationManager.IMPORTANCE_DEFAULT))
            val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this@MainActivity,"due") else Notification.Builder(this@MainActivity)
            manager.notify(700,builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("CardFlow payment reminder").setContentText(message.take(120)).setAutoCancel(true).build())
        }
        @JavascriptInterface fun pending():String=SmsParser.pending(this@MainActivity)
        @JavascriptInterface fun dismiss(id:String){SmsParser.remove(this@MainActivity,id)}
        @JavascriptInterface fun importInbox():Int{
            if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return -1
            var count=0
            try{
                contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE),null,null,"date DESC LIMIT 500")?.use{cursor->
                    while(cursor.moveToNext()){
                        val sender=cursor.getString(0)?:"";val body=cursor.getString(1)?:"";val date=cursor.getLong(2)
                        SmsParser.parse(sender,body,date)?.let{SmsParser.enqueue(this@MainActivity,it);count++}
                    }
                }
            }catch(_:Exception){return -2}
            return count
        }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,resultData:Intent?){
        super.onActivityResult(requestCode,resultCode,resultData)
        if(requestCode==PDF_PICKER && resultCode==RESULT_OK){
            val name=resultData?.data?.lastPathSegment?:"statement.pdf"
            web.evaluateJavascript("window.pdfSelected && window.pdfSelected("+org.json.JSONObject.quote(name)+")",null)
        }
    }
    override fun onResume(){super.onResume();if(::web.isInitialized)web.evaluateJavascript("window.refreshSmsQueue && window.refreshSmsQueue()",null)}
    override fun onBackPressed(){if(web.canGoBack())web.goBack() else super.onBackPressed()}
}
