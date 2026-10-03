/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.desktop

import com.robowolves.wolfvision.WolfVision
import com.robowolves.wolfvision.studio.Studio
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture

private const val USAGE =
    """Usage: desktop <graph.json> [source] [port]
  source  demo (the default): moving coloured shapes
          camera or camera:<index>: a camera of this computer
          a path to an image, or to a folder of images shown in turn
  port    the port of the studio; 5800 by default"""

private const val STARTER =
    """{ "version": 1, "nodes": [
  { "id": "camera", "type": "Camera", "x": 20, "y": 40 },
  { "id": "hsv", "type": "ConvertColor", "params": { "to": "HSV" }, "in": ["camera"],
    "x": 260, "y": 40 },
  { "id": "mask", "type": "Threshold",
    "params": { "min": [20, 100, 100], "max": [35, 255, 255] }, "in": ["hsv"], "x": 500, "y": 40 },
  { "id": "clean", "type": "Morphology", "params": { "op": "open", "size": 5 }, "in": ["mask"],
    "x": 740, "y": 40 },
  { "id": "blobs", "type": "FindContours", "in": ["clean"], "x": 20, "y": 420 },
  { "id": "best", "type": "Largest", "in": ["blobs"], "x": 260, "y": 420 },
  { "id": "box", "type": "Boxes", "params": { "kind": "rotated" }, "in": ["best"],
    "x": 500, "y": 420 },
  { "id": "target", "type": "Output", "params": { "name": "target" }, "in": ["box"],
    "x": 740, "y": 420 }
] }
"""

/** Supplies frames in RGB order. */
private interface Source {
    /** Fills [into] with the next frame and returns whether there was one. */
    fun read(into: Mat): Boolean
}

private class DemoSource : Source {
    private val noise = Mat()
    private var t = 0.0

    override fun read(into: Mat): Boolean {
        t += 1.0 / 30.0
        into.create(480, 640, CvType.CV_8UC3)
        into.setTo(Scalar(95.0, 100.0, 105.0))
        Imgproc.circle(
            into, Point(150 + 60 * sin(t * 0.7), 340 + 40 * cos(t * 0.9)), 45,
            Scalar(30.0, 70.0, 220.0), -1)
        Imgproc.circle(
            into, Point(500 + 50 * cos(t * 0.5), 130 + 30 * sin(t * 1.1)), 38,
            Scalar(215.0, 40.0, 45.0), -1)
        val corners = arrayOfNulls<Point>(4)
        RotatedRect(
                Point(320 + 150 * sin(t * 0.4), 240 + 70 * sin(t * 0.6)),
                Size(150.0, 60.0),
                t * 20.0,
            )
            .points(corners)
        val sample = MatOfPoint(*corners.requireNoNulls())
        Imgproc.fillConvexPoly(into, sample, Scalar(240.0, 200.0, 20.0))
        sample.release()
        Imgproc.rectangle(into, Point(40.0, 60.0), Point(70.0, 80.0), Scalar(235.0, 205.0, 40.0), -1)
        noise.create(into.rows(), into.cols(), into.type())
        Core.randn(noise, 0.0, 6.0)
        Core.add(into, noise, into)
        return true
    }
}

private class CameraSource(index: Int) : Source {
    private val capture = VideoCapture(index)
    private val bgr = Mat()

    init {
        check(capture.isOpened) { "Cannot open camera $index" }
    }

    override fun read(into: Mat): Boolean {
        if (!capture.read(bgr) || bgr.empty()) return false
        Imgproc.cvtColor(bgr, into, Imgproc.COLOR_BGR2RGB)
        return true
    }
}

private class ImageSource(path: File) : Source {
    private val images: List<Mat>
    private val started = System.nanoTime()

    init {
        val files =
            if (path.isDirectory) {
                path.listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg") }
                    .orEmpty()
                    .sortedBy { it.name }
            } else {
                listOf(path)
            }
        images =
            files.mapNotNull { file ->
                val bgr = Imgcodecs.imread(file.path)
                if (bgr.empty()) return@mapNotNull null
                val rgb = Mat()
                Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB)
                bgr.release()
                rgb
            }
        check(images.isNotEmpty()) { "No image could be read from $path" }
    }

    override fun read(into: Mat): Boolean {
        val seconds = (System.nanoTime() - started) / 1_000_000_000L
        images[((seconds / 3) % images.size).toInt()].copyTo(into)
        return true
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--help") {
        println(USAGE)
        exitProcess(if (args.isEmpty()) 2 else 0)
    }
    nu.pattern.OpenCV.loadLocally()

    val file = File(args[0])
    if (!file.exists()) {
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(STARTER)
        println("Created $file with a starter pipeline")
    }
    val sourceName = args.getOrElse(1) { "demo" }
    val source: Source =
        when {
            sourceName == "demo" -> DemoSource()
            sourceName.startsWith("camera") ->
                CameraSource(sourceName.substringAfter(':', "0").toInt())
            else -> ImageSource(File(sourceName))
        }
    val port = args.getOrNull(2)?.toInt() ?: Studio.DEFAULT_PORT

    val vision = WolfVision.load(file)
    val studio = Studio.start(vision, port)
    println("WolfVision Studio: http://localhost:${studio.port}/")
    Runtime.getRuntime().addShutdownHook(Thread { studio.stop() })

    val frame = Mat()
    val periodNanos = 1_000_000_000L / 30
    while (true) {
        val start = System.nanoTime()
        if (source.read(frame)) vision.process(frame, start)
        val left = periodNanos - (System.nanoTime() - start)
        if (left > 0) Thread.sleep(left / 1_000_000, (left % 1_000_000).toInt())
    }
}
