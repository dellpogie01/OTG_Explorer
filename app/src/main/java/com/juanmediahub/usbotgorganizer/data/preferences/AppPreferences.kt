package com.juanmediahub.usbotgorganizer.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocation
import com.juanmediahub.usbotgorganizer.domain.model.SortDirection
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.ThemeMode
import com.juanmediahub.usbotgorganizer.domain.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "otg_explorer_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortSpec: SortSpec = SortSpec(),
    val showHidden: Boolean = false
)

class AppPreferences(private val context: Context) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[THEME] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[DYNAMIC] ?: true,
            viewMode = runCatching { ViewMode.valueOf(prefs[VIEW] ?: ViewMode.LIST.name) }
                .getOrDefault(ViewMode.LIST),
            sortSpec = SortSpec(
                field = runCatching { SortField.valueOf(prefs[SORT_FIELD] ?: SortField.NAME.name) }
                    .getOrDefault(SortField.NAME),
                direction = runCatching {
                    SortDirection.valueOf(prefs[SORT_DIR] ?: SortDirection.ASC.name)
                }.getOrDefault(SortDirection.ASC),
                foldersFirst = prefs[FOLDERS_FIRST] ?: true
            ),
            showHidden = prefs[HIDDEN] ?: false
        )
    }

    val locations: Flow<List<SavedLocation>> = context.dataStore.data.map { prefs ->
        decodeLocations(prefs[LOCATIONS] ?: "[]")
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC] = enabled }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[VIEW] = mode.name }
    }

    suspend fun setSort(spec: SortSpec) {
        context.dataStore.edit {
            it[SORT_FIELD] = spec.field.name
            it[SORT_DIR] = spec.direction.name
            it[FOLDERS_FIRST] = spec.foldersFirst
        }
    }

    suspend fun setShowHidden(show: Boolean) {
        context.dataStore.edit { it[HIDDEN] = show }
    }

    suspend fun saveLocations(list: List<SavedLocation>) {
        context.dataStore.edit { it[LOCATIONS] = encodeLocations(list) }
    }

    private fun encodeLocations(list: List<SavedLocation>): String {
        val array = JSONArray()
        list.forEach { loc ->
            array.put(
                JSONObject().apply {
                    put("id", loc.id)
                    put("treeUri", loc.treeUri)
                    put("displayName", loc.displayName)
                    put("rootDocumentId", loc.rootDocumentId)
                    put("addedAtEpochMs", loc.addedAtEpochMs)
                    put("lastOpenedEpochMs", loc.lastOpenedEpochMs ?: JSONObject.NULL)
                    put("hasWritePermission", loc.hasWritePermission)
                }
            )
        }
        return array.toString()
    }

    private fun decodeLocations(raw: String): List<SavedLocation> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        SavedLocation(
                            id = obj.getString("id"),
                            treeUri = obj.getString("treeUri"),
                            displayName = obj.getString("displayName"),
                            rootDocumentId = obj.getString("rootDocumentId"),
                            addedAtEpochMs = obj.getLong("addedAtEpochMs"),
                            lastOpenedEpochMs = if (obj.isNull("lastOpenedEpochMs")) null else obj.getLong("lastOpenedEpochMs"),
                            hasWritePermission = obj.optBoolean("hasWritePermission", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC = booleanPreferencesKey("dynamic_color")
        private val VIEW = stringPreferencesKey("view_mode")
        private val SORT_FIELD = stringPreferencesKey("sort_field")
        private val SORT_DIR = stringPreferencesKey("sort_dir")
        private val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
        private val HIDDEN = booleanPreferencesKey("show_hidden")
        private val LOCATIONS = stringPreferencesKey("locations")
    }
}
