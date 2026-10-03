/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.samples;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.robowolves.wolfvision.Detection;
import com.robowolves.wolfvision.ftc.WolfVisionProcessor;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.vision.VisionPortal;

/**
 * Runs a pipeline as a match OpMode does: with no studio. The pages of the documentation show this
 * OpMode; it is compiled here so that they stay correct.
 */
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
