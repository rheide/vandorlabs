package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.screen.NativeScreenRenderPlan;
import com.vandorlabs.neoforge.screen.NativeScreenControls;
import com.vandorlabs.neoforge.screen.NativeScreenBlock;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.screen.NativeScreenRenderProjection;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.screen.ScreenRenderPose;
import net.minecraft.core.BlockPos;
import com.vandorlabs.render.ScreenSurface;
import org.joml.Matrix4fc;
import com.vandorlabs.redstone.ChannelList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Carried mode controls drawn on the same surface and pixel bounds hit-tested by the pilot. */
public final class NativeVehicleStatusRenderer {
    private record Face(Matrix4f local, int height) {}
    private record Reading(int mode, int y, int clearance, int speed, int fuel, boolean available, boolean high, int x, int z, boolean emergency) {}
    private record Labels(Reading state, NativeOverlayCanvas.Mesh glyphs) {}
    private final NativeOverlayCanvas canvas;
    private final NativeScreenRowsRenderer rows;
    private record ScreenRows(NativeScreenEntity.Visual source,int primary,int secondary,NativeScreenEntity.Visual shown){}
    private final Map<NativeScreenEntity,ScreenRows> liveRows=new WeakHashMap<>();
    private NativeScreenEntity.Visual screenRows(NativeGroundVehicle craft,BlockPos local,NativeScreenEntity tile){
        var visual=tile.visual();
        if(!visual.controls().get(0).enabled()&&!visual.controls().get(1).enabled())return visual;
        int first=craft.meterSurface(local,0),second=craft.meterSurface(local,1);
        var saved=liveRows.get(tile);
        if(saved!=null&&saved.source==visual&&saved.primary==first&&saved.secondary==second)return saved.shown;
        var shown=new NativeScreenEntity.Visual(visual.family(),visual.settings(),visual.frames(),visual.glass(),
            List.of(withMeters(visual.controls().get(0),first),withMeters(visual.controls().get(1),second)),
            visual.effectivePower(),visual.physicalPower(),visual.inert(),visual.geometryRevision(),visual.appearance(),visual.chip(),visual.chipPage());
        liveRows.put(tile,new ScreenRows(visual,first,second,shown));
        return shown;
    }
    private static NativeScreenControls withMeters(NativeScreenControls surface,int packed){
        if(!surface.enabled())return surface;
        var rows=new ArrayList<>(surface.rows());
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);
            int level = packed >>> (4 * i) & 15;
            if (level == 0) continue;
            rows.set(i, new NativeScreenControls.Row(row.label(), row.channels(), row.slider(), row.pulse(),
                    row.min(), row.max(), level, java.util.Map.of(), ChannelList.EMPTY, !row.status(),
                    row.pulse() ? Long.MAX_VALUE : 0, row.status()));
        }
        return new NativeScreenControls(true,surface.title(),rows);
    }
    private final Map<NativeVehicleStatus.Panel, Face> faces = new WeakHashMap<>();
    private final Map<NativeGroundVehicle, Labels> readings = new WeakHashMap<>();
    private final Matrix4f matrix = new Matrix4f();
    private NativeFlatWallModel.Bake source;
    private NativeScreenRenderer.Bake screens;

    public NativeVehicleStatusRenderer(Font font) {
        canvas = new NativeOverlayCanvas(font);
        rows = new NativeScreenRowsRenderer(font);
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

    public void renderScreen(NativeGroundVehicle craft, BlockPos local, NativeScreenEntity tile, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        var next = NativePanelPlaybackRenderer.currentBake();
        if (next == null || !(tile.getBlockState().getBlock() instanceof NativeScreenBlock block)) return;
        if (source != next) {
            source = next;
            rows.clear();liveRows.clear();
            screens = new NativeScreenRenderer.Bake(next, Minecraft.getInstance().getResourceManager());
        }
        NativeScreenRenderer.renderAt(screens, screenRows(craft,local,tile), block.screenState(tile.getBlockState()),
                NativeScreenRenderProjection.SINGLE, craft.level(), tile.getBlockPos(), poses, buffers, light, overlay,craft.level().getGameTime(),
                this::renderRows);
    }

    private void renderRows(NativeScreenRenderPlan.Binding binding,NativeScreenEntity.Visual visual,
            ScreenFamily.State state,int slot,ScreenSurface.Quad content,Matrix4fc transform,
            PoseStack poses,MultiBufferSource buffers,int light,int overlay,long time){
        if(binding==NativeScreenRenderPlan.Binding.ROWS)
            rows.emit(binding,visual,state,slot,content,transform,poses,buffers,light,overlay,time);
    }

    public void render(NativeGroundVehicle craft, PoseStack poses, MultiBufferSource buffers) {
        if (craft.statusPanels().isEmpty()) return;
        var thrust = craft.flightReadout();
        var reading = new Reading(craft.mode(), (int)Math.floor(craft.flightAltitude()), Math.round(craft.groundClearance()),
                Math.round(Math.abs(craft.driveSpeed()) * 20), thrust.fuel(),
                FlightEnvelope.canFly(thrust.effectiveMass(), thrust.thrust()), craft.highFlight(),
                (int)craft.routeX(), (int)craft.routeZ(), craft.showsEmergencyPark(Minecraft.getInstance().player));
        var labels = readings.get(craft);
        if (labels == null || !labels.state.equals(reading)) {
            labels = new Labels(reading, canvas.build(text -> {
                text.plain("VEHICLE STATUS", 8, 4, 0xe1efff);
                for (int mode = 0; mode < NativeVehicleStatus.MODES.size(); mode++) {
                    text.centered(NativeVehicleStatus.MODES.get(mode), NativeVehicleStatus.buttonLeft(mode)
                            + NativeVehicleStatus.BUTTON_WIDTH / 2, NativeVehicleStatus.BUTTON_START + 2, 0xffffff);
                }
                text.plain("Y " + reading.y + "   " + reading.speed + " blocks/s", 8, 33, 0xc5daf0);
                String location = reading.high ? "X " + reading.x + " Z " + reading.z
                        : reading.clearance >= 0 ? "Ground " + reading.clearance : "";
                text.fitted(location, 8, 43, 112, 0xc5daf0);
                if (reading.emergency) text.centered("Emergency Park", 64, NativeVehicleStatus.EMERGENCY_START + 1, 0xffe5bb);
                else text.plain(reading.available ? "Fuel " + reading.fuel + " mB" : "Flight unavailable", 8, 53, 0xf2cf8a);
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
            if (reading.emergency) {
                NativeOverlayCanvas.rectangle(solid, matrix, 7, NativeVehicleStatus.EMERGENCY_START,
                        121, NativeVehicleStatus.EMERGENCY_END, .01, 0xff77513b);
            }
            NativeOverlayCanvas.draw(labels.glyphs, matrix, buffers, LightTexture.FULL_BRIGHT);
        }
    }
}
