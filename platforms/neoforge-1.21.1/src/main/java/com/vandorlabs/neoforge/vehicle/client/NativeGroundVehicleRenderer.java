package com.vandorlabs.neoforge.vehicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.NativeVehicleStatusRenderer;
import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.gear.NativeGearBlock;
import com.vandorlabs.neoforge.gear.NativeGearEntity;
import com.vandorlabs.neoforge.gear.client.NativeGearRenderer;
import com.vandorlabs.neoforge.propulsion.NativePropulsionParticles;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.transfer.client.NativeSnapshotPalette;
import com.vandorlabs.neoforge.transfer.client.NativeSnapshotPalette.Palette;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleBindings;
import com.vandorlabs.neoforge.vehicle.NativeVehicleVisual;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Uses the public snapshot and the same baked palette as other native moving blocks. */
@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeGroundVehicleRenderer extends EntityRenderer<NativeGroundVehicle> {
    private static final int MAX_CACHE = 16 * 1024 * 1024;
    private static final int MAX_QUADS = 8 * 1024 * 1024;
    private static final Map<NativeVehicleVisual, Mesh> MESHES = new WeakHashMap<>();
    private static Object level;

    private record Key(BlockState state, BlockPos tile, CompoundTag appearance) {}
    private record Cell(BlockPos pos, Palette palette, NativeGearEntity arm) {}
    private static final class Mesh {
        final List<Cell> cells;
        final int bytes;
        int lastExhaustTick = Integer.MIN_VALUE;

        Mesh(List<Cell> cells, int bytes) {
            this.cells = cells;
            this.bytes = bytes;
        }
    }

    private final NativeVehicleStatusRenderer status;
    private final BlockPos.MutableBlockPos lightPosition=new BlockPos.MutableBlockPos();
    private final org.joml.Matrix3f hullRotation=new org.joml.Matrix3f();

    public NativeGroundVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        status = new NativeVehicleStatusRenderer(context.getFont());
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        if (NativeVehicleBindings.VEHICLE != null) event.registerEntityRenderer(NativeVehicleBindings.VEHICLE, NativeGroundVehicleRenderer::new);
    }

    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ignored -> clear());
    }

    static void clear() { MESHES.clear(); level = null; }

    private static Mesh mesh(NativeGroundVehicle craft, NativeVehicleVisual visual) {
        if (level != craft.level()) {
            clear();
            level = craft.level();
        }
        var existing = MESHES.get(visual);
        if (existing != null) return existing;
        var materials = new HashMap<Key, Palette>();
        var cells = new ArrayList<Cell>(visual.cells().size());
        int bytes = 256 + visual.cells().size() * 96;
        int quadBytes = 0;
        for (var cell : visual.cells()) {
            if (cell.state().getBlock() instanceof NativeGearBlock block) {
                if (cell.appearance().getBoolean("GearController")
                    && block.newBlockEntity(cell.pos(), cell.state()) instanceof NativeGearEntity arm) {
                    arm.loadWithComponents(cell.appearance(), craft.level().registryAccess());
                    cells.add(new Cell(cell.pos(), null, arm));
                    bytes += 1024;
                }
                continue;
            }
            var at = craft.blockPosition().offset(cell.pos());
            var key = new Key(cell.state(), cell.state().hasBlockEntity() ? cell.pos() : null, cell.appearance());
            var palette = materials.get(key);
            if (palette == null) {
                palette = NativeSnapshotPalette.build(craft.level(), at, cell.state(), key.appearance, MAX_QUADS - quadBytes);
                quadBytes += palette.quadBytes();
                bytes += palette.retainedBytes();
                materials.put(key, palette);
            }
            cells.add(new Cell(cell.pos(), palette, null));
        }
        var built = new Mesh(List.copyOf(cells), bytes);
        int used = MESHES.values().stream().mapToInt(value -> value.bytes).sum();
        while (used + bytes > MAX_CACHE && !MESHES.isEmpty()) {
            var iterator = MESHES.entrySet().iterator();
            used -= iterator.next().getValue().bytes;
            iterator.remove();
        }
        if (bytes <= MAX_CACHE) MESHES.put(visual, built);
        return built;
    }

    @Override public ResourceLocation getTextureLocation(NativeGroundVehicle craft) { return InventoryMenu.BLOCK_ATLAS; }

    @Override public void render(NativeGroundVehicle craft, float entityYaw, float partial, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        var visual = craft.visual();
        if (visual == null || !craft.admitted()) return;
        var world = craft.level();
        var client = Minecraft.getInstance();
        var mesh = mesh(craft, visual);
        float yaw = Mth.rotLerp(partial, craft.yRotO, craft.getYRot());
        float pitch = craft.renderPitch(partial);
        double originX=Mth.lerp(partial,craft.xo,craft.getX());
        double originY=Mth.lerp(partial,craft.yo,craft.getY());
        double originZ=Mth.lerp(partial,craft.zo,craft.getZ());
        hullRotation.rotationY(-yaw*Mth.DEG_TO_RAD);
        if(pitch!=0)hullRotation.rotate(pitch*Mth.DEG_TO_RAD,-craft.facing().getStepZ(),0,craft.facing().getStepX());
        boolean emitExhaust = mesh.lastExhaustTick != craft.tickCount;
        mesh.lastExhaustTick = craft.tickCount;
        poses.pushPose();
        try {
            poses.mulPose(Axis.YP.rotationDegrees(-yaw));
            if (pitch != 0) poses.mulPose(Axis.of(new org.joml.Vector3f(-craft.facing().getStepZ(), 0, craft.facing().getStepX())).rotationDegrees(pitch));
            poses.translate(-.5, 0, -.5);
            for (var cell : mesh.cells) {
                var palette = cell.palette;
                // A deck can rest half a cell above its logical owner. Sample its drawn center so
                // floor rounding cannot borrow the light inside the opaque road beneath it.
                double x=cell.pos.getX(),y=cell.pos.getY()+.5,z=cell.pos.getZ();
                var at=lightPosition.set(originX+hullRotation.m00()*x+hullRotation.m10()*y+hullRotation.m20()*z,
                    originY+hullRotation.m01()*x+hullRotation.m11()*y+hullRotation.m21()*z,
                    originZ+hullRotation.m02()*x+hullRotation.m12()*y+hullRotation.m22()*z);
                int cellLight = !craft.highFlight() && world.hasChunkAt(at) ? LevelRenderer.getLightColor(world, at) : light;
                poses.pushPose();
                try {
                    poses.translate(cell.pos.getX(), cell.pos.getY(), cell.pos.getZ());
                    if (cell.arm != null) {
                        NativeGearRenderer.renderCarried(cell.arm, craft.armPose(cell.pos), poses, buffers, cellLight, OverlayTexture.NO_OVERLAY);
                        continue;
                    }
                    if (palette.fallback()) {
                        client.getBlockRenderer().renderSingleBlock(palette.state(), poses, buffers, cellLight,
                                OverlayTexture.NO_OVERLAY, palette.data(), null);
                    } else for (var layer : palette.layers()) {
                        var buffer = buffers.getBuffer(layer.type());
                        for (var quad : layer.quads()) {
                            int tint = quad.isTinted() ? client.getBlockColors().getColor(palette.state(), world, at, quad.getTintIndex()) : 0xffffff;
                            float shade = world.getShade(quad.getDirection(), quad.isShade());
                            buffer.putBulkData(poses.last(), quad, (tint >> 16 & 255) / 255F * shade,
                                    (tint >> 8 & 255) / 255F * shade, (tint & 255) / 255F * shade,
                                    1, cellLight, OverlayTexture.NO_OVERLAY);
                        }
                    }
                    var tile = palette.tile();
                    if (tile instanceof NativeBuilderEntity builder && builder.maxwell()) {
                        int gauge = craft.maxwellGauge(cell.pos);
                        if (gauge >= 0) builder.updateCarriedMaxwellVisual(gauge >>> 4, gauge & 15);
                        if (emitExhaust && builder.driveLevel() == 15 && world instanceof ClientLevel level) {
                            exhaust(level, craft, builder, cell.pos, partial, yaw, pitch);
                        }
                        // The entity already passed culling. Render the detached engine at
                        // the hull pose with current light, independent of its cached block position.
                        client.getBlockEntityRenderDispatcher().renderItem(builder, poses, buffers, cellLight,
                                OverlayTexture.NO_OVERLAY);
                    } else if (tile instanceof NativeScreenEntity screen) {
                        status.renderScreen(craft, cell.pos, screen, poses, buffers, cellLight, OverlayTexture.NO_OVERLAY);
                    } else if (tile != null && client.getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
                        client.getBlockEntityRenderDispatcher().render(tile, partial, poses, buffers);
                    }
                } finally {
                    poses.popPose();
                }
            }
            status.render(craft, poses, buffers);
        } finally {
            poses.popPose();
        }
    }

    private static void exhaust(ClientLevel world, NativeGroundVehicle craft, NativeBuilderEntity builder,
                                BlockPos local, float partial, float yaw, float pitch) {
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return;
        var layout = builder.layout();
        var original = builder.getBlockPos();
        var outlet = NativeBuilderBindings.world(builder, layout.width * .5, layout.height * .55, layout.depth - .05)
                .add(local.getX() - original.getX() - .5, local.getY() - original.getY(),
                        local.getZ() - original.getZ() - .5);
        var nozzle = craft.getPosition(partial).add(craft.rotateLocalDirection(outlet, yaw, pitch));
        if (camera.distanceToSqr(nozzle) > 64 * 64) return;
        var up = craft.rotateLocalDirection(new Vec3(0, 1, 0), yaw, pitch);
        var rear = craft.rotateLocalDirection(Vec3.atLowerCornerOf(builder.facing().getOpposite().getNormal()), yaw, pitch);
        NativePropulsionParticles.carriedMaxwell(world, nozzle, up, rear, .95F * layout.width / MaxwellEngine.BASE_WIDTH);
    }
}
