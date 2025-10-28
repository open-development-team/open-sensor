package com.opendevelopment.opensensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runBlocking {
                val settingsDataStore = SettingsDataStore(context)
                val settings = settingsDataStore.settingsFlow.first()

                if (settings.autoStart) {
                    val serviceManager = ServiceManager(context)
                    serviceManager.startAllServices()
                }
            }
        }
    }
}