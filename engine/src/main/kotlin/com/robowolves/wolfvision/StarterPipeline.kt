/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision

/** The pipeline a new graph file starts as: it finds the largest yellow object. */
object StarterPipeline {
    /** The graph, as JSON. Its one output is named "target". */
    const val JSON: String =
        """{ "version": 1, "nodes": [
  { "id": "camera", "type": "Camera", "x": 20, "y": 40 },
  { "id": "hsv", "type": "ConvertColor", "params": { "to": "HSV" }, "in": ["camera"],
    "x": 260, "y": 40 },
  { "id": "mask", "type": "Threshold",
    "params": { "min": [20, 100, 100], "max": [35, 255, 255] }, "in": ["hsv"], "x": 500, "y": 40 },
  { "id": "clean", "type": "Morphology", "params": { "op": "open", "size": 5 }, "in": ["mask"],
    "x": 740, "y": 40 },
  { "id": "blobs", "type": "FindContours", "in": ["clean"], "x": 20, "y": 560 },
  { "id": "best", "type": "Largest", "in": ["blobs"], "x": 260, "y": 560 },
  { "id": "box", "type": "Boxes", "params": { "kind": "rotated" }, "in": ["best"],
    "x": 500, "y": 560 },
  { "id": "target", "type": "Output", "params": { "name": "target" }, "in": ["box"],
    "x": 740, "y": 560 }
] }
"""
}
