package com.vandorlabs.neoforge.builders.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.drives.DriveControlPolicy;
import com.vandorlabs.drives.DriveShutterPose;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.machines.CoreLayout;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeDriveDesign;
import com.vandorlabs.neoforge.builders.NativeDriveSettings;
import com.vandorlabs.neoforge.client.NativeMaterialPlayback;
import com.vandorlabs.neoforge.propulsion.NativePropulsionParticles;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

final class NativeDriveRenderer {
    private record Surface(float[][] corners, float[] normal) {}
    private record Artwork(List<String> models, Surface[] surfaces) {}
    private record Material(ResourceLocation location, float usedU, float usedV, NativeMaterialPlayback.Material playback) {}
    private static final Map<NativeDriveDesign, Artwork> ARTWORK = loadArtwork();
    private static Map<NativeDriveDesign, Map<String, NativeBuilderModel>> baked = Map.of();
    private static final Map<Integer, Material> MATERIALS = new LinkedHashMap<>(32, .75F, true);
    private record Shutters(UUID identity, DriveShutterPose pose) {}
    private static final Map<Level, LinkedHashMap<BlockPos, Shutters>> SHUTTERS = new WeakHashMap<>();
    static void clear(){SHUTTERS.clear();}
    static double observe(NativeBuilderEntity tile,double time){
        var positions=SHUTTERS.computeIfAbsent(tile.getLevel(),unused->new LinkedHashMap<>(16,.75F,true));
        var entry=positions.get(tile.getBlockPos());
        if(entry==null||!entry.identity.equals(tile.key())){
            entry=new Shutters(tile.key(),new DriveShutterPose());
            positions.put(tile.getBlockPos().immutable(),entry);
            if(positions.size()>256)positions.remove(positions.keySet().iterator().next());
        }
        return entry.pose.sample(tile.formed()&&tile.driveLevel()>0&&tile.driveActivity()==2,time);
    }
    private static final int BRIGHT = 0xf000f0;
    private static final int FUEL_BARS = 8;
    private static final ResourceLocation INLET = ResourceLocation.parse("vandorlabs:blocks/resource_ports/fluid_in_off");
    private static final ResourceLocation GAUGE = ResourceLocation.parse("vandorlabs:blocks/longburn_drive/maxwell_gauge");
    private static final ResourceLocation FUEL_BAR = ResourceLocation.parse("vandorlabs:blocks/longburn_drive/maxwell_gauge_bar");

    static ResourceLocation model(NativeDriveDesign drive, String file) {
        return ResourceLocation.fromNamespaceAndPath("vandorlabs", "block/drives/" + drive.id + "/" + file);
    }

    static List<String> parts(NativeDriveDesign design) { return ARTWORK.get(design).models; }

    static void bake(Map<ModelResourceLocation, BakedModel> models) {
        var next = new EnumMap<NativeDriveDesign, Map<String, NativeBuilderModel>>(NativeDriveDesign.class);
        long bytes = 0;
        for (var drive : NativeDriveDesign.SUPPORTED) {
            var parts = new LinkedHashMap<String, NativeBuilderModel>();
            for (String name : parts(drive)) {
                var state = models.get(ModelResourceLocation.standalone(model(drive, name)));
                if (state == null) throw new IllegalStateException("Missing drive artwork " + drive.id + "/" + name);
                var mesh = new NativeBuilderModel(state);
                bytes += mesh.retainedBytes();
                parts.put(name, mesh);
            }
            next.put(drive, Map.copyOf(parts));
        }
        if (bytes > 8 * 1024 * 1024) throw new IllegalStateException("Drive mesh generation byte budget");
        baked = Map.copyOf(next);
        MATERIALS.clear();
    }

    static void render(NativeBuilderEntity tile, PoseStack poses, MultiBufferSource buffers, int light, int overlay, double time) {
        var drive = tile.drive();
        var mesh = baked.get(drive);
        if (mesh == null) return;
        CoreLayout shape = tile.layout();
        var settings = tile.driveSettings();
        int level = tile.driveLevel();
        boolean active = level > 0;
        poses.pushPose();
        try {
            poses.translate(-shape.pivotX(), 0, -shape.pivotZ());
            NativeBuilderRenderer.orient(poses, tile.facing(), shape.width, shape.depth);
            var out = buffers.getBuffer(NativeBuilderModelCache.LAYER);
            if(tile.maxwell()){
                renderMaxwell(tile, shape, mesh, poses, out, light, overlay, level);
                return;
            }
            if(drive.computer()){
                int mode=active?tile.driveActivity():0;
                String body=switch(mode){case 0->"off";case 1->"idle";default->"computing";};
                mesh.get(body+"/complete").emit(poses.last(),out,light,overlay);
            }else if(drive==NativeDriveDesign.VECTOR_FOLD){
                mesh.get(active?"active/static":"static").emit(poses.last(),out,light,overlay);
            }else if(drive==NativeDriveDesign.OCULUS){
                int activity=active?tile.driveActivity():0;
                double progress=observe(tile,time);
                int appearance=progress>0&&progress<1?1:activity;
                String folder=(appearance==0?"standby":appearance==1?"charging":"active")+"/parts/";
                mesh.get(folder+"static").emit(poses.last(),out,light,overlay);
                double travel=DriveShutterPose.easedTravel(progress);
                poses.pushPose();
                try{
                    poses.translate(0,travel,0);
                    mesh.get(folder+"shutter_upper").emit(poses.last(),out,light,overlay);
                    poses.translate(0,-travel*2,0);
                    mesh.get(folder+"shutter_lower").emit(poses.last(),out,light,overlay);
                }finally{poses.popPose();}
                float brightness=settings.levels()&&appearance!=0?level/15F:1;
                mesh.get(folder+"field").emitTinted(poses.last(),out,appearance==0?light:BRIGHT,overlay,brightness);
            } else if (drive == NativeDriveDesign.MERIDIAN && !active) {
                mesh.get("static").emit(poses.last(), out, light, overlay);
            } else {
                String folder = active ? "active/" : "";
                mesh.get(folder + "drive_body").emit(poses.last(), out, light, overlay);
                float brightness = active ? (settings.levels() ? level / 15F : 1) : .35F;
                if (drive == NativeDriveDesign.MERIDIAN) brightness *= .55F + .45F * (float) (.5 - .5 * Math.cos(time * Math.PI / 20));
                mesh.get(folder + "drive_lights").emitTinted(poses.last(), out, BRIGHT, overlay, brightness);
            }
            var surfaces = ARTWORK.get(drive).surfaces;
            panel(surfaces[0], settings.display(), settings, active, poses, buffers, light, overlay);
            if(surfaces.length>1)panel(surfaces[1], settings.keyboard(), settings, active, poses, buffers, light, overlay);
            var chip = tile.getItem(3);
            if (!chip.isEmpty()) {
                poses.translate(drive.chipX, drive.chipY, drive.chipZ - .07);
                poses.scale((float)drive.chipWidth,(float)drive.chipHeight,.5F);
                poses.mulPose(Axis.YP.rotationDegrees(180));
                var mc = Minecraft.getInstance();
                mc.getItemRenderer().renderStatic(chip, ItemDisplayContext.FIXED, light, overlay, poses, buffers, mc.level, 0);
            }
        } finally {
            poses.popPose();
        }
    }

    private static void renderMaxwell(NativeBuilderEntity tile, CoreLayout shape, Map<String, NativeBuilderModel> mesh,
                                      PoseStack poses, VertexConsumer out, int light, int overlay, int level) {
        float widthScale = shape.width / (float) MaxwellEngine.BASE_WIDTH;
        float depthScale = shape.depth / (float) MaxwellEngine.depth(MaxwellEngine.BASE_WIDTH);
        poses.pushPose();
        try {
            poses.scale(widthScale, widthScale, depthScale);
            mesh.get("maxwell_body").emit(poses.last(), out, light, overlay);
            mesh.get(level == 0 ? "drive_lights" : "active/drive_lights").emitTinted(poses.last(), out,
                    BRIGHT, overlay, level == 0 ? .35F : .4F + level * .04F);
        } finally {
            poses.popPose();
        }
        int x = MaxwellEngine.faceCenter(shape.width), y = MaxwellEngine.faceCenter(shape.height);
        int z = MaxwellEngine.faceCenter(shape.depth);
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var inlet = atlas.apply(INLET);
        var pose = poses.last();
        instrument(pose, out, inlet, Direction.NORTH, x, y, 0, .4375F, -.4375F, .4375F, .001F, light, overlay);
        instrument(pose, out, inlet, Direction.WEST, 0, y, z, .4375F, -.4375F, .4375F, .001F, light, overlay);
        instrument(pose, out, inlet, Direction.EAST, shape.width - 1, y, z, .4375F, -.4375F, .4375F, .001F, light, overlay);
        instrument(pose, out, atlas.apply(GAUGE), Direction.NORTH, x + 1, y, 0, .4375F, -.4375F, .4375F, .001F, light, overlay);
        var bar = atlas.apply(FUEL_BAR);
        int filled = MaxwellEngine.bars(tile.maxwellFuel(), FUEL_BARS);
        for (int i = 0; i < filled; i++) {
            float bottom = (-21 + 6 * i) * .875F / 64;
            instrument(pose, out, bar, Direction.NORTH, x + 1, y, 0, .26F, bottom, bottom + 4 * .875F / 64,
                    .002F, BRIGHT, overlay);
        }
    }

    private static void instrument(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, Direction face,
                                   int x, int y, int z, float halfWidth, float bottom, float top,
                                   float offset, int light, int overlay) {
        float cx = x + .5F + face.getStepX() * (.5F + offset);
        float cy = y + .5F;
        float cz = z + .5F + face.getStepZ() * (.5F + offset);
        var across = face.getCounterClockWise();
        for (int i = 0; i < 4; i++) {
            boolean right = i == 1 || i == 2;
            float horizontal = right ? halfWidth : -halfWidth;
            out.addVertex(pose.pose(), cx + across.getStepX() * horizontal, cy + (i < 2 ? bottom : top),
                    cz + across.getStepZ() * horizontal).setColor(-1)
                    .setUv(right ? sprite.getU1() : sprite.getU0(), i < 2 ? sprite.getV1() : sprite.getV0())
                    .setLight(light).setOverlay(overlay).setNormal(pose, face.getStepX(), 0, face.getStepZ());
        }
    }

    private static void panel(Surface plane, int choice, NativeDriveSettings settings, boolean active,
                              PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (choice == -2 && active) return;
        float u0, u1, v0, v1;
        RenderType layer;
        Material material = material(choice);
        if (material.playback != null && choice >= 0) {
            var playback = material.playback;
            int mode = active ? (settings.animated() ? 2 : 1) : 0;
            int frame = playback.frame(mode, settings.speed(), NativeMaterialPlayback.tick());
            layer = RenderType.entityCutoutNoCull(playback.texture(mode));
            u0 = 0;
            u1 = 1;
            v0 = (float) playback.top(mode, frame);
            v1 = (float) playback.bottom(mode, frame);
        } else {
            var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            var sprite = atlas.apply(active ? material.location : ResourceLocation.withDefaultNamespace("block/black_concrete"));
            layer = NativeBuilderModelCache.LAYER;
            u0 = sprite.getU0();
            u1 = sprite.getU(active ? material.usedU : 1);
            v0 = sprite.getV0();
            v1 = sprite.getV(active ? material.usedV : 1);
        }
        var out = buffers.getBuffer(layer);
        var pose = poses.last();
        for (int i = 0; i < 4; i++) {
            var point = plane.corners[i];
            out.addVertex(pose.pose(), point[0], point[1], point[2]).setColor(-1)
                    .setUv(i == 1 || i == 2 ? u1 : u0, i < 2 ? v1 : v0)
                    .setLight(light).setOverlay(overlay).setNormal(pose, plane.normal[0], plane.normal[1], plane.normal[2]);
        }
    }

    private static Material material(int choice) {
        var cached = MATERIALS.get(choice);
        if (cached != null) return cached;
        if (choice < 0) cached = new Material(ResourceLocation.withDefaultNamespace("block/black_concrete"), 1, 1, null);
        else {
            var entry = ModernFinishCatalog.entry(choice);
            cached = new Material(ResourceLocation.parse(entry.texture()), entry.u(), entry.v(), NativeMaterialPlayback.capture(entry));
        }
        MATERIALS.put(choice, cached);
        if (MATERIALS.size() > 256) MATERIALS.remove(MATERIALS.keySet().iterator().next());
        return cached;
    }

    static void tick(NativeBuilderEntity tile) {
        var drive = tile.drive();
        var settings = tile.driveSettings();
        int level = tile.driveLevel();
        if (!drive.exhaust || !(tile.getLevel() instanceof ClientLevel world)) return;
        if (tile.maxwell() ? level != 15
                : !DriveControlPolicy.plume(level > 0, settings.levels(), settings.particles(), settings.threshold(), level)) return;
        CoreLayout shape = tile.layout();
        var point = NativeBuilderBindings.world(tile, shape.width * .5, shape.height * .55, shape.depth - .05);
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null && camera.distanceToSqr(point) <= 64 * 64) {
            float spread = tile.maxwell() ? .95F * shape.width / MaxwellEngine.BASE_WIDTH : .95F;
            NativePropulsionParticles.drive(world, point, tile.facing().getOpposite(),
                    tile.maxwell() ? 1 : settings.particles(), spread);
        }
    }

    private static Map<NativeDriveDesign, Artwork> loadArtwork() {
        var result = new EnumMap<NativeDriveDesign, Artwork>(NativeDriveDesign.class);
        try (var input = NativeDriveRenderer.class.getResourceAsStream("/assets/vandorlabs/data/native_drive_surfaces.json")) {
            if (input == null) throw new IllegalStateException("Missing shared drive surfaces");
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var drive : NativeDriveDesign.SUPPORTED) {
                var entry = data.getAsJsonObject(drive.id);
                var surfaces = entry.getAsJsonArray("surfaces");
                var planes = new Surface[surfaces.size()];
                for (int i = 0; i < planes.length; i++) {
                    var surface = surfaces.get(i).getAsJsonObject();
                    var corners = new float[4][];
                    for (int vertex = 0; vertex < corners.length; vertex++) corners[vertex] = triple(surface.getAsJsonArray("corners").get(vertex).getAsJsonArray());
                    planes[i] = new Surface(corners, triple(surface.getAsJsonArray("normal")));
                }
                var paths = new java.util.ArrayList<String>();
                for (var model : entry.getAsJsonArray("meshes")) paths.add(model.getAsString());
                result.put(drive, new Artwork(List.copyOf(paths), planes));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read authored drive surfaces", failure);
        }
        return Map.copyOf(result);
    }

    private static float[] triple(com.google.gson.JsonArray vector) {
        return new float[]{vector.get(0).getAsFloat(), vector.get(1).getAsFloat(), vector.get(2).getAsFloat()};
    }

    private NativeDriveRenderer() {}
}
