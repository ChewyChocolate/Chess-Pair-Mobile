package com.example.chesspairmobile.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.chesspairmobile.data.models.AccelerationMode
import com.example.chesspairmobile.data.models.TiebreakType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chesspair_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultWinPoints: Double = 1.0,
    val defaultDrawPoints: Double = 0.5,
    val defaultLossPoints: Double = 0.0,
    val defaultAvoidClubs: Boolean = false,
    val defaultAcceleration: AccelerationMode = AccelerationMode.NONE,
    val defaultTiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.PROGRESSIVE
    ),
    val enableForfeitScoring: Boolean = false
)

class AppPreferences(private val context: Context) {

    private val gson = Gson()
    private val tiebreakTypeToken = object : TypeToken<List<String>>() {}.type

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_WIN_PTS = doublePreferencesKey("default_win_points")
        private val KEY_DRAW_PTS = doublePreferencesKey("default_draw_points")
        private val KEY_LOSS_PTS = doublePreferencesKey("default_loss_points")
        private val KEY_AVOID_CLUBS = booleanPreferencesKey("default_avoid_clubs")
        private val KEY_ACCELERATION = stringPreferencesKey("default_acceleration")
        private val KEY_TIEBREAKS = stringPreferencesKey("default_tiebreak_order")
        private val KEY_FORFEIT = booleanPreferencesKey("enable_forfeit_scoring")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = try { ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: "SYSTEM") } catch (_: Exception) { ThemeMode.SYSTEM },
            defaultWinPoints = prefs[KEY_WIN_PTS] ?: 1.0,
            defaultDrawPoints = prefs[KEY_DRAW_PTS] ?: 0.5,
            defaultLossPoints = prefs[KEY_LOSS_PTS] ?: 0.0,
            defaultAvoidClubs = prefs[KEY_AVOID_CLUBS] ?: false,
            defaultAcceleration = try { AccelerationMode.valueOf(prefs[KEY_ACCELERATION] ?: "NONE") } catch (_: Exception) { AccelerationMode.NONE },
            defaultTiebreakOrder = parseTiebreaks(prefs[KEY_TIEBREAKS] ?: ""),
            enableForfeitScoring = prefs[KEY_FORFEIT] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDefaultScoring(win: Double, draw: Double, loss: Double) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIN_PTS] = win.coerceAtLeast(0.0)
            prefs[KEY_DRAW_PTS] = draw.coerceAtLeast(0.0)
            prefs[KEY_LOSS_PTS] = loss.coerceAtLeast(0.0)
        }
    }

    suspend fun setDefaultAvoidClubs(avoid: Boolean) {
        context.dataStore.edit { it[KEY_AVOID_CLUBS] = avoid }
    }

    suspend fun setDefaultAcceleration(mode: AccelerationMode) {
        context.dataStore.edit { it[KEY_ACCELERATION] = mode.name }
    }

    suspend fun setDefaultTiebreakOrder(order: List<TiebreakType>) {
        val json = gson.toJson(order.map { it.name })
        context.dataStore.edit { it[KEY_TIEBREAKS] = json }
    }

    suspend fun setEnableForfeitScoring(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FORFEIT] = enabled }
    }

    private fun parseTiebreaks(json: String): List<TiebreakType> {
        if (json.isBlank()) return AppSettings().defaultTiebreakOrder
        return try {
            val names: List<String> = gson.fromJson(json, tiebreakTypeToken) ?: emptyList()
            if (names.isEmpty()) return emptyList()
            names.mapNotNull { try { TiebreakType.valueOf(it) } catch (_: Exception) { null } }
                .ifEmpty { AppSettings().defaultTiebreakOrder }
        } catch (_: Exception) { AppSettings().defaultTiebreakOrder }
    }
}
