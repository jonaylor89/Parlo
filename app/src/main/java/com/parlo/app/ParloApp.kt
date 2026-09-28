package com.parlo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.parlo.app.data.LiveModelDiscovery
import com.parlo.app.data.ModelRepository
import com.parlo.app.data.SessionRepository
import com.parlo.app.data.SettingsRepository
import com.parlo.app.data.VocabCapture
import com.parlo.app.data.VocabRepository
import com.parlo.app.data.db.ParloDatabase
import com.parlo.app.gemini.VocabMiner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual DI: one graph, lazily built, shared by Activity, ViewModel and Service. */
class AppContainer(context: Context) {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    val db: ParloDatabase = ParloDatabase.build(context)
    val settings = SettingsRepository(context)
    val sessions = SessionRepository(db.sessionDao())
    val vocab = VocabRepository(db.vocabDao())
    val models = ModelRepository(settings, LiveModelDiscovery(okHttp))
    /** Outlives the session service so post-walk work (vocab mining) can finish after it stops. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vocabCapture = VocabCapture(
        sessions, vocab, settings,
        VocabMiner(okHttp.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()),
        appScope,
    )
}

class ParloApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_session_desc)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        const val CHANNEL_SESSION = "walk_session"
        fun container(context: Context): AppContainer = (context.applicationContext as ParloApp).container
    }
}
