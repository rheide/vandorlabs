package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.screen.ScreenRenderPose;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Carried mode controls drawn on the same surface and pixel bounds hit-tested by the pilot. */
public final class NativeVehicleStatusRenderer {
    private record Face(Matrix4f local, int height) {}
    private record Reading(int mode, int y, int clearance, int speed, int fuel, int ceiling, boolean available) {}
    private record Labels(Reading state, NativeOverlayCanvas.Mesh glyphs) {}
    private final NativeOverlayCanvas canvas;
    private final Map<NativeVehicleStatus.Panel, Face> faces = new WeakHashMap<>();
    private final Map<NativeGroundVehicle, Labels> readings = new WeakHashMap<>();
    private final Matrix4f matrix = new Matrix4f();

    public NativeVehicleStatusRenderer(Font font) {
        canvas = new NativeOverlayCanvas(font);
    }

    private static Face face(NativeVehicleStatus.Panel panel) {
        double[] values = new double[16];
        ScreenRenderPose.write(ScreenFamily.SCREEN, new ScreenFamily.State(panel.facing(), false, false, false), false, -1, values);
        float[] floats = new float[16];
        for (int index = 0; index < floats.length; index++) floats[index] = (float)values[index];
        var ui = new Matrix4f();
        NativeOverlayCanvas.uiMatrix(panel.projection().surface, NativeVehicleStatus.WIDTH, panel.projection().height, ui);
        return new Face(new Matrix4f().set(floats).mul(ui), panel.projection().height);
    }

    public void render(NativeGroundVehicle craft, PoseStack poses, MultiBufferSource buffers) {
        if (craft.statusPanels().isEmpty()) return;
        var thrust = craft.flightReadout();
        var reading = new Reading(craft.mode(), (int)Math.floor(craft.getY()), Math.round(craft.groundClearance()),
                Math.round(Math.abs(craft.driveSpeed()) * 20), thrust.fuel(), (int)thrust.ceiling(),
                FlightEnvelope.canFly(thrust.effectiveMass(), thrust.thrust()));
        var labels = readings.get(craft);
        if (labels == null || !labels.state.equals(reading)) {
            labels = new Labels(reading, canvas.build(text -> {
                text.plain("VEHICLE STATUS", 8, 4, 0xe1efff);
                for (int mode = 0; mode < NativeVehicleStatus.MODES.size(); mode++) {
                    text.centered(NativeVehicleStatus.MODES.get(mode), NativeVehicleStatus.buttonLeft(mode)
                            + NativeVehicleStatus.BUTTON_WIDTH / 2, NativeVehicleStatus.BUTTON_START + 2, 0xffffff);
                }
                text.plain("Y " + reading.y + "   " + reading.speed + " blocks/s", 8, 33, 0xc5daf0);
                String ground = reading.clearance >= 0 ? "Ground " + reading.clearance : "";
                text.plain(ground, 8, 43, 0xc5daf0);
                text.plain(reading.available ? "Fuel " + reading.fuel + " mB" : "Flight unavailable", 8, 53, 0xf2cf8a);
            }));
            readings.put(craft, labels);
        }
        for (var panel : craft.statusPanels()) {
            var face = faces.computeIfAbsent(panel, NativeVehicleStatusRenderer::face);
            var at = panel.pos();
            matrix.set(poses.last().pose()).translate(at.getX(), at.getY(), at.getZ()).mul(face.local);
            var solid = NativeOverlayCanvas.solid(buffers);
            NativeOverlayCanvas.rectangle(solid, matrix, 0, 0, 128, face.height, 0, 0xff17252f);
            for (int mode = 0; mode < NativeVehicleStatus.MODES.size(); mode++) {
                int left = NativeVehicleStatus.buttonLeft(mode);
                int color = mode == reading.mode ? 0xff208675 : 0xff405663;
                NativeOverlayCanvas.rectangle(solid, matrix, left, NativeVehicleStatus.BUTTON_START,
                        left + NativeVehicleStatus.BUTTON_WIDTH, NativeVehicleStatus.BUTTON_END, .01, color);
            }
            NativeOverlayCanvas.draw(labels.glyphs, matrix, buffers, LightTexture.FULL_BRIGHT);
        }
    }
}
