/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class WolfVisionTest {
    private val graph =
        """
        { "version": 1, "nodes": [
          { "id": "camera", "type": "Camera" },
          { "id": "hsv", "type": "ConvertColor", "params": { "to": "HSV" }, "in": ["camera"] },
          { "id": "mask", "type": "Threshold",
            "params": { "min": [20, 100, 100], "max": [40, 255, 255] }, "in": ["hsv"] },
          { "id": "clean", "type": "Morphology", "params": { "op": "open" }, "in": ["mask"] },
          { "id": "blobs", "type": "FindContours", "in": ["clean"] },
          { "id": "best", "type": "Largest", "in": ["blobs"] },
          { "id": "box", "type": "Boxes", "in": ["best"] },
          { "id": "x", "type": "Measure", "params": { "field": "x" }, "in": ["box"] },
          { "id": "out", "type": "Output", "params": { "name": "target" }, "in": ["box"] },
          { "id": "outX", "type": "Output", "params": { "name": "target/x" }, "in": ["x"] }
        ] }
        """

    /** A grey image with a large yellow rectangle centred on (200, 120) and a small one. */
    private fun image(): Mat {
        val image = Mat(240, 320, CvType.CV_8UC3, Scalar(90.0, 90.0, 90.0))
        val yellow = Scalar(255.0, 220.0, 0.0)
        Imgproc.rectangle(image, Point(170.0, 100.0), Point(230.0, 140.0), yellow, -1)
        Imgproc.rectangle(image, Point(20.0, 20.0), Point(40.0, 35.0), yellow, -1)
        return image
    }

    @Test
    fun findsTheLargestYellowArea() {
        val vision = WolfVision.fromJson(graph)
        vision.require("target")
        vision.require("target/x")
        assertNull(vision.detection("target"))
        assertFalse(vision.results.valid)

        val image = image()
        vision.process(image)

        assertNull(vision.error)
        assertTrue(vision.results.valid)
        val target = assertNotNull(vision.detection("target"))
        assertEquals(200.0, target.x, 2.0)
        assertEquals(120.0, target.y, 2.0)
        assertEquals(61.0, target.width, 3.0)
        assertEquals(200.0, vision.number("target/x"), 2.0)
        assertEquals(320, vision.results.frameWidth)
        image.release()
        vision.close()
    }

    @Test
    fun aNewGraphRunsFromTheNextFrame() {
        val vision = WolfVision.fromJson(graph)
        val image = image()
        vision.process(image)
        assertEquals(1, vision.detections("target").size)

        vision.setGraph(GraphDef.fromJson(graph.replace("\"type\": \"Largest\"",
            "\"type\": \"Largest\", \"params\": { \"count\": 5 }")))
        vision.process(image)
        assertEquals(2, vision.detections("target").size)
        image.release()
        vision.close()
    }

    @Test
    fun anInvalidGraphIsRejectedAndTheRunningOneKept() {
        val vision = WolfVision.fromJson(graph)
        val wrongType = graph.replace("\"in\": [\"clean\"]", "\"in\": [\"camera\"]")
        val e = assertFailsWith<GraphException> { vision.setGraph(GraphDef.fromJson(wrongType)) }
        assertEquals("blobs", e.nodeId)

        val image = image()
        vision.process(image)
        assertNotNull(vision.detection("target"))
        image.release()
        vision.close()
    }

    @Test
    fun graphFaultsAreReported() {
        fun fails(json: String) = assertFailsWith<GraphException> { WolfVision.fromJson(json) }
        fails("""{ "nodes": [ { "id": "a", "type": "Nope" } ] }""")
        fails("""{ "nodes": [ { "id": "a", "type": "Camera" }, { "id": "a", "type": "Camera" } ] }""")
        fails("""{ "nodes": [ { "id": "a", "type": "Invert", "in": ["a"] } ] }""")
        fails("""{ "nodes": [ { "id": "a", "type": "Invert", "in": ["missing"] } ] }""")
        fails(
            """{ "nodes": [ { "id": "a", "type": "Blur", "params": { "kind": "x" } } ] }""")
        assertFailsWith<GraphException> { WolfVision.fromJson(graph).require("nothing") }
    }

    @Test
    fun aNodeWithoutItsInputIsInactive() {
        val vision =
            WolfVision.fromJson(
                """{ "nodes": [ { "id": "c", "type": "Camera" },
                    { "id": "blur", "type": "Blur" },
                    { "id": "out", "type": "Output", "in": ["blur"] } ] }""")
        val image = image()
        vision.process(image)
        assertNull(vision.error)
        assertTrue(vision.outputs().isEmpty())
        assertNull(vision.status().first { it.id == "blur" }.type)
        image.release()
        vision.close()
    }

    @Test
    fun aGraphSurvivesBeingWrittenAndRead() {
        val first = GraphDef.fromJson(graph)
        val second = GraphDef.fromJson(first.toJson())
        assertEquals(first.toJson(), second.toJson())
        assertEquals(listOf("hsv"), second.nodes.first { it.id == "mask" }.inputs)
    }

    companion object {
        init {
            nu.pattern.OpenCV.loadLocally()
        }
    }
}
