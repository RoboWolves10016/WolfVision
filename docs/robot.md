---
title: On a robot
nav_order: 2
---

# On a robot

## Run a pipeline

`WolfVisionProcessor` is a `VisionProcessor`, so it is added to a `VisionPortal` in the same way
as the SDK's AprilTag processor, and can run beside it.

```java
@TeleOp(name = "WolfVision Example")
public class WolfVisionExample extends LinearOpMode {
    @Override
    public void runOpMode() {
        WolfVisionProcessor vision = WolfVisionProcessor.load("samples");
        // Fails here, before the match, if the pipeline has no output named "target".
        vision.require("target");
        VisionPortal portal =
                new VisionPortal.Builder()
                        .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
                        .addProcessor(vision)
                        .build();

        waitForStart();
        while (opModeIsActive()) {
            Detection target = vision.detection("target");
            if (target != null) {
                // How far the target is from the middle of the picture, from -1 at the left edge
                // to 1 at the right edge.
                double offset = target.getX() / vision.getResults().getFrameWidth() * 2.0 - 1.0;
                telemetry.addData("target", "offset %.2f  area %.0f", offset, target.getArea());
            } else {
                telemetry.addLine("target: nothing found");
            }
            telemetry.update();
        }

        portal.close();
        vision.close();
    }
}
```

Change `"Webcam 1"` to the name of the camera in the robot configuration.

`load("samples")` loads the pipeline named `samples`. The first time, when no such pipeline exists
yet, it loads a starter pipeline that finds the largest yellow object as the output `target`. See
[Pipeline files](files.md) for where pipelines are kept.

The pipeline runs on the camera's own thread, not in the OpMode loop. The loop only reads the
latest results, which takes no time.

## Read the outputs

Outputs are named by the Output nodes of the pipeline.

| Call                       | Gives                                                        |
| -------------------------- | ------------------------------------------------------------ |
| `vision.detection(name)`   | The first box of a detection output, or `null` if there is none. |
| `vision.detections(name)`  | Every box of a detection output; empty if there are none.    |
| `vision.number(name)`      | A number output; `NaN` if there is none.                     |
| `vision.getResults()`      | Every output of one frame together, with the size of the frame and the time it was captured. |
| `vision.require(name)`     | Nothing; it throws if the pipeline has no output of that name. |

A `Detection` is a box around an object:

| Property              | Meaning                                                             |
| --------------------- | ------------------------------------------------------------------- |
| `getX()`, `getY()`    | The centre of the box, in pixels from the top left of the picture.  |
| `getWidth()`, `getHeight()` | The size of the box, in pixels.                               |
| `getAngle()`          | The rotation of the box in degrees; zero for an upright box.        |
| `getArea()`           | The area of the object, in square pixels.                           |

Positions are in pixels of the camera picture. WolfVision does not yet turn them into angles or
field positions.

Call `require` for every output the OpMode reads, straight after `load`. If someone renames an
output in the studio, the OpMode then stops with a clear message when it is started, instead of
quietly finding nothing during a match.

## What the Driver Station shows

The camera preview on the Driver Station, and on a monitor plugged into the Control Hub, shows a
green box around every detection the pipeline outputs.

## Tune it

The OpMode above does not start the studio, so nothing can change its pipeline while it runs. To
build or tune a pipeline, use a separate OpMode that starts the studio: see
[The studio](studio.md).
