---
title: Pipeline files
nav_order: 6
---

# Pipeline files

A pipeline is one JSON file. The same file works on a robot and on a laptop.

## Where a robot looks

`WolfVisionProcessor.load("samples")` takes the pipeline from the first of these that exists:

1. `/sdcard/FIRST/wolfvision/samples.json` in the storage of the robot. This is what **Save** in
   the studio writes.
2. `wolfvision/samples.json` in the assets of the robot app, that is,
   `TeamCode/src/main/assets/wolfvision/samples.json` in the robot project.
3. A starter pipeline, which finds the largest yellow object as the output `target`.

A saved pipeline on the robot therefore overrides the one built into the app.

A pipeline name may hold letters, digits, `-` and `_`.

## Keep pipelines in git

A pipeline that exists only in the storage of one robot is lost with that robot, and nobody can
see how it changed. Once a pipeline works:

1. Press **Download** in the studio.
2. Put the file in `TeamCode/src/main/assets/wolfvision/` of the robot project, named after the
   pipeline.
3. Commit it.

Every build of the robot app then carries the pipeline.

After that, the file saved on the robot still wins. To go back to the one in the app, delete the
saved file, for example from a computer connected to the robot:

```
adb shell rm /sdcard/FIRST/wolfvision/samples.json
```

## The format

```json
{ "version": 1, "nodes": [
  { "id": "camera", "type": "Camera", "x": 20, "y": 40 },
  { "id": "hsv", "type": "ConvertColor", "params": { "to": "HSV" }, "in": ["camera"],
    "x": 260, "y": 40 },
  { "id": "mask", "type": "Threshold",
    "params": { "min": [20, 100, 100], "max": [35, 255, 255] }, "in": ["hsv"],
    "x": 500, "y": 40 },
  { "id": "target", "type": "Output", "params": { "name": "target" }, "in": ["mask"],
    "x": 740, "y": 40 }
] }
```

| Field    | Meaning                                                                          |
| -------- | -------------------------------------------------------------------------------- |
| `id`     | The name of the node, unique in the file.                                        |
| `type`   | The node type, as in the [node reference](nodes.md).                             |
| `params` | The settings of the node. A setting left out takes its default.                  |
| `in`     | The `id` of the node feeding each input, in order. `null` for an input that is not connected. |
| `x`, `y` | Where the node sits in the studio.                                               |

A file can be edited by hand. A file that cannot run, for example one that names a node type that
does not exist, makes `load` throw with a message that names the node at fault.
