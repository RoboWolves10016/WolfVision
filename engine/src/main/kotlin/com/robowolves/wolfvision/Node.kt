/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import org.opencv.core.Mat

/**
 * One step of a running pipeline. An instance belongs to one pipeline, is given its settings when
 * it is created, and is called once per frame on the frame thread.
 *
 * A node owns the value it returns and reuses it on the next frame, so that a running pipeline
 * allocates no images.
 */
abstract class Node {
    /**
     * Computes the output of the node for one frame.
     *
     * @param inputs the outputs of the nodes feeding this node, in the order of the inputs of its
     *   type. The array and its values are valid only during the call.
     * @param frame the frame being processed.
     * @return the output, of the type the node's [NodeSpec] declares.
     */
    abstract fun process(inputs: Array<Any?>, frame: Frame): Any?

    /** Frees the native memory the node holds. The node is not used afterwards. */
    open fun release() {}
}

/** The frame a pipeline is processing. */
class Frame internal constructor() {
    /** The camera image, in RGB order. Nodes must not change it. */
    lateinit var image: Mat
        internal set

    internal var numbers = HashMap<String, Double>()
    internal var detections = HashMap<String, List<Detection>>()

    /**
     * Publishes a number as an output of the pipeline.
     *
     * @param name the output name.
     * @param value the value.
     */
    fun publish(name: String, value: Double) {
        numbers[name] = value
    }

    /**
     * Publishes detections as an output of the pipeline.
     *
     * @param name the output name.
     * @param value the detections.
     */
    fun publish(name: String, value: List<Detection>) {
        detections[name] = value
    }
}

/**
 * An input of a node type.
 *
 * @property name the label of the input in the editor.
 * @property type the type of value the input takes, or null if it takes any.
 */
class InputSpec(val name: String, val type: SocketType?)

/** The kind of control the editor shows for a parameter. */
enum class ParamKind {
    /** A whole number between [ParamSpec.min] and [ParamSpec.max]. */
    INT,

    /** A number between [ParamSpec.min] and [ParamSpec.max]. */
    NUMBER,

    /** One of [ParamSpec.choices]. */
    CHOICE,

    /** Three numbers between [ParamSpec.min] and [ParamSpec.max], one per image channel. */
    TRIPLE,

    /** Free text. */
    TEXT,
}

/**
 * A setting of a node type.
 *
 * @property name the name of the parameter in a graph file.
 * @property kind the kind of value.
 * @property default the value used when a graph does not give one: an `Int`, a `Double`, a `String`
 *   or a `List<Double>` of three, by [kind].
 * @property min the least value allowed, for the numeric kinds.
 * @property max the greatest value allowed, for the numeric kinds.
 * @property choices the values allowed, for [ParamKind.CHOICE].
 */
class ParamSpec(
    val name: String,
    val kind: ParamKind,
    val default: Any,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val choices: List<String> = emptyList(),
) {
    companion object {
        /** Returns a whole-number parameter. */
        @JvmStatic
        fun int(name: String, default: Int, min: Int, max: Int): ParamSpec =
            ParamSpec(name, ParamKind.INT, default, min.toDouble(), max.toDouble())

        /** Returns a number parameter. */
        @JvmStatic
        fun number(name: String, default: Double, min: Double, max: Double): ParamSpec =
            ParamSpec(name, ParamKind.NUMBER, default, min, max)

        /** Returns a parameter that takes one of the given values; the first is the default. */
        @JvmStatic
        fun choice(name: String, vararg choices: String): ParamSpec =
            ParamSpec(name, ParamKind.CHOICE, choices[0], choices = choices.toList())

        /** Returns a parameter of three numbers between 0 and 255, one per image channel. */
        @JvmStatic
        fun triple(name: String, default: Double): ParamSpec =
            ParamSpec(name, ParamKind.TRIPLE, listOf(default, default, default), 0.0, 255.0)

        /** Returns a free text parameter. */
        @JvmStatic
        fun text(name: String, default: String): ParamSpec =
            ParamSpec(name, ParamKind.TEXT, default)
    }
}

/**
 * A node type: what it takes, what it gives, its settings and how to create it.
 *
 * @property type the name of the type in a graph file.
 * @property category the group the editor lists the type under.
 * @property description one sentence on what the node does, shown in the editor.
 * @property inputs the inputs, in order.
 * @property output the type of the output; null if the output has the type of the first input
 *   ([outputFollowsInput]).
 * @property outputFollowsInput true if the output has the type of whatever feeds the first input.
 * @property terminal true if nothing can be connected to the output of the node.
 * @property params the settings.
 * @property factory creates a node from its settings.
 */
class NodeSpec(
    val type: String,
    val category: String,
    val description: String,
    val inputs: List<InputSpec> = emptyList(),
    val output: SocketType? = null,
    val outputFollowsInput: Boolean = false,
    val terminal: Boolean = false,
    val params: List<ParamSpec> = emptyList(),
    val factory: (Params) -> Node,
)

/**
 * The settings of one node, read against the parameters its type declares. A value outside the
 * range of its parameter is brought into the range.
 */
class Params
internal constructor(
    private val nodeId: String,
    private val spec: NodeSpec,
    private val values: Map<String, JsonElement>,
) {
    /** Returns the value of a whole-number parameter. */
    fun int(name: String): Int {
        val p = param(name)
        val v = values[name]?.let { number(name, it) } ?: (p.default as Number).toDouble()
        return Math.round(v.coerceIn(p.min, p.max)).toInt()
    }

    /** Returns the value of a number parameter. */
    fun number(name: String): Double {
        val p = param(name)
        val v = values[name]?.let { number(name, it) } ?: (p.default as Number).toDouble()
        return v.coerceIn(p.min, p.max)
    }

    /** Returns the value of a choice or text parameter. */
    fun text(name: String): String {
        val p = param(name)
        val element = values[name] ?: return p.default as String
        if (!element.isString()) throw GraphException("\"$name\" must be text", nodeId)
        val v = element.asString
        if (p.kind == ParamKind.CHOICE && v !in p.choices) {
            throw GraphException("\"$name\" must be one of ${p.choices}, not \"$v\"", nodeId)
        }
        return v
    }

    /** Returns the three values of a triple parameter. */
    fun triple(name: String): DoubleArray {
        val p = param(name)
        val element = values[name]
        if (element == null) {
            @Suppress("UNCHECKED_CAST") val d = p.default as List<Double>
            return d.toDoubleArray()
        }
        if (element !is JsonArray || element.size() != 3) {
            throw GraphException("\"$name\" must be three numbers", nodeId)
        }
        return DoubleArray(3) { number(name, element.get(it)).coerceIn(p.min, p.max) }
    }

    private fun param(name: String): ParamSpec =
        spec.params.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("${spec.type} has no parameter \"$name\"")

    private fun number(name: String, element: JsonElement): Double {
        if (!element.isNumber()) throw GraphException("\"$name\" must be a number", nodeId)
        val v = element.asDouble
        if (v.isNaN()) throw GraphException("\"$name\" must be a number", nodeId)
        return v
    }
}
