package com.example.cutecamera

import android.annotation.SuppressLint
import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class MyCameraApp : Application(), CameraXConfig.Provider {
    @SuppressLint("RestrictedApi")
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}