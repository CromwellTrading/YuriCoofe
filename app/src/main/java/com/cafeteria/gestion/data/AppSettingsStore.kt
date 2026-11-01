package com.cafeteria.gestion.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cafeteriaSettings by preferencesDataStore(name = "cafeteria_app_settings")

data class AppSettings(
    val appearance: String = "LIGHT",
    val palette: String = "COFFEE"
)

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val APPEARANCE = stringPreferencesKey("appearance")
        val PALETTE = stringPreferencesKey("palette")
    }

    val settings: Flow<AppSettings> = context.cafeteriaSettings.data.map { prefs ->
        AppSettings(
            appearance = prefs[Keys.APPEARANCE]?.takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "LIGHT",
            palette = prefs[Keys.PALETTE]?.takeIf { it in setOf("COFFEE", "PURPLE", "GREEN", "BLUE", "ORANGE", "ROSE") } ?: "COFFEE"
        )
    }

    suspend fun setAppearance(value: String) {
        require(value in setOf("SYSTEM", "LIGHT", "DARK"))
        context.cafeteriaSettings.edit { it[Keys.APPEARANCE] = value }
    }

    suspend fun setPalette(value: String) {
        require(value in setOf("COFFEE", "PURPLE", "GREEN", "BLUE", "ORANGE", "ROSE"))
        context.cafeteriaSettings.edit { it[Keys.PALETTE] = value }
    }
}
