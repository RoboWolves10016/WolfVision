---
title: On a laptop
nav_order: 5
---

# On a laptop

The studio runs on a laptop with no robot, against a webcam, saved pictures or a built-in demo.
A pipeline built this way is the same file a robot loads.

## Run it

In a copy of the WolfVision repository:

```
./gradlew :desktop:installDist
desktop/build.nosync/install/desktop/bin/desktop pipeline.json camera:0
```

Then open <http://localhost:5800/>.

The first argument is the pipeline file. If it does not exist, it is created with a starter
pipeline. **Save** in the studio writes to it.

The second argument is where the pictures come from:

| Source            | Pictures                                                        |
| ----------------- | --------------------------------------------------------------- |
| `demo`            | Moving coloured shapes. This is the default.                    |
| `camera:0`        | A camera of the laptop. Try `camera:1` if the wrong one opens.  |
| a picture file    | That one picture.                                               |
| a folder          | Each `.png` and `.jpg` in the folder in turn, three seconds each. |

A third argument sets the port, which is 5800 otherwise.

On macOS, the terminal asks to use the camera the first time. If the camera cannot be opened,
allow the terminal under System Settings → Privacy & Security → Camera and start it again.

## Tune against real pictures

Take pictures with the robot's camera at the venue, put them in a folder, and give the folder as
the source. The pipeline can then be tuned against real lighting without the robot.

## Move a pipeline to the robot

Press **Download** in the studio on the laptop, then **Open…** and **Save** in the studio on the
robot. See [Pipeline files](files.md) for the other ways.
