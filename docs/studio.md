---
title: The studio
nav_order: 3
---

# The studio

The studio is a page served by the robot, on which a pipeline is built and tuned from a laptop.

It runs only while an OpMode that started it is running. A robot build without the
`wolfvision-studio` dependency does not contain it.

## A tuning OpMode

Add this OpMode to the robot project. It runs the pipeline `samples` with the studio on.

```java
@TeleOp(name = "WolfVision Tuning", group = "Tuning")
public class WolfVisionTuning extends LinearOpMode {
    @Override
    public void runOpMode() {
        WolfVisionProcessor vision = WolfVisionProcessor.load("samples");
        VisionPortal portal =
                new VisionPortal.Builder()
                        .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
                        .addProcessor(vision)
                        .build();
        Studio studio = Studio.start(vision.getVision());

        try {
            // The studio works from here on: there is no need to press start.
            while (!isStopRequested()) {
                telemetry.addLine("Studio: http://192.168.43.1:" + studio.getPort() + "/");
                Detection target = vision.detection("target");
                if (target == null) {
                    telemetry.addLine("target: nothing found");
                } else {
                    telemetry.addData("target", "x %.0f  y %.0f", target.getX(), target.getY());
                }
                telemetry.update();
                sleep(50);
            }
        } finally {
            studio.stop();
            portal.close();
            vision.close();
        }
    }
}
```

## Open it

1. Connect the laptop to the robot's Wi-Fi network.
2. On the Driver Station, select **WolfVision Tuning** and press **INIT**.
3. In a browser on the laptop, open <http://192.168.43.1:5800/>.

The studio stops when the OpMode is stopped.

## The page

| Part              | What it is                                                              |
| ----------------- | ----------------------------------------------------------------------- |
| Left              | The node types. Click one to add it to the pipeline.                    |
| Middle            | The pipeline. Every node shows the picture it produces, its settings and the time it takes per frame. |
| Top               | The size of the picture, frames per second, and the time the whole pipeline takes per frame. |
| **Preview**       | Shows or hides a panel with a large picture of the selected node and the values of every output. |

## Build a pipeline

- **Add a node:** click its type on the left.
- **Move a node:** drag it by its title.
- **Connect two nodes:** drag from the dot on the right of one node to a dot on the left of
  another. A connection is coloured by the kind of value it carries.
- **Remove a connection:** click the dot it ends at.
- **Delete a node:** click the × in its title, or select it and press Delete.
- **Zoom:** pinch, or hold Ctrl or Cmd and scroll. The buttons at the top do the same.

A node whose inputs are not all connected is shown faded and does nothing, so a pipeline keeps
running while it is being rewired.

Every change is sent to the robot at once and takes effect on the next frame. If a change cannot
work, such as connecting contours to an input that takes an image, a red bar says why and the
robot keeps running the last pipeline that worked.

To give robot code a value, connect it to an **Output** node and give the output a name. See the
[node reference](nodes.md) for what each node does.

## Tune a colour

1. Select the **Threshold** node.
2. Press **Pick from image**. The preview panel opens and shows the picture the node is fed.
3. Click the object in the preview. The range is set around the colour that was clicked.
4. Shift-click other parts of the object, such as a part in shadow, to widen the range to include
   them.
5. Adjust the sliders by hand if needed. They are named after what they limit, such as Hue.

Tune under the lighting the robot will compete in. A range tuned in a workshop often fails in a
gym.

## Save

**Save** writes the pipeline to the robot. The next `load` of the same name, in any OpMode, gets
the saved pipeline.

A change that has not been saved is lost when the OpMode stops.

**Download** gives the pipeline as a file, and **Open…** loads one. Use them to keep pipelines in
git: see [Pipeline files](files.md).

## How much it costs

While the studio is on, the robot copies and compresses a picture only for a node that a browser
is showing. With the **Thumbnails** box cleared and the preview panel hidden, it does almost no
extra work.

The time shown at the top is the time the pipeline itself takes. Keep it well below the time
between frames: 33 ms at 30 frames per second.
