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
    val apiConfigured: Boolean get() = Network.isBaseUrlConfigured(BuildConfig.API_BASE_URL)
    lateinit var seeder: DemoSeeder
    override fun onCreate() {
        super.onCreate()
        DebugLogStore.init(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { DebugLogStore.logException("UNCAUGHT:${thread.name}", throwable) }
            android.util.Log.e("ClinicCrash", "Необработанное исключение в ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        DebugLogStore.log("INFO", "STARTUP", "Инициализация локальной базы данных")
        database = ClinicDatabase.get(this)
        DebugLogStore.log("INFO", "STARTUP", "Локальная база данных создана")
        settings = SettingsStore(this)
        tokenHolder = TokenHolder()
        api = Network.api(this, BuildConfig.API_BASE_URL, tokenHolder)
        DebugLogStore.log("INFO", "STARTUP", "Сетевой клиент создан; HTTP защищён проверкой конфигурации")
        DebugLogStore.log(
            if (apiConfigured) "INFO" else "WARN",
            "CONFIG",
            if (apiConfigured) "API_BASE_URL настроен" else "API_BASE_URL не задан: приложение работает автономно в DEMO; сетевые запросы заблокированы"
        )
        repository = ClinicRepository(database.dao(), api, settings)
        seeder = DemoSeeder(this, database.dao())
    }
}
