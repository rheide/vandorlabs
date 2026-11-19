package com.vandorlabs.neoforge.turntable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.transfer.client.NativeSnapshotPalette;
import com.vandorlabs.neoforge.transfer.client.NativeSnapshotPalette.Palette;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
final class NativeTurntableRenderer implements BlockEntityRenderer<NativeTurntableEntity> {
    private static final int MAX_CACHE = 16 * 1024 * 1024;
    private static final int MAX_QUADS = 8 * 1024 * 1024;

    private record Key(BlockState state, CompoundTag appearance, BlockPos tilePosition) {}
    private record Cell(BlockPos offset, BlockPos lightPos, Palette palette) {}
    private record Mesh(List<Cell> cells, int bytes) {}

    private static final Map<NativeTurntableVisual, Mesh> CACHE = new WeakHashMap<>();

    NativeTurntableRenderer(BlockEntityRendererProvider.Context context) {}

    static void clear() { CACHE.clear(); }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static Mesh mesh(NativeTurntableEntity owner, NativeTurntableVisual view) {
        var existing = CACHE.get(view);
        if (existing != null) return existing;
        int bytes = 256;
        int quadBytes = 0;
        var palettes = new HashMap<Key, Palette>();
        var cells = new ArrayList<Cell>(view.cells().size());
        for (var cell : view.cells()) {
            var context = owner.getBlockPos().offset(cell.offset());
            var key = new Key(cell.state(), cell.appearance(), cell.state().hasBlockEntity() ? context : null);
            var palette = palettes.get(key);
            if (palette == null) {
                palette = NativeSnapshotPalette.build(owner.getLevel(), context, cell.state(), cell.appearance(), MAX_QUADS - quadBytes);
                quadBytes = Math.addExact(quadBytes, palette.quadBytes());
                bytes = Math.addExact(bytes, palette.retainedBytes());
                palettes.put(key, palette);
            }
            cells.add(new Cell(cell.offset(), context, palette));
            bytes = Math.addExact(bytes, 96);
        }
        var next = new Mesh(List.copyOf(cells), bytes);
        int used = CACHE.values().stream().mapToInt(Mesh::bytes).sum();
        while (used + bytes > MAX_CACHE && !CACHE.isEmpty()) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) break;
            used -= iterator.next().getValue().bytes();
            iterator.remove();
        }
        if (bytes <= MAX_CACHE) CACHE.put(view, next);
        return next;
    }

    @Override public void render(NativeTurntableEntity owner, float partial, PoseStack poses, MultiBufferSource buffers,
                                 int light, int overlay) {
        var view = owner.visual();
        var world = owner.getLevel();
        if (view == null || world == null) return;
        var mc = Minecraft.getInstance();
        for (var cell : mesh(owner, view).cells()) {
            var palette = cell.palette();
            int cellLight = world.hasChunkAt(cell.lightPos()) ? LevelRenderer.getLightColor(world, cell.lightPos()) : light;
            poses.pushPose();
            try {
                poses.translate(cell.offset().getX(), cell.offset().getY(), cell.offset().getZ());
                if (palette.fallback()) {
                    mc.getBlockRenderer().renderSingleBlock(palette.state(), poses, buffers, cellLight, overlay, palette.data(), null);
                } else {
                    for (var layer : palette.layers()) {
                        var buffer = buffers.getBuffer(layer.type());
                        for (var quad : layer.quads()) {
                            int tint = quad.isTinted()
                                    ? mc.getBlockColors().getColor(palette.state(), world, cell.lightPos(), quad.getTintIndex()) : 0xffffff;
                            float shade = world.getShade(quad.getDirection(), quad.isShade());
                            buffer.putBulkData(poses.last(), quad, (tint >> 16 & 255) / 255F * shade,
                                    (tint >> 8 & 255) / 255F * shade, (tint & 255) / 255F * shade, 1, cellLight, overlay);
                        }
                    }
                }
                if (palette.tile() != null && mc.getBlockEntityRenderDispatcher().getRenderer(palette.tile()) != null) {
                    mc.getBlockEntityRenderDispatcher().render(palette.tile(), partial, poses, buffers);
                }
            } finally {
                poses.popPose();
            }
        }
    }

    @Override public AABB getRenderBoundingBox(NativeTurntableEntity owner) {
        var view = owner.visual();
        return view == null ? new AABB(owner.getBlockPos()) : view.bounds().move(owner.getBlockPos());
    }

    @Override public boolean shouldRenderOffScreen(NativeTurntableEntity owner) { return owner.visual() != null; }

    @Override public boolean shouldRender(NativeTurntableEntity owner, Vec3 camera) {
        return owner.visual() != null && getRenderBoundingBox(owner).distanceToSqr(camera) <= 64 * 64;
    }
}
