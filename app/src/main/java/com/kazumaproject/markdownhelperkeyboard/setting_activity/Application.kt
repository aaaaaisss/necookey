package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Application
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.seed.NecookeyDefaultLayoutSeeder
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@HiltAndroidApp
class Application : Application() {

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    override fun onCreate() {
        super.onCreate()
        // Opening preferences (including directory lookup) happens on AppPreferenceInit.
        AppPreference.startInitialization(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // The `:zenz` process must not touch Room; seed only in the main process. This blocks only
        // on the very first launch (one Room insert) so the IME never starts before the default
        // custom layout exists; afterwards it is a single SharedPreferences read.
        if (isMainProcess() && NecookeyDefaultLayoutSeeder.needsStartupCheck(this)) {
            runBlocking(Dispatchers.IO) {
                NecookeyDefaultLayoutSeeder.seedIfNeeded(this@Application, keyboardRepository)
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val name = runCatching {
            File("/proc/self/cmdline").readText().trimEnd('\u0000').substringBefore('\u0000')
        }.getOrNull()
        return name.isNullOrEmpty() || name == packageName
    }
}
