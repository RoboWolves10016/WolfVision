/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import org.opencv.core.Mat

/**
 * A graph compiled for running: its nodes in the order they run, each with the positions of the
 * nodes feeding it. Compiling checks the whole graph, so a graph that compiles can run.
 *
 * A node with an input that is not connected, or fed by such a node, is inactive: it is skipped and
 * has no value.
 */
internal class Pipeline(val graph: GraphDef, registry: NodeRegistry) : FrameView {
    private val defs: Array<NodeDef>
    private val specs: Array<NodeSpec>
    private val nodes: Array<Node?>
    private val sources: Array<IntArray>
    private val args: Array<Array<Any?>>
    private val types: Array<SocketType?>
    private val values: Array<Any?>
    private val millis: DoubleArray
    private val frame = Frame()

    /** The type of every output of the pipeline, by output name. */
    val outputs: Map<String, SocketType>

    /** The failure of the most recent frame, or null if it ran. */
    @Volatile
    var error: GraphException? = null
        private set

    init {
        val byId = HashMap<String, NodeDef>()
        for (def in graph.nodes) {
            if (byId.put(def.id, def) != null) {
                throw GraphException("Two nodes are named \"${def.id}\"", def.id)
            }
        }
        val specOf = HashMap<String, NodeSpec>()
        for (def in graph.nodes) {
            val spec =
                registry.find(def.type)
                    ?: throw GraphException("Unknown node type \"${def.type}\"", def.id)
            if (def.inputs.size > spec.inputs.size) {
                throw GraphException("${def.type} takes ${spec.inputs.size} inputs", def.id)
            }
            for (input in def.inputs) {
                if (input == null) continue
                val source =
                    byId[input] ?: throw GraphException("No node named \"$input\"", def.id)
                if (registry.find(source.type)?.terminal == true) {
                    throw GraphException("\"$input\" has no output to connect to", def.id)
                }
            }
            specOf[def.id] = spec
        }

        defs = sort(graph.nodes, byId)
        val n = defs.size
        val index = HashMap<String, Int>()
        defs.forEachIndexed { i, def -> index[def.id] = i }
        specs = Array(n) { specOf.getValue(defs[it].id) }
        sources = Array(n) { i -> IntArray(specs[i].inputs.size) { k -> sourceOf(i, k, index) } }
        args = Array(n) { arrayOfNulls<Any?>(specs[it].inputs.size) }
        types = arrayOfNulls(n)
        values = arrayOfNulls(n)
        millis = DoubleArray(n)

        val names = LinkedHashMap<String, SocketType>()
        for (i in 0 until n) {
            types[i] = resolve(i)
            val type = types[i]
            if (type != null && specs[i].type == OUTPUT) {
                val name = defs[i].params["name"]?.takeIf { it.isString() }?.asString ?: "output"
                if (names.put(name, publishedType(type)) != null) {
                    throw GraphException("Two outputs are named \"$name\"", defs[i].id)
                }
            }
        }
        outputs = names

        nodes = arrayOfNulls(n)
        try {
            for (i in 0 until n) {
                val params = Params(defs[i].id, specs[i], defs[i].params)
                // Inactive nodes are created too, so that a wrong setting is reported at once.
                nodes[i] = specs[i].factory(params)
            }
        } catch (e: RuntimeException) {
            release()
            throw e
        }
    }

    private fun sourceOf(i: Int, k: Int, index: Map<String, Int>): Int {
        val id = defs[i].inputs.getOrNull(k) ?: return -1
        return index.getValue(id)
    }

    /** Returns the type of the output of node [i], or null if the node is inactive. */
    private fun resolve(i: Int): SocketType? {
        val spec = specs[i]
        var first: SocketType? = null
        for (k in spec.inputs.indices) {
            val source = sources[i][k]
            if (source < 0) return null
            val type = types[source] ?: return null
            val wanted = spec.inputs[k].type
            if (wanted != null && !wanted.accepts(type)) {
                throw GraphException(
                    "Input \"${spec.inputs[k].name}\" takes ${wanted.label()}, " +
                        "but \"${defs[source].id}\" gives ${type.label()}",
                    defs[i].id,
                )
            }
            if (k == 0) first = type
        }
        return if (spec.outputFollowsInput) first else spec.output
    }

    /**
     * Runs the pipeline on one frame and returns its outputs.
     *
     * @param image the camera image, in RGB order.
     * @param timestampNanos the time the image was captured.
     */
    fun run(image: Mat, timestampNanos: Long): VisionResults {
        frame.image = image
        frame.numbers = HashMap()
        frame.detections = HashMap()
        var failure: GraphException? = null
        for (i in nodes.indices) {
            if (types[i] == null) continue
            val inputs = args[i]
            for (k in inputs.indices) inputs[k] = values[sources[i][k]]
            val start = System.nanoTime()
            try {
                values[i] = nodes[i]!!.process(inputs, frame)
            } catch (e: Exception) {
                failure = GraphException(e.message ?: e.javaClass.simpleName, defs[i].id)
                break
            }
            val ms = (System.nanoTime() - start) / 1e6
            millis[i] = if (millis[i] == 0.0) ms else millis[i] + 0.1 * (ms - millis[i])
        }
        error = failure
        if (failure != null) {
            return VisionResults(
                emptyMap(), emptyMap(), image.cols(), image.rows(), timestampNanos, false)
        }
        return VisionResults(
            frame.numbers, frame.detections, image.cols(), image.rows(), timestampNanos, true)
    }

    /** Frees the native memory of every node. The pipeline is not used afterwards. */
    fun release() {
        for (i in nodes.indices) {
            nodes[i]?.release()
            nodes[i] = null
            values[i] = null
        }
    }

    override val image: Mat
        get() = frame.image

    override val size: Int
        get() = defs.size

    override fun id(index: Int): String = defs[index].id

    override fun type(index: Int): SocketType? = types[index]

    override fun value(index: Int): Any? = values[index]

    override fun millis(index: Int): Double = millis[index]

    private companion object {
        const val OUTPUT = "Output"

        fun publishedType(type: SocketType): SocketType =
            if (type == SocketType.NUMBER) SocketType.NUMBER else SocketType.DETECTIONS

        fun SocketType.label(): String = name.lowercase()

        /** Returns the nodes ordered so that every node comes after the nodes feeding it. */
        fun sort(nodes: List<NodeDef>, byId: Map<String, NodeDef>): Array<NodeDef> {
            val sorted = ArrayList<NodeDef>(nodes.size)
            // 0: not visited, 1: being visited, 2: done.
            val state = HashMap<String, Int>()
            fun visit(def: NodeDef) {
                when (state[def.id]) {
                    2 -> return
                    1 -> throw GraphException("The connections form a loop", def.id)
                }
                state[def.id] = 1
                for (input in def.inputs) if (input != null) visit(byId.getValue(input))
                state[def.id] = 2
                sorted.add(def)
            }
            for (def in nodes) visit(def)
            return sorted.toTypedArray()
        }
    }
}

/**
 * The nodes of the running pipeline and their values for the frame just processed. It is valid only
 * during the call it is passed to, on the frame thread.
 */
interface FrameView {
    /** The camera image, in RGB order. */
    val image: Mat

    /** The number of nodes. */
    val size: Int

    /** Returns the id of a node. */
    fun id(index: Int): String

    /** Returns the type of the output of a node, or null if the node is inactive. */
    fun type(index: Int): SocketType?

    /** Returns the output of a node for this frame, or null if the node is inactive. */
    fun value(index: Int): Any?

    /** Returns the smoothed time a node takes per frame, in milliseconds. */
    fun millis(index: Int): Double
}
