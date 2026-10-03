/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.opencv.core.Mat

/** Receives every processed frame. Called on the frame thread. */
fun interface FrameTap {
    /**
     * Called after a frame has been processed.
     *
     * @param view the nodes and their values, valid only during the call.
     */
    fun onFrame(view: FrameView)
}

/**
 * The state of one node of the running pipeline.
 *
 * @property id the id of the node.
 * @property type the type of its output, or null if the node is inactive.
 * @property millis the smoothed time it takes per frame, in milliseconds.
 */
class NodeStatus(val id: String, val type: SocketType?, val millis: Double)

/**
 * A vision pipeline loaded from a graph, with no code written for it.
 *
 * Give every camera frame to [process], on one thread, and read the outputs from any thread with
 * [number], [detections] and [detection]. Outputs are named by the Output nodes of the graph.
 *
 * ```
 * WolfVision vision = WolfVision.load(new File("/sdcard/FIRST/wolfvision/samples.json"));
 * vision.require("target");
 * ...
 * Detection target = vision.detection("target");
 * ```
 */
class WolfVision
private constructor(
    graph: GraphDef,
    private val registry: NodeRegistry,
    /** The file the graph was loaded from, or null. */
    val file: File?,
) : AutoCloseable {
    @Volatile private var pipeline = Pipeline(graph, registry)
    private val pending = AtomicReference<Pipeline?>()
    private var lastFrameNanos = 0L

    /** The outputs of the most recent frame. */
    @Volatile
    var results: VisionResults = VisionResults.EMPTY
        private set

    /** The smoothed rate at which frames are processed, in frames per second. */
    @Volatile
    var fps: Double = 0.0
        private set

    /** Receives every processed frame, or null. Used by the studio; null costs nothing. */
    @Volatile var tap: FrameTap? = null

    /** The graph most recently set, which runs from the next frame. */
    val graph: GraphDef
        get() = (pending.get() ?: pipeline).graph

    /** The failure of the most recent frame, or null if it ran. */
    val error: GraphException?
        get() = pipeline.error

    /**
     * Processes one frame. Call it from one thread only.
     *
     * @param image the camera image, in RGB order. It is not changed.
     * @param timestampNanos the time the image was captured.
     */
    @JvmOverloads
    fun process(image: Mat, timestampNanos: Long = System.nanoTime()) {
        val next = pending.getAndSet(null)
        if (next != null) {
            val old = pipeline
            pipeline = next
            old.release()
        }
        val running = pipeline
        results = running.run(image, timestampNanos)

        val now = System.nanoTime()
        if (lastFrameNanos != 0L && now > lastFrameNanos) {
            val rate = 1e9 / (now - lastFrameNanos)
            fps = if (fps == 0.0) rate else fps + 0.1 * (rate - fps)
        }
        lastFrameNanos = now
        tap?.onFrame(running)
    }

    /**
     * Replaces the graph. The new graph is checked now and runs from the next frame; if it is not
     * valid, the running graph is kept. Safe to call from any thread.
     *
     * @param graph the new graph.
     * @throws GraphException if the graph cannot be run.
     */
    fun setGraph(graph: GraphDef) {
        pending.getAndSet(Pipeline(graph, registry))?.release()
    }

    /**
     * Checks that the graph has an output of a given name, so that a missing or renamed output is
     * found when the pipeline is loaded rather than when it is read.
     *
     * @param name the output name.
     * @throws GraphException if there is no such output.
     */
    fun require(name: String) {
        val outputs = (pending.get() ?: pipeline).outputs
        if (name !in outputs) {
            throw GraphException("The pipeline has no output \"$name\"; it has ${outputs.keys}")
        }
    }

    /** Returns the type of every output of the graph, by output name. */
    fun outputs(): Map<String, SocketType> = (pending.get() ?: pipeline).outputs

    /**
     * Returns a number output of the most recent frame.
     *
     * @param name the output name.
     * @return the value, or NaN if there is none.
     */
    fun number(name: String): Double = results.numbers[name] ?: Double.NaN

    /**
     * Returns a detection output of the most recent frame.
     *
     * @param name the output name.
     * @return the detections, largest first if the graph sorts them; empty if there are none.
     */
    fun detections(name: String): List<Detection> = results.detections[name] ?: emptyList()

    /**
     * Returns the first detection of a detection output of the most recent frame.
     *
     * @param name the output name.
     * @return the detection, or null if there is none.
     */
    fun detection(name: String): Detection? = results.detections[name]?.firstOrNull()

    /** Returns the state of every node of the running pipeline. Safe to call from any thread. */
    fun status(): List<NodeStatus> {
        val p = pipeline
        return List(p.size) { NodeStatus(p.id(it), p.type(it), p.millis(it)) }
    }

    /**
     * Writes the graph to a file.
     *
     * @param target the file to write; by default the file the graph was loaded from.
     * @throws IllegalStateException if no file is given and the graph was not loaded from one.
     */
    @JvmOverloads
    fun save(target: File? = file) {
        checkNotNull(target) { "The pipeline was not loaded from a file" }
        target.absoluteFile.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        temp.writeText(graph.toJson())
        if (!temp.renameTo(target)) {
            target.writeText(graph.toJson())
            temp.delete()
        }
    }

    /** Frees the native memory of the pipeline. Call it once frames are no longer processed. */
    override fun close() {
        pending.getAndSet(null)?.release()
        pipeline.release()
    }

    companion object {
        /**
         * Loads a pipeline from a graph file.
         *
         * @param file the graph file.
         * @param registry the node types the graph may use.
         * @throws GraphException if the file cannot be read or the graph cannot be run.
         */
        @JvmStatic
        @JvmOverloads
        fun load(file: File, registry: NodeRegistry = NodeRegistry.standard()): WolfVision {
            val text =
                try {
                    file.readText()
                } catch (e: java.io.IOException) {
                    throw GraphException("Cannot read $file: ${e.message}")
                }
            return WolfVision(GraphDef.fromJson(text), registry, file)
        }

        /**
         * Creates a pipeline from the text of a graph.
         *
         * @param json the graph, as JSON.
         * @param registry the node types the graph may use.
         * @param file the file [save] writes to, or null.
         * @throws GraphException if the graph cannot be run.
         */
        @JvmStatic
        @JvmOverloads
        fun fromJson(
            json: String,
            registry: NodeRegistry = NodeRegistry.standard(),
            file: File? = null,
        ): WolfVision = WolfVision(GraphDef.fromJson(json), registry, file)
    }
}
