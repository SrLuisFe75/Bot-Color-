package com.icon.nexus.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun interface MicrophonePermission {
    fun isGranted(): Boolean
}

class AndroidMicrophonePermission(
    context: Context,
) : MicrophonePermission {
    private val appContext = context.applicationContext

    override fun isGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
