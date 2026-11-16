package ru.diploma.clinicstaff

import android.app.Application
import ru.diploma.clinicstaff.data.*
import ru.diploma.clinicstaff.data.local.*
import ru.diploma.clinicstaff.data.remote.*

class ClinicApp : Application() {
    lateinit var database: ClinicDatabase
    lateinit var settings: SettingsStore
    lateinit var tokenHolder: TokenHolder
    lateinit var api: ApiService
    lateinit var repository: ClinicRepository
    lateinit var seeder: DemoSeeder
    override fun onCreate() {
        super.onCreate()
        DebugLogStore.init(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { DebugLogStore.logException("UNCAUGHT:${thread.name}", throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
        database = ClinicDatabase.get(this)
        settings = SettingsStore(this)
        tokenHolder = TokenHolder()
        api = Network.api(this, BuildConfig.API_BASE_URL, tokenHolder)
        repository = ClinicRepository(database.dao(), api, settings)
        seeder = DemoSeeder(this, database.dao())
    }
}
