package com.appremote.remotecontrol.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object ScreenCaptureHolder {
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    @Volatile
    private var projection: MediaProjection? = null

    fun setPermissionResult(resultCode: Int, data: Intent) {
        release()
        this.resultCode = resultCode
        this.resultData = data
    }

    fun hasPermission(): Boolean =
        resultCode == Activity.RESULT_OK && resultData != null

    fun createProjection(context: Context): MediaProjection? {
        val data = resultData ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.getMediaProjection(resultCode, data)
    }

    fun attachProjection(mediaProjection: MediaProjection) {
        projection?.stop()
        projection = mediaProjection
    }

    fun getProjection(): MediaProjection? = projection

    fun release() {
        projection?.stop()
        projection = null
        resultData = null
        resultCode = Activity.RESULT_CANCELED
    }
}
