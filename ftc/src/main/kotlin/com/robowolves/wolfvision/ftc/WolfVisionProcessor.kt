/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.ftc

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.robowolves.wolfvision.Detection
import com.robowolves.wolfvision.GraphException
import com.robowolves.wolfvision.StarterPipeline
import com.robowolves.wolfvision.VisionResults
import com.robowolves.wolfvision.WolfVision
import java.io.File
import java.io.IOException
import org.firstinspires.ftc.robotcore.internal.camera.calibration.CameraCalibration
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.firstinspires.ftc.vision.VisionProcessor
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Runs a WolfVision pipeline on the frames of a `VisionPortal`.
 *
 * ```
 * WolfVisionProcessor vision = WolfVisionProcessor.load("samples");
 * vision.require("target");
 * VisionPortal portal = new VisionPortal.Builder()
 *         .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
 *         .addProcessor(vision)
 *         .build();
 * ...
 * Detection target = vision.detection("target");
 * ```
 *
 * The outputs can be read from any thread. The boxes of every detection output are drawn on the
 * camera preview of the Driver Station.
 */
class WolfVisionProcessor private constructor(
    /** The pipeline, for starting a studio on it. */
    val vision: WolfVision,
) : VisionProcessor {
    private val lock = Any()
    private val rgb = Mat()
    private var closed = false
    private var paint: Paint? = null

    override fun init(width: Int, height: Int, calibration: CameraCalibration?) {}

    override fun processFrame(frame: Mat, captureTimeNanos: Long): Any? {
        synchronized(lock) {
            if (closed) return null
            // The pipeline takes RGB; a portal may give RGBA.
            val image =
                if (frame.channels() == 4) {
                    Imgproc.cvtColor(frame, rgb, Imgproc.COLOR_RGBA2RGB)
                    rgb
                } else {
                    frame
                }
            vision.process(image, captureTimeNanos)
            return vision.results
        }
    }

    override fun onDrawFrame(
        canvas: Canvas,
        onscreenWidth: Int,
        onscreenHeight: Int,
        scaleBmpPxToCanvasPx: Float,
        scaleCanvasDensity: Float,
        userContext: Any?,
    ) {
        val results = userContext as? VisionResults ?: return
        val stroke =
            paint
                ?: Paint().also {
                    it.color = Color.GREEN
                    it.style = Paint.Style.STROKE
                    it.isAntiAlias = true
                    paint = it
                }
        stroke.strokeWidth = 3f * scaleCanvasDensity
        for (list in results.detections.values) {
            for (d in list) {
                val x = d.x.toFloat() * scaleBmpPxToCanvasPx
                val y = d.y.toFloat() * scaleBmpPxToCanvasPx
                val halfWidth = d.width.toFloat() * scaleBmpPxToCanvasPx / 2f
                val halfHeight = d.height.toFloat() * scaleBmpPxToCanvasPx / 2f
                canvas.save()
                canvas.rotate(d.angle.toFloat(), x, y)
                canvas.drawRect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, stroke)
                canvas.restore()
            }
        }
    }

    /**
     * Checks that the pipeline has an output of a given name. Call it when the OpMode starts, so
     * that a missing or renamed output is found then rather than when it is read.
     *
     * @param name the output name.
     * @throws GraphException if there is no such output.
     */
    fun require(name: String) = vision.require(name)

    /**
     * Returns a number output of the most recent frame.
     *
     * @param name the output name.
     * @return the value, or NaN if there is none.
     */
    fun number(name: String): Double = vision.number(name)

    /**
     * Returns the first detection of a detection output of the most recent frame.
     *
     * @param name the output name.
     * @return the detection, or null if there is none.
     */
    fun detection(name: String): Detection? = vision.detection(name)

    /**
     * Returns a detection output of the most recent frame.
     *
     * @param name the output name.
     * @return the detections; empty if there are none.
     */
    fun detections(name: String): List<Detection> = vision.detections(name)

    /** The outputs of the most recent frame, all from the same frame. */
    val results: VisionResults
        get() = vision.results

    /**
     * Frees the memory of the pipeline. Frames given afterwards are ignored, so it is safe to call
     * while the portal is still running.
     */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            vision.close()
            rgb.release()
        }
    }

    companion object {
        /** The folder of pipelines, inside the `FIRST` folder of the robot and inside assets. */
        const val FOLDER = "wolfvision"

        private val NAME = Regex("[A-Za-z0-9_-]+")

        /**
         * Returns the file of a pipeline in the storage of the robot:
         * `/sdcard/FIRST/wolfvision/<name>.json`.
         *
         * @param name the pipeline name.
         */
        @JvmStatic
        fun file(name: String): File {
            require(NAME.matches(name)) {
                "A pipeline name may hold only letters, digits, - and _: \"$name\""
            }
            return File(File(AppUtil.FIRST_FOLDER, FOLDER), "$name.json")
        }

        /**
         * Loads a pipeline by name. It is taken from the first of these that exists:
         * 1. the file in the storage of the robot, which is what the studio saves;
         * 2. the asset `wolfvision/<name>.json` of the robot app;
         * 3. a starter pipeline, which finds the largest yellow object as the output "target".
         *
         * Whichever it is taken from, the studio saves it to the file in the storage of the robot.
         *
         * @param name the pipeline name.
         * @throws GraphException if the pipeline cannot be read or cannot be run.
         */
        @JvmStatic
        fun load(name: String): WolfVisionProcessor {
            val file = file(name)
            if (file.exists()) return WolfVisionProcessor(WolfVision.load(file))
            val json = asset("$FOLDER/$name.json") ?: StarterPipeline.JSON
            return WolfVisionProcessor(WolfVision.fromJson(json, file = file))
        }

        private fun asset(path: String): String? =
            try {
                AppUtil.getDefContext().assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: IOException) {
                null
            }
    }
}
