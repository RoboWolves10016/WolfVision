/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.nodes

import com.robowolves.wolfvision.Frame
import com.robowolves.wolfvision.InputSpec
import com.robowolves.wolfvision.Node
import com.robowolves.wolfvision.NodeSpec
import com.robowolves.wolfvision.ParamSpec
import com.robowolves.wolfvision.SocketType
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

private const val SOURCE = "Source"
private const val COLOUR = "Colour"
private const val FILTER = "Filter"

private val IMAGE_IN = listOf(InputSpec("image", SocketType.IMAGE))

/** A node whose output is one image, which it owns. */
internal abstract class MatNode : Node() {
    protected val out = Mat()

    override fun release() = out.release()
}

internal fun imageNodes(): List<NodeSpec> =
    listOf(
        NodeSpec(
            "Camera",
            SOURCE,
            "The camera image, in RGB.",
            output = SocketType.IMAGE,
        ) {
            object : Node() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any = frame.image
            }
        },
        NodeSpec(
            "ConvertColor",
            COLOUR,
            "Converts an RGB image to another colour space.",
            IMAGE_IN,
            SocketType.IMAGE,
            params = listOf(ParamSpec.choice("to", "HSV", "YCrCb", "Lab", "Gray")),
        ) { p ->
            val code =
                when (p.text("to")) {
                    "HSV" -> Imgproc.COLOR_RGB2HSV
                    "YCrCb" -> Imgproc.COLOR_RGB2YCrCb
                    "Lab" -> Imgproc.COLOR_RGB2Lab
                    else -> Imgproc.COLOR_RGB2GRAY
                }
            object : MatNode() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    check(src.channels() == 3) { "Needs a colour image" }
                    Imgproc.cvtColor(src, out, code)
                    return out
                }
            }
        },
        NodeSpec(
            "Channel",
            COLOUR,
            "Takes one channel of an image.",
            IMAGE_IN,
            SocketType.IMAGE,
            params = listOf(ParamSpec.int("channel", 0, 0, 2)),
        ) { p ->
            val channel = p.int("channel")
            object : MatNode() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    Core.extractChannel(src, out, minOf(channel, src.channels() - 1))
                    return out
                }
            }
        },
        NodeSpec(
            "Blur",
            FILTER,
            "Smooths an image to remove noise.",
            IMAGE_IN,
            SocketType.IMAGE,
            params =
                listOf(
                    ParamSpec.choice("kind", "gaussian", "median", "box"),
                    ParamSpec.int("size", 5, 1, 31),
                ),
        ) { p ->
            val kind = p.text("kind")
            val k = p.int("size") or 1
            val size = Size(k.toDouble(), k.toDouble())
            object : MatNode() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    when (kind) {
                        "gaussian" -> Imgproc.GaussianBlur(src, out, size, 0.0)
                        "median" -> Imgproc.medianBlur(src, out, k)
                        else -> Imgproc.blur(src, out, size)
                    }
                    return out
                }
            }
        },
        NodeSpec(
            "Threshold",
            COLOUR,
            "Keeps the pixels whose channels are all between min and max. " +
                "If the first min is above the first max, that channel wraps around, as hue does.",
            IMAGE_IN,
            SocketType.MASK,
            params = listOf(ParamSpec.triple("min", 0.0), ParamSpec.triple("max", 255.0)),
        ) { p ->
            val lo = p.triple("min")
            val hi = p.triple("max")
            val wraps = lo[0] > hi[0]
            val low = Scalar(lo[0], lo[1], lo[2])
            val high = Scalar(if (wraps) 255.0 else hi[0], hi[1], hi[2])
            val low2 = Scalar(0.0, lo[1], lo[2])
            val high2 = Scalar(hi[0], hi[1], hi[2])
            object : MatNode() {
                private val second = Mat()

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    Core.inRange(src, low, high, out)
                    if (wraps) {
                        Core.inRange(src, low2, high2, second)
                        Core.bitwise_or(out, second, out)
                    }
                    return out
                }

                override fun release() {
                    super.release()
                    second.release()
                }
            }
        },
        NodeSpec(
            "Canny",
            FILTER,
            "Finds edges.",
            IMAGE_IN,
            SocketType.MASK,
            params =
                listOf(
                    ParamSpec.number("low", 50.0, 0.0, 500.0),
                    ParamSpec.number("high", 150.0, 0.0, 500.0),
                ),
        ) { p ->
            val low = p.number("low")
            val high = p.number("high")
            object : MatNode() {
                private val gray = Mat()

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    if (src.channels() == 3) {
                        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
                        Imgproc.Canny(gray, out, low, high)
                    } else {
                        Imgproc.Canny(src, out, low, high)
                    }
                    return out
                }

                override fun release() {
                    super.release()
                    gray.release()
                }
            }
        },
        NodeSpec(
            "Region",
            FILTER,
            "Blacks out everything outside a rectangle, given in percent of the image. " +
                "Positions stay those of the whole image.",
            listOf(InputSpec("image", SocketType.IMAGE)),
            outputFollowsInput = true,
            params =
                listOf(
                    ParamSpec.number("x", 0.0, 0.0, 100.0),
                    ParamSpec.number("y", 0.0, 0.0, 100.0),
                    ParamSpec.number("width", 100.0, 0.0, 100.0),
                    ParamSpec.number("height", 100.0, 0.0, 100.0),
                ),
        ) { p ->
            val x = p.number("x") / 100.0
            val y = p.number("y") / 100.0
            val w = p.number("width") / 100.0
            val h = p.number("height") / 100.0
            object : MatNode() {
                private val zero = Scalar.all(0.0)

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    out.create(src.rows(), src.cols(), src.type())
                    out.setTo(zero)
                    val left = (x * src.cols()).toInt().coerceIn(0, src.cols())
                    val top = (y * src.rows()).toInt().coerceIn(0, src.rows())
                    val width = (w * src.cols()).toInt().coerceIn(0, src.cols() - left)
                    val height = (h * src.rows()).toInt().coerceIn(0, src.rows() - top)
                    if (width > 0 && height > 0) {
                        val rect = Rect(left, top, width, height)
                        val from = src.submat(rect)
                        val to = out.submat(rect)
                        from.copyTo(to)
                        from.release()
                        to.release()
                    }
                    return out
                }
            }
        },
    )
