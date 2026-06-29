package com.zkc.plate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.hyperai.hyperlpr3.HyperLPR3
import com.hyperai.hyperlpr3.bean.HyperLPRParameter
import com.hyperai.hyperlpr3.bean.Plate

/**
 * Lightweight license plate recognition SDK.
 *
 * Usage:
 * ```
 * val recognizer = PlateRecognizer.init(context)
 * val plates = recognizer.recognize(bitmap)  // List<PlateResult>
 * plates.forEach { p -> Log.i(TAG, "${p.number} @ [${p.x1},${p.y1},${p.x2},${p.y2}]") }
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
     * When [PlateConfig.enableRotationRetry] is true, the image is tried at 0°, 90°, 180°, and
     * 270° rotation until plates are found or all angles are exhausted. All coordinates are
     * returned in the **original input bitmap** coordinate space.
     *
     * @param bitmap  input image (any resolution, will be scaled down automatically)
     * @return list of [PlateResult] with number, confidence, type, and bounding box;
     *         empty if none found
     */
    fun recognize(bitmap: Bitmap): List<PlateResult> {
        val origW = bitmap.width
        val origH = bitmap.height

        if (!config.enableRotationRetry) {
            return detect(bitmap, 0, origW, origH)
        }

        val angles = intArrayOf(0, 90, 180, 270)
        for (angle in angles) {
            if (angle == 0) {
                val result = detect(bitmap, 0, origW, origH)
                if (result.isNotEmpty()) return result
            } else {
                var rotated: Bitmap? = null
                try {
                    rotated = rotateBitmap(bitmap, angle.toFloat())
                    val result = detect(rotated, angle, origW, origH)
                    if (result.isNotEmpty()) return result
                } finally {
                    rotated?.recycle()
                }
            }
        }
        return emptyList()
    }

    // ── internal helpers ────────────────────────────────────────────

    /** Single-pass detection, mapping raw plates into [PlateResult] with corrected coordinates. */
    private fun detect(
        bitmap: Bitmap,
        angle: Int,
        origW: Int,
        origH: Int,
    ): List<PlateResult> {
        val scaledW = config.roiImageWidth
        val scaledH = (bitmap.height.toFloat() / bitmap.width * scaledW).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val rawPlates = HyperLPR3.getInstance().plateRecognition(
            scaledBitmap,
            0,
            HyperLPR3.STREAM_BGRA
        )

        val results = rawPlates
            .filter { it.code.isNotBlank() }
            .map { plate -> plate.toResult(bitmap.width, bitmap.height, angle, origW, origH) }

        Log.d(Companion.TAG, "recognize (${angle}°) → ${results.size} plate(s)")
        return results
    }

    /** Rotate bitmap by [degrees] clockwise, returning a new Bitmap. */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Map a raw hyperlpr3 [Plate] into our [PlateResult] with coordinates in original image space. */
    private fun Plate.toResult(
        bmpW: Int,
        bmpH: Int,
        angle: Int,
        origW: Int,
        origH: Int,
    ): PlateResult {
        // scale from detection space (scaled bitmap) to the bitmap passed to hyperlpr3
        val scale = bmpW.toFloat() / config.roiImageWidth
        val bx1 = x1 * scale
        val by1 = y1 * scale
        val bx2 = x2 * scale
        val by2 = y2 * scale

        // rotate from bitmap space back to original input space
        val (ox1, oy1) = unrotate(bx1, by1, angle, origW, origH)
        val (ox2, oy2) = unrotate(bx2, by2, angle, origW, origH)

        return PlateResult(
            number = code,
            confidence = confidence,
            type = type,
            x1 = ox1,
            y1 = oy1,
            x2 = ox2,
            y2 = oy2,
        )
    }

    /** Reverse a clockwise rotation so coordinates are in the original image space. */
    private fun unrotate(
        x: Float,
        y: Float,
        angle: Int,
        origW: Int,
        origH: Int,
    ): Pair<Float, Float> = when (angle) {
        90  -> y to (origH - x)
        180 -> (origW - x) to (origH - y)
        270 -> (origW - y) to x
        else -> x to y
    }
}
