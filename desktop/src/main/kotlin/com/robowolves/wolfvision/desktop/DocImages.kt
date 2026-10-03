/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.desktop

import com.robowolves.wolfvision.Detection
import com.robowolves.wolfvision.FrameTap
import com.robowolves.wolfvision.SocketType
import com.robowolves.wolfvision.WolfVision
import java.io.File
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/** Every node the node reference shows, in one graph. The image of each is named after its id. */
private const val GRAPH =
    """{ "nodes": [
  { "id": "camera", "type": "Camera" },
  { "id": "hsv", "type": "ConvertColor", "params": { "to": "HSV" }, "in": ["camera"] },
  { "id": "saturation", "type": "Channel", "params": { "channel": 1 }, "in": ["hsv"] },
  { "id": "blur", "type": "Blur", "params": { "size": 15 }, "in": ["camera"] },
  { "id": "smooth", "type": "Blur", "params": { "size": 5 }, "in": ["camera"] },
  { "id": "edges", "type": "Canny", "params": { "low": 60, "high": 160 }, "in": ["smooth"] },
  { "id": "region", "type": "Region",
    "params": { "x": 25, "y": 20, "width": 55, "height": 60 }, "in": ["camera"] },
  { "id": "yellow", "type": "Threshold",
    "params": { "min": [20, 100, 100], "max": [35, 255, 255] }, "in": ["hsv"] },
  { "id": "blue", "type": "Threshold",
    "params": { "min": [100, 100, 100], "max": [130, 255, 255] }, "in": ["hsv"] },
  { "id": "clean", "type": "Morphology", "params": { "op": "open", "size": 5 },
    "in": ["yellow"] },
  { "id": "both", "type": "Combine", "params": { "op": "or" }, "in": ["yellow", "blue"] },
  { "id": "inverted", "type": "Invert", "in": ["clean"] },
  { "id": "cut", "type": "ApplyMask", "in": ["camera", "clean"] },
  { "id": "bothClean", "type": "Morphology", "params": { "op": "open", "size": 5 },
    "in": ["both"] },
  { "id": "contours", "type": "FindContours", "in": ["bothClean"] },
  { "id": "filtered", "type": "FilterContours", "params": { "minArea": 1000 },
    "in": ["contours"] },
  { "id": "largest", "type": "Largest", "params": { "count": 1 }, "in": ["contours"] },
  { "id": "boxes", "type": "Boxes", "params": { "kind": "rotated" }, "in": ["contours"] }
] }"""

private fun block(into: Mat, x: Double, y: Double, w: Double, h: Double, angle: Double, c: Scalar) {
    val corners = arrayOfNulls<Point>(4)
    RotatedRect(Point(x, y), Size(w, h), angle).points(corners)
    val polygon = MatOfPoint(*corners.requireNoNulls())
    Imgproc.fillConvexPoly(into, polygon, c)
    polygon.release()
}

/** Returns a picture of coloured blocks on a grey floor, with a little noise and a few specks. */
private fun scene(): Mat {
    val yellow = Scalar(240.0, 200.0, 20.0)
    val image = Mat(240, 320, CvType.CV_8UC3, Scalar(95.0, 100.0, 105.0))
    block(image, 195.0, 125.0, 96.0, 38.0, 20.0, yellow)
    block(image, 75.0, 175.0, 70.0, 42.0, -12.0, Scalar(30.0, 70.0, 220.0))
    block(image, 255.0, 50.0, 60.0, 36.0, 8.0, Scalar(215.0, 40.0, 45.0))
    block(image, 45.0, 45.0, 26.0, 16.0, 0.0, yellow)
    for ((x, y) in listOf(120 to 30, 150 to 210, 290 to 190, 20 to 120, 230 to 215, 110 to 95)) {
        Imgproc.circle(image, Point(x.toDouble(), y.toDouble()), 1, yellow, -1)
    }
    val noise = Mat(image.rows(), image.cols(), image.type())
    Core.setRNGSeed(1)
    Core.randn(noise, 0.0, 5.0)
    Core.add(image, noise, image)
    noise.release()
    return image
}

private val GREEN = Scalar(0.0, 255.0, 0.0)
private val RED = Scalar(255.0, 0.0, 0.0)

/**
 * Writes the example images of the node reference, by running the real nodes on one picture.
 *
 * The only argument is the folder to write to.
 */
fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()
    val folder = File(args[0])
    folder.mkdirs()
    val quality = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 88)
    val vision = WolfVision.fromJson(GRAPH)
    val image = scene()

    vision.tap = FrameTap { view ->
        val picture = Mat()
        val bgr = Mat()
        for (i in 0 until view.size) {
            val value = view.value(i)
            when (view.type(i)) {
                SocketType.IMAGE,
                SocketType.MASK -> (value as Mat).copyTo(picture)
                SocketType.CONTOURS -> {
                    view.image.copyTo(picture)
                    @Suppress("UNCHECKED_CAST")
                    Imgproc.drawContours(picture, value as List<MatOfPoint>, -1, GREEN, 2)
                }
                SocketType.DETECTIONS -> {
                    view.image.copyTo(picture)
                    @Suppress("UNCHECKED_CAST")
                    for (d in value as List<Detection>) {
                        val corners = arrayOfNulls<Point>(4)
                        RotatedRect(Point(d.x, d.y), Size(d.width, d.height), d.angle)
                            .points(corners)
                        for (k in 0 until 4) {
                            Imgproc.line(picture, corners[k], corners[(k + 1) % 4], GREEN, 2)
                        }
                        Imgproc.circle(picture, Point(d.x, d.y), 3, RED, -1)
                    }
                }
                else -> continue
            }
            val encoded =
                if (picture.channels() == 3) {
                    Imgproc.cvtColor(picture, bgr, Imgproc.COLOR_RGB2BGR)
                    bgr
                } else {
                    picture
                }
            Imgcodecs.imwrite(File(folder, view.id(i) + ".jpg").path, encoded, quality)
        }
        picture.release()
        bgr.release()
    }
    vision.process(image)
    vision.error?.let { throw it }
    println("Wrote ${vision.status().size} images to $folder")
    image.release()
    vision.close()
}
