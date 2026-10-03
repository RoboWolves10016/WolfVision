/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.studio

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.robowolves.wolfvision.Detection
import com.robowolves.wolfvision.FrameTap
import com.robowolves.wolfvision.FrameView
import com.robowolves.wolfvision.SocketType
import java.util.concurrent.ConcurrentHashMap
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * The picture of each watched node. A node is watched from the moment its picture is asked for
 * until [WATCH_NANOS] after it was last asked for; only watched nodes are copied, on the frame
 * thread, and a picture is encoded on the thread that asks for it.
 */
internal class Previews : FrameTap {
    private class Preview {
        val mat = Mat()
        @Volatile var requestedNanos = System.nanoTime()
        var released = false
    }

    private val previews = ConcurrentHashMap<String, Preview>()

    /** The value of every node that gives a number, by node id. */
    val numbers = ConcurrentHashMap<String, Double>()

    override fun onFrame(view: FrameView) {
        val now = System.nanoTime()
        for (i in 0 until view.size) {
            val id = view.id(i)
            val type = view.type(i)
            val value = view.value(i)
            if (type == SocketType.NUMBER && value is Double) numbers[id] = value
            val preview = previews[id] ?: continue
            if (now - preview.requestedNanos > WATCH_NANOS) {
                if (now - preview.requestedNanos > FORGET_NANOS) forget(id, preview)
                continue
            }
            synchronized(preview) { if (!preview.released) render(view, type, value, preview.mat) }
        }
    }

    private fun render(view: FrameView, type: SocketType?, value: Any?, into: Mat) {
        when (type) {
            SocketType.IMAGE,
            SocketType.MASK -> (value as Mat).copyTo(into)
            SocketType.CONTOURS -> {
                view.image.copyTo(into)
                @Suppress("UNCHECKED_CAST")
                Imgproc.drawContours(into, value as List<MatOfPoint>, -1, GREEN, 2)
            }
            SocketType.DETECTIONS -> {
                view.image.copyTo(into)
                @Suppress("UNCHECKED_CAST") for (d in value as List<Detection>) draw(into, d)
            }
            else -> {}
        }
    }

    private fun draw(into: Mat, d: Detection) {
        val corners = arrayOfNulls<Point>(4)
        RotatedRect(Point(d.x, d.y), Size(d.width, d.height), d.angle).points(corners)
        for (k in 0 until 4) Imgproc.line(into, corners[k], corners[(k + 1) % 4], GREEN, 2)
        Imgproc.circle(into, Point(d.x, d.y), 4, RED, -1)
    }

    /**
     * Returns the picture of a node as a JPEG, and marks the node as watched.
     *
     * @param id the node id.
     * @param width the greatest width of the picture, in pixels.
     * @return the JPEG, or null if the node has no picture yet.
     */
    fun jpeg(id: String, width: Int): ByteArray? {
        val preview = previews.getOrPut(id) { Preview() }
        preview.requestedNanos = System.nanoTime()
        val small = Mat()
        val colour = Mat()
        val bytes = MatOfByte()
        val quality = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY)
        try {
            synchronized(preview) {
                val mat = preview.mat
                if (preview.released || mat.empty()) return null
                if (mat.cols() > width) {
                    val height = maxOf(1, mat.rows() * width / mat.cols())
                    Imgproc.resize(
                        mat, small, Size(width.toDouble(), height.toDouble()), 0.0, 0.0,
                        Imgproc.INTER_NEAREST)
                } else {
                    mat.copyTo(small)
                }
            }
            val encoded =
                when (small.channels()) {
                    3 -> colour.also { Imgproc.cvtColor(small, it, Imgproc.COLOR_RGB2BGR) }
                    4 -> colour.also { Imgproc.cvtColor(small, it, Imgproc.COLOR_RGBA2BGR) }
                    else -> small
                }
            Imgcodecs.imencode(".jpg", encoded, bytes, quality)
            return bytes.toArray()
        } finally {
            small.release()
            colour.release()
            bytes.release()
            quality.release()
        }
    }

    /**
     * Returns the range of the channel values of a node's picture around a point.
     *
     * @param id the node id.
     * @param x the horizontal position, from 0 at the left to 1 at the right.
     * @param y the vertical position, from 0 at the top to 1 at the bottom.
     * @param radius how far around the point to look, in pixels.
     * @return the least, greatest and mean value of each channel, or null if there is no picture.
     */
    fun sample(id: String, x: Double, y: Double, radius: Int): JsonObject? {
        val preview = previews[id] ?: return null
        val channels: Int
        val data: ByteArray
        synchronized(preview) {
            val mat = preview.mat
            if (preview.released || mat.empty()) return null
            val cx = (x * mat.cols()).toInt().coerceIn(0, mat.cols() - 1)
            val cy = (y * mat.rows()).toInt().coerceIn(0, mat.rows() - 1)
            val left = maxOf(0, cx - radius)
            val top = maxOf(0, cy - radius)
            val right = minOf(mat.cols() - 1, cx + radius)
            val bottom = minOf(mat.rows() - 1, cy + radius)
            val region = mat.submat(Rect(left, top, right - left + 1, bottom - top + 1))
            val copy = region.clone()
            channels = copy.channels()
            data = ByteArray(copy.total().toInt() * channels)
            copy.get(0, 0, data)
            copy.release()
            region.release()
        }
        val min = IntArray(channels) { 255 }
        val max = IntArray(channels)
        val sum = LongArray(channels)
        for (i in data.indices) {
            val v = data[i].toInt() and 0xff
            val c = i % channels
            if (v < min[c]) min[c] = v
            if (v > max[c]) max[c] = v
            sum[c] += v.toLong()
        }
        val pixels = data.size / channels
        val o = JsonObject()
        val mins = JsonArray()
        val maxs = JsonArray()
        val means = JsonArray()
        for (c in 0 until channels) {
            mins.add(min[c])
            maxs.add(max[c])
            means.add(sum[c].toDouble() / pixels)
        }
        o.add("min", mins)
        o.add("max", maxs)
        o.add("mean", means)
        return o
    }

    private fun forget(id: String, preview: Preview) {
        previews.remove(id)
        synchronized(preview) {
            preview.released = true
            preview.mat.release()
        }
    }

    /** Frees every picture. */
    fun release() {
        for ((id, preview) in previews) forget(id, preview)
        numbers.clear()
    }

    private companion object {
        const val WATCH_NANOS = 2_000_000_000L
        const val FORGET_NANOS = 30_000_000_000L
        const val JPEG_QUALITY = 75
        val GREEN = Scalar(0.0, 255.0, 0.0)
        val RED = Scalar(255.0, 0.0, 0.0)
    }
}
