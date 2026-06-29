package com.zkc.plate

/**
 * Full recognition result including bounding box.
 *
 * All coordinates are in the **original input bitmap** coordinate space,
 * regardless of any internal scaling or rotation.
 *
 * @param number      plate number string (e.g. "粤A12345")
 * @param confidence  recognition confidence (0.0 – 1.0)
 * @param type        plate type code (e.g. blue / green / yellow)
 * @param x1          left edge of the plate bounding box
 * @param y1          top edge of the plate bounding box
 * @param x2          right edge of the plate bounding box
 * @param y2          bottom edge of the plate bounding box
 */
data class PlateResult(
    val number: String,
    val confidence: Float,
    val type: Int,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)
