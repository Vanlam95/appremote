package com.appremote.remotecontrol.util

data class TapCalibration(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
)

object TouchCoordinateMapper {

    /** Cách tính ban đầu đã hoạt động ổn + bù lệch tùy chỉnh. */
    fun mapSimple(
        touchX: Float,
        touchY: Float,
        viewWidth: Float,
        viewHeight: Float,
        remoteWidth: Int,
        remoteHeight: Int,
        calibration: TapCalibration
    ): Pair<Float, Float>? {
        if (remoteWidth <= 0 || remoteHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return null
        }
        val x = (touchX / viewWidth * remoteWidth + calibration.offsetX)
            .coerceIn(0f, remoteWidth.toFloat())
        val y = (touchY / viewHeight * remoteHeight + calibration.offsetY)
            .coerceIn(0f, remoteHeight.toFloat())
        return Pair(x, y)
    }
}
