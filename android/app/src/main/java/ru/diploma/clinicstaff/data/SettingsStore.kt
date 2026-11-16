package ru.diploma.clinicstaff.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("clinic_settings")
enum class AppMode { DEMO, SERVER }
class SettingsStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("mode")
    private val tokenKey = stringPreferencesKey("token")
    val mode = context.settings.data.map { runCatching { AppMode.valueOf(it[modeKey] ?: "DEMO") }.getOrDefault(AppMode.DEMO) }
    val token = context.settings.data.map { it[tokenKey] }
    suspend fun setMode(mode: AppMode) { context.settings.edit { it[modeKey] = mode.name } }
    suspend fun setToken(token: String?) { context.settings.edit { if (token == null) it -= tokenKey else it[tokenKey] = token } }
}
