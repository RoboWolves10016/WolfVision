/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

/** The kind of value that flows along a connection between two nodes. */
enum class SocketType {
    /** An image of one or three 8-bit channels, as an OpenCV `Mat`. */
    IMAGE,

    /** A single-channel image in which every pixel is 0 or 255, as an OpenCV `Mat`. */
    MASK,

    /** A list of contours, as a `List<MatOfPoint>`. */
    CONTOURS,

    /** A list of [Detection]. */
    DETECTIONS,

    /** A `Double`. */
    NUMBER;

    /**
     * Returns whether an input of this type can be fed by an output of the given type. A mask can
     * be used wherever an image is expected.
     *
     * @param source the type of the output.
     */
    fun accepts(source: SocketType): Boolean = this == source || (this == IMAGE && source == MASK)
}
