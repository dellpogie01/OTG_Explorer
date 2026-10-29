package com.juanmediahub.usbotgorganizer.di

import android.content.Context
import com.juanmediahub.usbotgorganizer.data.preferences.AppPreferences
import com.juanmediahub.usbotgorganizer.data.preferences.LocationRepository
import com.juanmediahub.usbotgorganizer.data.storage.SafStorageAccess
import com.juanmediahub.usbotgorganizer.data.storage.UriPermissionManager
import com.juanmediahub.usbotgorganizer.domain.operations.FileOperationEngine

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val preferences = AppPreferences(appContext)
    val permissions = UriPermissionManager(appContext.contentResolver)
    val storage = SafStorageAccess(appContext, permissions)
    val locations = LocationRepository(preferences, permissions, storage)
    val operations = FileOperationEngine(storage)
}
