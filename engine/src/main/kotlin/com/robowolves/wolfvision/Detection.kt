/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

/**
 * An object found in a frame, as a box around it. Positions and sizes are in pixels of the frame,
 * with the origin at the top left corner.
 *
 * @property x the horizontal position of the centre of the box.
 * @property y the vertical position of the centre of the box.
 * @property width the width of the box.
 * @property height the height of the box.
 * @property angle the rotation of the box in degrees, as OpenCV reports it for a rotated rectangle;
 *   zero for an upright box.
 * @property area the area enclosed by the contour the box was made from, in square pixels.
 */
data class Detection(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val angle: Double,
    val area: Double,
)

/**
 * The outputs of a pipeline for one frame. An instance never changes, so it can be read from any
 * thread.
 *
 * @property numbers the value of every number output, by output name.
 * @property detections the detections of every detection output, by output name.
 * @property frameWidth the width of the frame in pixels; zero before the first frame.
 * @property frameHeight the height of the frame in pixels; zero before the first frame.
 * @property timestampNanos the time the frame was captured, as given to [WolfVision.process].
 * @property valid false before the first frame and for a frame on which a node failed.
 */
class VisionResults(
    val numbers: Map<String, Double>,
    val detections: Map<String, List<Detection>>,
    val frameWidth: Int,
    val frameHeight: Int,
    val timestampNanos: Long,
    val valid: Boolean,
) {
    internal companion object {
        val EMPTY = VisionResults(emptyMap(), emptyMap(), 0, 0, 0L, false)
    }
}
