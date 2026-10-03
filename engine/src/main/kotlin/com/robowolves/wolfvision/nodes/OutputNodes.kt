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
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

internal fun outputNodes(): List<NodeSpec> =
    listOf(
        NodeSpec(
            "Output",
            "Output",
            "Gives robot code a value under a name. A number is published as a number; " +
                "anything else as detections.",
            listOf(InputSpec("value", null)),
            outputFollowsInput = true,
            terminal = true,
            params = listOf(ParamSpec.text("name", "output")),
        ) { p ->
            val name = p.text("name")
            object : Node() {
                private val scratch = MatOfPoint2f()
                private val found = ArrayList<MatOfPoint>()
                private val hierarchy = Mat()

                override fun process(inputs: Array<Any?>, frame: Frame): Any? {
                    val value = inputs[0]
                    when (value) {
                        is Double -> frame.publish(name, value)
                        is Mat -> {
                            // An image or mask is published as the boxes of its white areas.
                            clear()
                            if (value.channels() == 1) {
                                Imgproc.findContours(
                                    value,
                                    found,
                                    hierarchy,
                                    Imgproc.RETR_EXTERNAL,
                                    Imgproc.CHAIN_APPROX_SIMPLE,
                                )
                            }
                            frame.publish(name, boxes(found, false, scratch))
                        }
                        is List<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val detections =
                                if (value.firstOrNull() is MatOfPoint) {
                                    boxes(value as List<MatOfPoint>, false, scratch)
                                } else {
                                    value as List<Detection>
                                }
                            frame.publish(name, detections)
                        }
                    }
                    return value
                }

                private fun clear() {
                    for (contour in found) contour.release()
                    found.clear()
                }

                override fun release() {
                    clear()
                    scratch.release()
                    hierarchy.release()
                }
            }
        }
    )
