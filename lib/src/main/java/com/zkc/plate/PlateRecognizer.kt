package com.zkc.plate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.hyperai.hyperlpr3.HyperLPR3
import com.hyperai.hyperlpr3.bean.HyperLPRParameter

/**
 * Lightweight license plate recognition SDK.
 *
 * Usage:
 * ```
 * val recognizer = PlateRecognizer.init(context)
 * val plates = recognizer.recognize(bitmap)  // List<String>
 * ```
 *
 * With custom config:
 * ```
 * val recognizer = PlateRecognizer.init(context, PlateConfig(maxPlates = 3))
 * ```
 */
class PlateRecognizer private constructor(private val config: PlateConfig) {

    companion object {
        private const val TAG = "PlateRecognizer"
        private var instance: PlateRecognizer? = null

        /**
         * Initialize the SDK. Safe to call multiple times — subsequent calls return the existing instance.
         * @param context  any Context (Application context is used internally)
         * @param config   optional configuration; defaults are used when not specified
         */
        fun init(context: Context, config: PlateConfig = PlateConfig()): PlateRecognizer {
            val existing = instance
            if (existing != null) {
                Log.w(TAG, "Already initialized — returning existing instance")
                return existing
            }
            val parameter = HyperLPRParameter()
                .setDetLevel(config.detectionLevel)
                .setMaxNum(config.maxPlates)
                .setRecConfidenceThreshold(config.confidenceThreshold)
            HyperLPR3.getInstance().init(context.applicationContext, parameter)
            val recognizer = PlateRecognizer(config)
            instance = recognizer
            Log.i(TAG, "Initialized — $config")
            return recognizer
        }

        /** Retrieve the singleton instance. Throws if not initialized. */
        fun getInstance(): PlateRecognizer {
            return instance
                ?: throw IllegalStateException("PlateRecognizer not initialized. Call PlateRecognizer.init(context) first.")
        }
    }

    /**
     * Recognize license plates from a bitmap.
     *
     * When [PlateConfig.enableRotationRetry] is true, the image is tried at 0°, 90°, 180°, and 270°
     * rotation until plates are found or all angles are exhausted.
     *
     * @param bitmap  input image (any resolution, will be scaled down automatically)
     * @return list of plate numbers (e.g. ["粤A12345", "京B67890"]), empty if none found
     */
    fun recognize(bitmap: Bitmap): List<String> {
        if (!config.enableRotationRetry) {
            return doRecognize(bitmap, "0°")
        }

        val angles = intArrayOf(0, 90, 180, 270)
        for (angle in angles) {
            if (angle == 0) {
                val result = doRecognize(bitmap, "0°")
                if (result.isNotEmpty()) return result
            } else {
                var rotated: Bitmap? = null
                try {
                    rotated = rotateBitmap(bitmap, angle.toFloat())
                    val result = doRecognize(rotated, "${angle}°")
                    if (result.isNotEmpty()) return result
                } finally {
                    rotated?.recycle()
                }
            }
        }
        return emptyList()
    }

    // ── internal helpers ────────────────────────────────────────────

    /** Single-pass recognition on an upright bitmap. */
    private fun doRecognize(bitmap: Bitmap, tag: String): List<String> {
        val scaledW = config.roiImageWidth
        val scaledH = (bitmap.height.toFloat() / bitmap.width * scaledW).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val plates = HyperLPR3.getInstance().plateRecognition(
            scaledBitmap,
            0,
            HyperLPR3.STREAM_BGRA
        )

        val result = plates.map { it.code }.filter { it.isNotBlank() }
        Log.d(Companion.TAG, "recognize ($tag) → ${result.size} plate(s)")
        return result
    }

    /** Rotate bitmap by [degrees] clockwise, returning a new Bitmap. */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
