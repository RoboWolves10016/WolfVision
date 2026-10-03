/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.nodes

import com.robowolves.wolfvision.Detection
import com.robowolves.wolfvision.Frame
import com.robowolves.wolfvision.InputSpec
import com.robowolves.wolfvision.Node
import com.robowolves.wolfvision.NodeSpec
import com.robowolves.wolfvision.ParamSpec
import com.robowolves.wolfvision.SocketType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

private const val DETECT = "Detect"

private val CONTOURS_IN = listOf(InputSpec("contours", SocketType.CONTOURS))

@Suppress("UNCHECKED_CAST") private fun contours(value: Any?): List<MatOfPoint> =
    value as List<MatOfPoint>

/** Returns the boxes around contours, upright or rotated to fit. */
internal fun boxes(
    contours: List<MatOfPoint>,
    rotated: Boolean,
    scratch: MatOfPoint2f,
): List<Detection> =
    contours.map { contour ->
        val area = Imgproc.contourArea(contour)
        if (rotated) {
            scratch.fromArray(*contour.toArray())
            val r = Imgproc.minAreaRect(scratch)
            Detection(r.center.x, r.center.y, r.size.width, r.size.height, r.angle, area)
        } else {
            val r = Imgproc.boundingRect(contour)
            Detection(
                r.x + r.width / 2.0,
                r.y + r.height / 2.0,
                r.width.toDouble(),
                r.height.toDouble(),
                0.0,
                area,
            )
        }
    }

internal fun contourNodes(): List<NodeSpec> =
    listOf(
        NodeSpec(
            "FindContours",
            DETECT,
            "Finds the outline of every white area of a mask.",
            listOf(InputSpec("mask", SocketType.MASK)),
            SocketType.CONTOURS,
            params = listOf(ParamSpec.choice("mode", "outer", "all")),
        ) { p ->
            val mode = if (p.text("mode") == "outer") Imgproc.RETR_EXTERNAL else Imgproc.RETR_LIST
            object : Node() {
                private val found = ArrayList<MatOfPoint>()
                private val hierarchy = Mat()

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    clear()
                    Imgproc.findContours(
                        inputs[0] as Mat, found, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
                    return found
                }

                private fun clear() {
                    for (contour in found) contour.release()
                    found.clear()
                }

                override fun release() {
                    clear()
                    hierarchy.release()
                }
            }
        },
        NodeSpec(
            "FilterContours",
            DETECT,
            "Keeps the contours of the right size and shape. Aspect is width divided by height; " +
                "solidity is how much of its convex hull a contour fills, in percent.",
            CONTOURS_IN,
            SocketType.CONTOURS,
            params =
                listOf(
                    ParamSpec.number("minArea", 100.0, 0.0, 100000.0),
                    ParamSpec.number("maxArea", 100000.0, 0.0, 100000.0),
                    ParamSpec.number("minAspect", 0.0, 0.0, 10.0),
                    ParamSpec.number("maxAspect", 10.0, 0.0, 10.0),
                    ParamSpec.number("minSolidity", 0.0, 0.0, 100.0),
                ),
        ) { p ->
            val minArea = p.number("minArea")
            val maxArea = p.number("maxArea")
            val minAspect = p.number("minAspect")
            val maxAspect = p.number("maxAspect")
            val minSolidity = p.number("minSolidity") / 100.0
            // The top of each range means "no limit".
            val areaCapped = maxArea < 100000.0
            val aspectCapped = maxAspect < 10.0
            object : Node() {
                private val kept = ArrayList<MatOfPoint>()
                private val hullIndices = MatOfInt()
                private val hull = MatOfPoint()

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    kept.clear()
                    for (contour in contours(inputs[0])) {
                        val area = Imgproc.contourArea(contour)
                        if (area < minArea || (areaCapped && area > maxArea)) continue
                        if (minAspect > 0.0 || aspectCapped) {
                            val r = Imgproc.boundingRect(contour)
                            val aspect = r.width.toDouble() / r.height
                            if (aspect < minAspect || (aspectCapped && aspect > maxAspect)) continue
                        }
                        if (minSolidity > 0.0 && solidity(contour, area) < minSolidity) continue
                        kept.add(contour)
                    }
                    return kept
                }

                private fun solidity(contour: MatOfPoint, area: Double): Double {
                    Imgproc.convexHull(contour, hullIndices)
                    val points = contour.toArray()
                    val indices = hullIndices.toArray()
                    hull.fromArray(*Array<Point>(indices.size) { points[indices[it]] })
                    val hullArea = Imgproc.contourArea(hull)
                    return if (hullArea > 0.0) area / hullArea else 0.0
                }

                override fun release() {
                    hullIndices.release()
                    hull.release()
                }
            }
        },
        NodeSpec(
            "Largest",
            DETECT,
            "Keeps the largest contours, largest first.",
            CONTOURS_IN,
            SocketType.CONTOURS,
            params = listOf(ParamSpec.int("count", 1, 1, 50)),
        ) { p ->
            val count = p.int("count")
            object : Node() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any =
                    contours(inputs[0])
                        .map { it to Imgproc.contourArea(it) }
                        .sortedByDescending { it.second }
                        .take(count)
                        .map { it.first }
            }
        },
        NodeSpec(
            "Boxes",
            DETECT,
            "Puts a box around every contour: upright, or rotated to fit as tightly as it can.",
            CONTOURS_IN,
            SocketType.DETECTIONS,
            params = listOf(ParamSpec.choice("kind", "upright", "rotated")),
        ) { p ->
            val rotated = p.text("kind") == "rotated"
            object : Node() {
                private val scratch = MatOfPoint2f()

                override fun process(inputs: Array<Any?>, frame: Frame): Any =
                    boxes(contours(inputs[0]), rotated, scratch)

                override fun release() = scratch.release()
            }
        },
        NodeSpec(
            "Measure",
            DETECT,
            "Takes one number from the first detection, or the count of detections.",
            listOf(InputSpec("detections", SocketType.DETECTIONS)),
            SocketType.NUMBER,
            params =
                listOf(
                    ParamSpec.choice("field", "x", "y", "width", "height", "angle", "area", "count")
                ),
        ) { p ->
            val field = p.text("field")
            object : Node() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    @Suppress("UNCHECKED_CAST") val list = inputs[0] as List<Detection>
                    if (field == "count") return list.size.toDouble()
                    val d = list.firstOrNull() ?: return Double.NaN
                    return when (field) {
                        "x" -> d.x
                        "y" -> d.y
                        "width" -> d.width
                        "height" -> d.height
                        "angle" -> d.angle
                        else -> d.area
                    }
                }
            }
        },
    )
