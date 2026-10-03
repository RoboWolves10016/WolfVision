# WolfVision

Vision pipelines for FTC robots, built without writing code.

A pipeline is a graph of nodes stored in a JSON file. Each node is one OpenCV step: convert colour,
threshold, clean up a mask, find contours, box them. The library runs the graph on every camera
frame and gives robot code the results by name.

WolfVision Studio is an optional dashboard for building and tuning a graph in a browser. Every
node shows the image it produces, so each layer of the pipeline is visible, and a change takes
effect on the next frame. The studio runs only when it is started; a robot that does not start it
runs the pipeline alone.

## Modules

| Module    | Contents                                                           |
| --------- | ------------------------------------------------------------------ |
| `engine`  | Graphs, nodes and the runtime. Kotlin and OpenCV, no Android code. |
| `studio`  | The dashboard: a small web server and the editor page.             |
| `desktop` | Runs a pipeline and the studio on a laptop. Not published.         |

## Try it on a laptop

```
./gradlew :desktop:run
```

Then open <http://localhost:5800/>. The pipeline is saved in `desktop/data.nosync/pipeline.json`.

The source of frames is the second argument:

```
./gradlew :desktop:installDist
desktop/build.nosync/install/desktop/bin/desktop pipeline.json demo        # moving shapes
desktop/build.nosync/install/desktop/bin/desktop pipeline.json camera:0    # a webcam
desktop/build.nosync/install/desktop/bin/desktop pipeline.json photos/     # a folder of images
```

## Using a pipeline

```java
WolfVision vision = WolfVision.load(new File("pipeline.json"));
vision.require("target"); // fails here if the graph has no output named "target"

// For every camera frame, in RGB:
vision.process(frame);

Detection target = vision.detection("target");
if (target != null) {
    double x = target.getX();
}
```

Outputs are named by the Output nodes of the graph. A number is read with `number(name)`;
anything else is read with `detection(name)` or `detections(name)`.

What each node does, with example images, is in the [node reference](docs/nodes.md).

## Starting the studio

```java
Studio studio = Studio.start(vision); // port 5800
...
studio.stop();
```

The studio copies an image only for a node that a browser is watching.

## Status

The engine, the studio and the desktop runner work. The Android module that plugs a pipeline into
the FTC SDK's `VisionPortal` is not written yet, and nothing has run on a Control Hub.

## Licence

MIT. See [LICENSE](LICENSE).
