/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

package com.robowolves.wolfvision.samples;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.robowolves.wolfvision.Detection;
import com.robowolves.wolfvision.ftc.WolfVisionProcessor;
import com.robowolves.wolfvision.studio.Studio;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.vision.VisionPortal;

/**
 * Runs a pipeline with the studio on, for building and tuning it from a laptop. The pages of the
 * documentation show this OpMode; it is compiled here so that they stay correct.
 */
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
