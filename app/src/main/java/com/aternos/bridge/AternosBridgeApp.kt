package com.aternos.bridge

import android.app.Application
import android.util.Log
import com.aternos.bridge.data.EncryptedConfigRepository

class AternosBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("AternosBridgeApp", "Initializing Aternos AI Console Bridge...")
        EncryptedConfigRepository.getInstance(this)
    }
}
