package com.loweffort.quakesense

import android.content.ContentValues.TAG
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loweffort.quakesense.room.AccelDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class MyFirebaseMessaginService : FirebaseMessagingService() {
    private lateinit var database: AccelDatabase
    private var firebaseRef: FirebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var firebaseAccelDataRef: DatabaseReference
    private lateinit var deviceId: String

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }

    override fun onCreate() {
        val mainActivityCntxt: Context? = SingletonContextProvider.getContext()
        if (mainActivityCntxt == null) {
            Log.e(TAG, "Context is null")
            // Handle error if context is null
            return
        }

        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        firebaseAccelDataRef = firebaseRef.getReference(deviceId)
        database = AccelDatabase.getDatabase(mainActivityCntxt)
        super.onCreate()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        val seismTime = remoteMessage.data.get("seismTime")
        Log.d(TAG, "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: $seismTime")

            // Ejecutar el bloque de código dentro de una corrutina
            serviceScope.launch {
                val timestampFromServer = seismTime?.toLong()
//                val timestampFromServer = 158.toLong()
                val firstData = timestampFromServer?.let {
                    database.accelReadingDao().getInitialData(it)
                }
                firebaseAccelDataRef.child("localData").setValue(firstData)
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        EventBus.getDefault().post(seismTime?.let { NotificationReceivedEvent(it) })
    }

}