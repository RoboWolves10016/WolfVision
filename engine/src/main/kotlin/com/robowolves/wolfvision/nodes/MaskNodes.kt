/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.nodes

import com.robowolves.wolfvision.Frame
import com.robowolves.wolfvision.InputSpec
import com.robowolves.wolfvision.NodeSpec
import com.robowolves.wolfvision.ParamSpec
import com.robowolves.wolfvision.SocketType
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

private const val MASK = "Mask"

private val MASK_IN = listOf(InputSpec("mask", SocketType.MASK))

internal fun maskNodes(): List<NodeSpec> =
    listOf(
        NodeSpec(
            "Morphology",
            MASK,
            "Shrinks or grows the white areas of a mask. Open removes specks; close fills holes.",
            MASK_IN,
            SocketType.MASK,
            params =
                listOf(
                    ParamSpec.choice("op", "open", "close", "erode", "dilate"),
                    ParamSpec.int("size", 5, 1, 31),
                    ParamSpec.int("iterations", 1, 1, 10),
                ),
        ) { p ->
            val op = p.text("op")
            val k = p.int("size").toDouble()
            val iterations = p.int("iterations")
            object : MatNode() {
                private val kernel =
                    Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k, k))
                private val anchor = Point(-1.0, -1.0)

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    when (op) {
                        "erode" -> Imgproc.erode(src, out, kernel, anchor, iterations)
                        "dilate" -> Imgproc.dilate(src, out, kernel, anchor, iterations)
                        "open" ->
                            Imgproc.morphologyEx(
                                src, out, Imgproc.MORPH_OPEN, kernel, anchor, iterations)
                        else ->
                            Imgproc.morphologyEx(
                                src, out, Imgproc.MORPH_CLOSE, kernel, anchor, iterations)
                    }
                    return out
                }

                override fun release() {
                    super.release()
                    kernel.release()
                }
            }
        },
        NodeSpec(
            "Combine",
            MASK,
            "Combines two masks.",
            listOf(InputSpec("a", SocketType.MASK), InputSpec("b", SocketType.MASK)),
            SocketType.MASK,
            params = listOf(ParamSpec.choice("op", "or", "and", "xor", "subtract")),
        ) { p ->
            val op = p.text("op")
            object : MatNode() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val a = inputs[0] as Mat
                    val b = inputs[1] as Mat
                    when (op) {
                        "or" -> Core.bitwise_or(a, b, out)
                        "and" -> Core.bitwise_and(a, b, out)
                        "xor" -> Core.bitwise_xor(a, b, out)
                        else -> Core.subtract(a, b, out)
                    }
                    return out
                }
            }
        },
        NodeSpec(
            "Invert",
            MASK,
            "Swaps the black and white of a mask.",
            MASK_IN,
            SocketType.MASK,
        ) {
            object : MatNode() {
                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    Core.bitwise_not(inputs[0] as Mat, out)
                    return out
                }
            }
        },
        NodeSpec(
            "ApplyMask",
            MASK,
            "Keeps the pixels of an image where a mask is white and blacks out the rest.",
            listOf(InputSpec("image", SocketType.IMAGE), InputSpec("mask", SocketType.MASK)),
            SocketType.IMAGE,
        ) {
            object : MatNode() {
                private val zero = Scalar.all(0.0)

                override fun process(inputs: Array<Any?>, frame: Frame): Any {
                    val src = inputs[0] as Mat
                    out.create(src.rows(), src.cols(), src.type())
                    out.setTo(zero)
                    src.copyTo(out, inputs[1] as Mat)
                    return out
                }
            }
        },
    )
