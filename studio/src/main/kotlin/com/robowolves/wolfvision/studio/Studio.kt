/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.studio

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.robowolves.wolfvision.GraphDef
import com.robowolves.wolfvision.GraphException
import com.robowolves.wolfvision.NodeRegistry
import com.robowolves.wolfvision.ParamKind
import com.robowolves.wolfvision.WolfVision
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * The tuning dashboard of a pipeline: a web server with a page on which the graph is edited and the
 * output of every node is watched.
 *
 * It runs only between [start] and [stop]. While it runs, an image is copied only for a node that
 * a browser is watching, so a studio with no browser open costs the pipeline almost nothing.
 *
 * ```
 * Studio studio = Studio.start(vision);
 * ...
 * studio.stop();
 * ```
 */
class Studio
private constructor(
    private val vision: WolfVision,
    private val registry: NodeRegistry,
    port: Int,
) {
    private val previews = Previews()
    private val server = Server(port)

    /** The port the studio listens on. */
    val port: Int
        get() = server.listeningPort

    /** Stops the server and detaches the studio from the pipeline. */
    fun stop() {
        server.stop()
        if (vision.tap === previews) vision.tap = null
        previews.release()
    }

    private inner class Server(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response =
            try {
                route(session)
            } catch (e: GraphException) {
                val o = JsonObject()
                o.addProperty("error", e.message)
                o.addProperty("node", e.nodeId)
                json(Response.Status.BAD_REQUEST, o)
            } catch (e: Exception) {
                val o = JsonObject()
                o.addProperty("error", e.message ?: e.javaClass.simpleName)
                json(Response.Status.INTERNAL_ERROR, o)
            }

        private fun route(session: IHTTPSession): Response {
            val path = session.uri
            val query = session.parms
            return when {
                path == "/api/nodes" -> json(Response.Status.OK, nodeTypes())
                path == "/api/graph" && session.method == Method.GET ->
                    json(Response.Status.OK, vision.graph.toJsonTree())
                path == "/api/graph" -> {
                    vision.setGraph(GraphDef.fromJson(body(session)))
                    ok()
                }
                path == "/api/save" && session.method == Method.POST -> {
                    if (vision.file == null) throw GraphException("The pipeline has no file")
                    vision.save()
                    ok()
                }
                path == "/api/status" -> json(Response.Status.OK, status())
                path == "/api/frame" -> {
                    val width = query["w"]?.toIntOrNull()?.coerceIn(16, 4096) ?: 320
                    val jpeg = previews.jpeg(query["node"] ?: "", width)
                    if (jpeg == null) {
                        newFixedLengthResponse(Response.Status.NO_CONTENT, "image/jpeg", "")
                    } else {
                        val response =
                            newFixedLengthResponse(
                                Response.Status.OK,
                                "image/jpeg",
                                ByteArrayInputStream(jpeg),
                                jpeg.size.toLong(),
                            )
                        response.addHeader("Cache-Control", "no-store")
                        response
                    }
                }
                path == "/api/sample" -> {
                    val sample =
                        previews.sample(
                            query["node"] ?: "",
                            query["x"]?.toDoubleOrNull() ?: 0.5,
                            query["y"]?.toDoubleOrNull() ?: 0.5,
                            query["r"]?.toIntOrNull()?.coerceIn(0, 50) ?: 4,
                        ) ?: throw GraphException("No image to sample")
                    json(Response.Status.OK, sample)
                }
                else -> page(if (path == "/") "index.html" else path.removePrefix("/"))
            }
        }

        private fun body(session: IHTTPSession): String {
            val length = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (length > MAX_BODY) throw GraphException("The graph is too large")
            val bytes = ByteArray(length)
            DataInputStream(session.inputStream).readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun page(name: String): Response {
            val stream =
                if (SAFE_NAME.matches(name)) {
                    Studio::class.java.getResourceAsStream("page/$name")
                } else {
                    null
                }
            if (stream == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            val bytes = stream.use { it.readBytes() }
            val mime =
                when (name.substringAfterLast('.')) {
                    "html" -> "text/html; charset=utf-8"
                    "js" -> "text/javascript; charset=utf-8"
                    "css" -> "text/css; charset=utf-8"
                    "svg" -> "image/svg+xml"
                    else -> "application/octet-stream"
                }
            val response =
                newFixedLengthResponse(
                    Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
            response.addHeader("Cache-Control", "no-cache")
            return response
        }

        private fun ok(): Response {
            val o = JsonObject()
            o.addProperty("ok", true)
            return json(Response.Status.OK, o)
        }

        private fun json(status: Response.Status, body: Any): Response {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            val response =
                newFixedLengthResponse(
                    status,
                    "application/json; charset=utf-8",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                )
            response.addHeader("Cache-Control", "no-store")
            return response
        }
    }

    private fun nodeTypes(): JsonArray {
        val array = JsonArray()
        for (spec in registry.all()) {
            val o = JsonObject()
            o.addProperty("type", spec.type)
            o.addProperty("category", spec.category)
            o.addProperty("description", spec.description)
            o.addProperty("terminal", spec.terminal)
            o.addProperty("output", spec.output?.name)
            val inputs = JsonArray()
            for (input in spec.inputs) {
                val i = JsonObject()
                i.addProperty("name", input.name)
                i.addProperty("type", input.type?.name)
                inputs.add(i)
            }
            o.add("inputs", inputs)
            val params = JsonArray()
            for (param in spec.params) {
                val p = JsonObject()
                p.addProperty("name", param.name)
                p.addProperty("kind", param.kind.name)
                p.addProperty("min", param.min)
                p.addProperty("max", param.max)
                when (param.kind) {
                    ParamKind.TRIPLE -> {
                        val d = JsonArray()
                        @Suppress("UNCHECKED_CAST")
                        for (v in param.default as List<Double>) d.add(v)
                        p.add("default", d)
                    }
                    ParamKind.CHOICE,
                    ParamKind.TEXT -> p.addProperty("default", param.default as String)
                    else -> p.addProperty("default", param.default as Number)
                }
                val choices = JsonArray()
                for (choice in param.choices) choices.add(choice)
                p.add("choices", choices)
                params.add(p)
            }
            o.add("params", params)
            array.add(o)
        }
        return array
    }

    private fun status(): JsonObject {
        val o = JsonObject()
        val results = vision.results
        o.addProperty("fps", vision.fps)
        o.addProperty("width", results.frameWidth)
        o.addProperty("height", results.frameHeight)
        o.addProperty("file", vision.file?.name)
        vision.error?.let {
            val e = JsonObject()
            e.addProperty("message", it.message)
            e.addProperty("node", it.nodeId)
            o.add("error", e)
        }
        val nodes = JsonObject()
        for (node in vision.status()) {
            val n = JsonObject()
            n.addProperty("type", node.type?.name)
            n.addProperty("ms", node.millis)
            previews.numbers[node.id]?.let { if (!it.isNaN()) n.addProperty("value", it) }
            nodes.add(node.id, n)
        }
        o.add("nodes", nodes)
        val numbers = JsonObject()
        for ((name, value) in results.numbers) {
            if (value.isNaN()) numbers.add(name, null) else numbers.addProperty(name, value)
        }
        o.add("numbers", numbers)
        val detections = JsonObject()
        for ((name, list) in results.detections) {
            val array = JsonArray()
            for (d in list) {
                val j = JsonObject()
                j.addProperty("x", d.x)
                j.addProperty("y", d.y)
                j.addProperty("width", d.width)
                j.addProperty("height", d.height)
                j.addProperty("angle", d.angle)
                j.addProperty("area", d.area)
                array.add(j)
            }
            detections.add(name, array)
        }
        o.add("detections", detections)
        return o
    }

    companion object {
        /** The port the studio listens on unless another is given. */
        const val DEFAULT_PORT = 5800

        private const val MAX_BODY = 1 shl 20
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

        /**
         * Starts the studio of a pipeline.
         *
         * @param vision the pipeline to edit and watch.
         * @param port the port to listen on.
         * @param registry the node types the editor offers; the same as the pipeline was loaded
         *   with.
         * @return the running studio.
         * @throws java.io.IOException if the port cannot be opened.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            vision: WolfVision,
            port: Int = DEFAULT_PORT,
            registry: NodeRegistry = NodeRegistry.standard(),
        ): Studio {
            val studio = Studio(vision, registry, port)
            studio.server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            vision.tap = studio.previews
            return studio
        }
    }
}
