/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Thrown when a pipeline graph cannot be read or cannot be run.
 *
 * @property nodeId the id of the node at fault, or null if the fault is not in one node.
 */
class GraphException(message: String, val nodeId: String? = null) : RuntimeException(message)

/**
 * One node of a pipeline graph, as it is stored.
 *
 * @property id the name of the node, unique in its graph.
 * @property type the node type, as registered in a [NodeRegistry].
 * @property params the settings of the node, by parameter name. A missing parameter takes its
 *   default.
 * @property inputs the id of the node feeding each input, in the order of the inputs of the type;
 *   null for an input that is not connected.
 * @property x the horizontal position of the node in the editor.
 * @property y the vertical position of the node in the editor.
 */
class NodeDef(
    val id: String,
    val type: String,
    val params: Map<String, JsonElement> = emptyMap(),
    val inputs: List<String?> = emptyList(),
    val x: Double = 0.0,
    val y: Double = 0.0,
)

/**
 * A pipeline graph, as it is stored: nodes, their settings and their connections.
 *
 * @property nodes the nodes, in any order.
 */
class GraphDef(val nodes: List<NodeDef>) {
    /** Returns the graph as indented JSON text. */
    fun toJson(): String = PRETTY.toJson(toJsonTree())

    /** Returns the graph as a JSON tree. */
    fun toJsonTree(): JsonObject {
        val root = JsonObject()
        root.addProperty("version", VERSION)
        val array = JsonArray()
        for (node in nodes) {
            val o = JsonObject()
            o.addProperty("id", node.id)
            o.addProperty("type", node.type)
            if (node.params.isNotEmpty()) {
                val params = JsonObject()
                for ((name, value) in node.params) params.add(name, value)
                o.add("params", params)
            }
            if (node.inputs.isNotEmpty()) {
                val inputs = JsonArray()
                for (input in node.inputs) {
                    if (input == null) inputs.add(JsonNull.INSTANCE) else inputs.add(input)
                }
                o.add("in", inputs)
            }
            o.addProperty("x", node.x)
            o.addProperty("y", node.y)
            array.add(o)
        }
        root.add("nodes", array)
        return root
    }

    companion object {
        /** The version of the file format this library writes. */
        const val VERSION = 1

        private val PRETTY = GsonBuilder().setPrettyPrinting().create()

        /**
         * Reads a graph from JSON text.
         *
         * @param json the text of a graph file.
         * @throws GraphException if the text is not a graph this library can read.
         */
        @JvmStatic
        fun fromJson(json: String): GraphDef {
            val root =
                try {
                    JsonParser().parse(json)
                } catch (e: RuntimeException) {
                    throw GraphException("Not valid JSON: ${e.message}")
                }
            if (root !is JsonObject) throw GraphException("A graph must be a JSON object")
            val version = root.get("version")?.takeIf { it.isNumber() }?.asInt ?: VERSION
            if (version > VERSION) {
                throw GraphException("Graph version $version is newer than this library reads")
            }
            val array =
                root.get("nodes") as? JsonArray ?: throw GraphException("A graph needs \"nodes\"")
            return GraphDef(array.map { readNode(it) })
        }

        private fun readNode(element: JsonElement): NodeDef {
            val o = element as? JsonObject ?: throw GraphException("A node must be a JSON object")
            val id = o.get("id")?.takeIf { it.isString() }?.asString
            if (id.isNullOrBlank()) throw GraphException("A node needs an \"id\"")
            val type =
                o.get("type")?.takeIf { it.isString() }?.asString
                    ?: throw GraphException("Node needs a \"type\"", id)
            val params = LinkedHashMap<String, JsonElement>()
            (o.get("params") as? JsonObject)?.entrySet()?.forEach { params[it.key] = it.value }
            val inputs =
                (o.get("in") as? JsonArray)?.map { input ->
                    if (input.isString()) input.asString.takeIf { it.isNotEmpty() } else null
                } ?: emptyList()
            val x = o.get("x")?.takeIf { it.isNumber() }?.asDouble ?: 0.0
            val y = o.get("y")?.takeIf { it.isNumber() }?.asDouble ?: 0.0
            return NodeDef(id, type, params, inputs, x, y)
        }
    }
}

internal fun JsonElement.isNumber(): Boolean = isJsonPrimitive && asJsonPrimitive.isNumber

internal fun JsonElement.isString(): Boolean = isJsonPrimitive && asJsonPrimitive.isString
