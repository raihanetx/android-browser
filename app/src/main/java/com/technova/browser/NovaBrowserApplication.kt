package com.technova.browser

import android.app.Application
import androidx.work.Configuration
import com.technova.browser.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NovaBrowserApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin dependency injection
        startKoin {
            androidLogger()
            androidContext(this@NovaBrowserApplication)
            modules(AppModule.appModule)
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
}
