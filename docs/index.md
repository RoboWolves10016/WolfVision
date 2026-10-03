---
title: Overview
nav_order: 1
---

# WolfVision

WolfVision is a vision library for FTC robots in which a pipeline is built, not written.

A pipeline is a chain of nodes: convert the colour, keep a colour range, tidy the result, find the
objects, box them. It is stored as a small file on the robot. The library runs it on every camera
frame and gives robot code the results by name.

WolfVision Studio is a page served by the robot for building and tuning a pipeline from a laptop.
Every node shows the picture it produces, and a change takes effect on the next frame, with no
rebuild.

| Part                | What it is for                                                         |
| ------------------- | ---------------------------------------------------------------------- |
| `wolfvision-ftc`    | Runs a pipeline in a `VisionPortal`. Every robot build needs it.       |
| `wolfvision-studio` | The studio. Only an OpMode that starts it runs it; leave it out of a build and it is not there at all. |

**Status:** version 0.1.0 is built against FTC SDK 12.0.0 and has been run on a laptop. It has not
yet been validated on a Control Hub.

## Install

WolfVision is not on Maven Central yet. Build it into the local Maven repository of the computer
that builds the robot app:

```
git clone https://github.com/RoboWolves10016/WolfVision.git
cd WolfVision
./gradlew publishToMavenLocal
```

Then, in `build.dependencies.gradle` of the robot project:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.github.robowolves10016:wolfvision-ftc:0.1.0'
    implementation 'io.github.robowolves10016:wolfvision-studio:0.1.0' // only for tuning
}
```

The robot project needs the FTC SDK's `Vision` module, which the standard project already has. A
robot project written in Kotlin needs Kotlin 2.1 or newer.

## Where to go next

1. [On a robot](robot.md): run a pipeline in an OpMode and read its outputs.
2. [The studio](studio.md): build and tune a pipeline from a laptop.
3. [Node reference](nodes.md): what each node does, with example pictures.
4. [On a laptop](laptop.md): try the studio with a webcam and no robot.
5. [Pipeline files](files.md): where pipelines are kept and how to get them into git.
