# WolfVision

Vision pipelines for FTC robots, built without writing code.

A pipeline is a graph of nodes stored in a JSON file. Each node is one OpenCV step: convert colour,
threshold, clean up a mask, find contours, box them. The library runs the graph on every camera
frame and gives robot code the results by name.

WolfVision Studio is an optional dashboard for building and tuning a graph in a browser. Every
node shows the image it produces, so each layer of the pipeline is visible, and a change takes
effect on the next frame. The studio runs only when it is started; a robot that does not start it
runs the pipeline alone.

Documentation: <https://robowolves10016.github.io/WolfVision/>

## Modules

| Module    | Contents                                                           |
| --------- | ------------------------------------------------------------------ |
| `engine`  | Graphs, nodes and the runtime. Kotlin and OpenCV, no Android code. |
| `ftc`     | Android library for robot projects: a `VisionProcessor`.           |
| `studio`  | The dashboard: a small web server and the editor page.             |
| `desktop` | Runs a pipeline and the studio on a laptop. Not published.         |

## Try it on a laptop

```
./gradlew :desktop:run
```

Then open <http://localhost:5800/>. See [On a laptop](docs/laptop.md) for using a webcam.

## Use it on a robot

```
./gradlew publishToMavenLocal
```

Then add `io.github.robowolves10016:wolfvision-ftc:0.1.0` to the robot project. See
[On a robot](docs/robot.md) and [The studio](docs/studio.md).

## Building

The `ftc` module needs the Android SDK: set `sdk.dir` in `local.properties` or `ANDROID_HOME`.

## Status

Version 0.1.0 is built against FTC SDK 12.0.0 and has been run on a laptop. It has not yet been
validated on a Control Hub.

## Licence

MIT. See [LICENSE](LICENSE).
