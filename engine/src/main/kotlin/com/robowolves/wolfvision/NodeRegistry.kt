/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

import com.robowolves.wolfvision.nodes.contourNodes
import com.robowolves.wolfvision.nodes.imageNodes
import com.robowolves.wolfvision.nodes.maskNodes
import com.robowolves.wolfvision.nodes.outputNodes

/** The node types a graph may use. */
class NodeRegistry {
    private val specs = LinkedHashMap<String, NodeSpec>()

    /**
     * Adds a node type.
     *
     * @param spec the type.
     * @return this registry.
     * @throws IllegalArgumentException if a type of the same name is registered.
     */
    fun register(spec: NodeSpec): NodeRegistry {
        require(specs.put(spec.type, spec) == null) { "Node type ${spec.type} is registered twice" }
        return this
    }

    /** Returns the node type of a given name, or null if there is none. */
    fun find(type: String): NodeSpec? = specs[type]

    /** Returns every node type, in the order registered. */
    fun all(): List<NodeSpec> = specs.values.toList()

    companion object {
        /** Returns a new registry holding the node types that ship with the library. */
        @JvmStatic
        fun standard(): NodeRegistry {
            val registry = NodeRegistry()
            for (spec in imageNodes() + maskNodes() + contourNodes() + outputNodes()) {
                registry.register(spec)
            }
            return registry
        }
    }
}
