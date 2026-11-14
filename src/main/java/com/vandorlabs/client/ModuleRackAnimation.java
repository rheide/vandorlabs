package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.rack.ModuleRackMotion;
import com.vandorlabs.tiles.TileEntityModuleRack;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only moving modules enter the tile-render pass. The real rack has no TESR:
 * one client-only render proxy exists during insertion and chunk publication,
 * then leaves RenderGlobal entirely. Idle work is one empty-collection check,
 * independent of the number of placed racks. Normal opaque-pass rendering also
 * keeps modules behind glass and within the shader pipeline.
 */
@Mod.EventBusSubscriber(modid = "vandorlabs", value = Side.CLIENT)
public final class ModuleRackAnimation {
    private static final double WAIT_LIMIT_TICKS = 40;
    private static final double INVISIBLE_SETTLE_TICKS = 20;
    private static final Map<BlockPos, Entry> ACTIVE = new LinkedHashMap<>();
    private static final VisualClock CLOCK = new VisualClock();
    private static final Field FRUSTUM = ReflectionHelper.findField(RenderGlobal.class, "viewFrustum", "field_175008_n");
    private static final Field TASK = ReflectionHelper.findField(RenderChunk.class, "compileTask", "field_178599_i");
    private static final Field RENDER_INFOS = ReflectionHelper.findField(RenderGlobal.class, "renderInfos", "field_72755_R");
    private static Field infoChunk;
    private static final Method LOOKUP = lookup();
    private static World world;
    private static double frameTime;
    private static long draws;

    private ModuleRackAnimation() { }

    public static void registerRenderer() {
        ClientRegistry.bindTileEntitySpecialRenderer(AnimatedRackTile.class, new Renderer());
    }

    private static Method lookup() {
        for (Method method : ViewFrustum.class.getDeclaredMethods())
            if (method.getReturnType() == RenderChunk.class
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] { BlockPos.class })) {
                method.setAccessible(true); return method;
            }
        throw new IllegalStateException("Missing render-section lookup");
    }

    private static double now(Minecraft mc) {
        return CLOCK.sample(System.nanoTime(), mc.isGamePaused());
    }

    /** Called from the client packet adapter only after its revision checks. */
    public static void updated(TileEntityModuleRack tile, int previousMask, long[] previousSequences, boolean initial) {
        if (tile.getWorld() == null || !tile.getWorld().isRemote || tile.getWorld() instanceof VehicleWorld) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(() -> updated(tile, previousMask, previousSequences, initial));
            return;
        }
        if (tile.getWorld() != mc.world) { tile.setRenderMask(tile.getOccupiedMask()); return; }
        if (world != mc.world) reset(mc);
        frameTime = now(mc);
        Entry entry = ACTIVE.get(tile.getPos());
        if (entry != null && entry.incarnation != tile.getIncarnation()) {
            detach(mc, entry);
            ACTIVE.remove(tile.getPos());
            entry = null;
        }
        // Replacement client tiles retain the timeline only for the same
        // authoritative rack incarnation. A newly placed rack starts afresh,
        // even when its insertion sequences match the rack it replaced.
        if (entry != null) entry.bind(tile);
        int occupied = tile.getOccupiedMask();
        for (int slot = 0; slot < 4; slot++) {
            int bit = 1 << slot;
            Pose previous = entry == null ? null : entry.poses[slot];
            if ((occupied & bit) == 0) {
                if (entry != null) entry.poses[slot] = null;
                continue;
            }
            long sequence = tile.getInsertionSequence(slot);
            if (previous != null && previous.sequence == sequence) continue;
            if (previous != null) entry.poses[slot] = null;
            long oldSequence = previousSequences == null || previousSequences.length <= slot ? 0 : previousSequences[slot];
            if (!ModuleRackMotion.starts(initial, previousMask, occupied, slot, oldSequence, sequence)) continue;
            if (entry == null) { entry = new Entry(tile); ACTIVE.put(tile.getPos().toImmutable(), entry); }
            // Packet occupancy is not an upload acknowledgement: a rapid
            // remove/reinsert can still have its old face in the terrain VBO.
            // Always wait for the empty-slot publication before sliding.
            entry.poses[slot] = new Pose(sequence, frameTime, true);
        }
        if (entry == null) { tile.setRenderMask(occupied); return; }
        if (!entry.hasPoses()) { detach(mc, entry); ACTIVE.remove(tile.getPos()); tile.setRenderMask(occupied); return; }
        entry.publish(mc);
        attach(mc, entry);
    }

    /** Defer identity resolution to the next active pass: a same-position tile
     * replacement can retain its pose, while a mined/unloaded rack is dropped.
     */
    public static void removed(TileEntityModuleRack tile) {
        if (tile.getWorld() == null || tile.getWorld() != world || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) { mc.addScheduledTask(() -> removed(tile)); return; }
        Entry entry = ACTIVE.get(tile.getPos());
        if (entry != null && entry.tile == tile) entry.removed = true;
    }

    public static void reload() {
        for (Entry entry : ACTIVE.values()) entry.publication = null;
    }

    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (event.getWorld() == world) { reset(Minecraft.getMinecraft()); world = null; }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        advance(Minecraft.getMinecraft());
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != world) { reset(mc); return; }
        frameTime = now(mc);
    }

    private static void advance(Minecraft mc) {
        if (mc.world != world) { reset(mc); return; }
        frameTime = now(mc);
        Iterator<Entry> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!entry.valid()) { detach(mc, entry); iterator.remove(); continue; }
            if (entry.publication != null && entry.publication.outsideView(mc)) {
                // Off-view sections cannot acknowledge an empty-slot upload.
                // No visible animation is owed there; their next build seats it.
                entry.tile.setRenderMask(entry.tile.getOccupiedMask());
                detach(mc, entry); iterator.remove(); continue;
            }
            if (entry.waitExpired(frameTime) || entry.allIdlePoses()
                    && frameTime - entry.lastVisible >= INVISIBLE_SETTLE_TICKS) {
                // RenderGlobal does not schedule dirty sections behind the
                // camera/occlusion graph. Never retain a waiting proxy forever
                // for a cosmetic animation that the player has not seen.
                entry.tile.setRenderMask(entry.tile.getOccupiedMask());
                detach(mc, entry); iterator.remove(); continue;
            }
            for (int slot = 0; slot < 4; slot++) {
                Pose pose = entry.poses[slot];
                if (pose == null) continue;
                if ((entry.tile.getOccupiedMask() & (1 << slot)) == 0
                        || pose.sequence != entry.tile.getInsertionSequence(slot)) { entry.poses[slot] = null; continue; }
                if (!pose.waiting && frameTime - pose.started >= ModuleRackMotion.DURATION_TICKS) pose.seated = true;
            }
            entry.publish(mc);
            entry.acknowledge(mc);
            // No render section represents a rack outside the current view
            // distance. Its eventual section build will read the seated mask;
            // an already-finished insertion needs no retained render proxy.
            if (entry.allSeated() && entry.publication.outsideView(mc)) {
                detach(mc, entry); iterator.remove(); continue;
            }
            if (!entry.hasPoses()) { detach(mc, entry); iterator.remove(); }
            else attach(mc, entry); // Reattach only if the renderer/frustum was recreated.
        }
    }

    private static void attach(Minecraft mc, Entry entry) {
        if (mc.renderGlobal == null) return;
        try {
            ViewFrustum view = (ViewFrustum)FRUSTUM.get(mc.renderGlobal);
            if (entry.attachedRenderer == mc.renderGlobal && entry.attachedView == view) return;
            detach(mc, entry);
            mc.renderGlobal.updateTileEntities(Collections.emptyList(), Collections.<TileEntity>singletonList(entry.proxy));
            entry.attachedRenderer = mc.renderGlobal; entry.attachedView = view;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Module Rack render-proxy attachment failed", failure);
        }
    }

    private static void detach(Minecraft mc, Entry entry) {
        if (entry.attachedRenderer != null)
            entry.attachedRenderer.updateTileEntities(Collections.<TileEntity>singletonList(entry.proxy), Collections.emptyList());
        entry.attachedRenderer = null; entry.attachedView = null;
    }

    private static void reset(Minecraft mc) {
        for (Entry entry : ACTIVE.values()) detach(mc, entry);
        ACTIVE.clear(); world = mc.world;
    }

    static int activeCount() { return ACTIVE.size(); }
    static long drawCount() { return draws; }

    private static final class Pose {
        final long sequence;
        double started;
        boolean waiting, seated;
        Pose(long sequence, double started, boolean waiting) {
            this.sequence = sequence; this.started = started; this.waiting = waiting;
        }
    }

    private static final class Entry {
        TileEntityModuleRack tile;
        final long incarnation;
        final Pose[] poses = new Pose[4];
        final AnimatedRackTile proxy;
        final BlockPos section;
        EnumFacing facing;
        AxisAlignedBB bounds;
        Publication publication;
        RenderGlobal attachedRenderer;
        ViewFrustum attachedView;
        int publishedMask = -1, publishedOccupied = -1;
        boolean removed;
        double lastVisible;
        double nextVisibilityProbe;
        Entry(TileEntityModuleRack tile) {
            incarnation = tile.getIncarnation();
            section = new BlockPos(tile.getPos().getX() & ~15, tile.getPos().getY() & ~15, tile.getPos().getZ() & ~15);
            proxy = new AnimatedRackTile(this); lastVisible = frameTime; bind(tile);
        }
        void bind(TileEntityModuleRack replacement) {
            tile = replacement; removed = false; proxy.setWorld(tile.getWorld()); proxy.setPos(tile.getPos());
            if (tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof BlockModuleRack) {
                facing = tile.getWorld().getBlockState(tile.getPos()).getValue(BlockModuleRack.FACING);
                BlockPos p = tile.getPos();
                double dx = facing.getFrontOffsetX() * ModuleRackMotion.TRAVEL_BLOCKS;
                double dy = facing.getFrontOffsetY() * ModuleRackMotion.TRAVEL_BLOCKS;
                double dz = facing.getFrontOffsetZ() * ModuleRackMotion.TRAVEL_BLOCKS;
                bounds = new AxisAlignedBB(p.getX() + Math.min(0, dx), p.getY() + Math.min(0, dy), p.getZ() + Math.min(0, dz),
                        p.getX() + 1 + Math.max(0, dx), p.getY() + 1 + Math.max(0, dy), p.getZ() + 1 + Math.max(0, dz));
            }
        }
        boolean valid() {
            if (world == null || tile.getWorld() != world || !world.isBlockLoaded(tile.getPos())) return false;
            TileEntity current = world.getTileEntity(tile.getPos());
            if (!(current instanceof TileEntityModuleRack)
                    || ((TileEntityModuleRack)current).getIncarnation() != incarnation) return false;
            if (current != tile) {
                bind((TileEntityModuleRack)current);
            }
            if (removed) return false;
            if (tile.isInvalid() || !(world.getBlockState(tile.getPos()).getBlock() instanceof BlockModuleRack)) return false;
            if (world.getBlockState(tile.getPos()).getValue(BlockModuleRack.FACING) != facing) {
                bind(tile); publication = null;
            }
            return true;
        }
        boolean hasPoses() { for (Pose pose : poses) if (pose != null) return true; return false; }
        boolean allSeated() {
            for (Pose pose : poses) if (pose != null && !pose.seated) return false;
            return true;
        }
        boolean allIdlePoses() {
            for (Pose pose : poses) if (pose != null && !pose.waiting && !pose.seated) return false;
            return true;
        }
        boolean waitExpired(double time) {
            double youngest = Double.NEGATIVE_INFINITY;
            for (Pose pose : poses) if (pose != null) {
                if (!pose.waiting) return false;
                youngest = Math.max(youngest, pose.started);
            }
            return youngest != Double.NEGATIVE_INFINITY && time - youngest >= WAIT_LIMIT_TICKS;
        }
        boolean hiddenCompletedSection(Minecraft mc, double time) {
            if (!allSeated() || time < nextVisibilityProbe) return false;
            double finished = Double.NEGATIVE_INFINITY;
            for (Pose pose : poses) if (pose != null)
                finished = Math.max(finished, pose.started + ModuleRackMotion.DURATION_TICKS);
            if (finished == Double.NEGATIVE_INFINITY || time - finished < INVISIBLE_SETTLE_TICKS) return false;
            nextVisibilityProbe = time + INVISIBLE_SETTLE_TICKS;
            try {
                // setupTerrain's main-view graph includes frustum AND occlusion
                // culling. At zero offset every module is inside this section,
                // so absence proves a transient handoff is no longer visible.
                for (Object info : (Iterable<?>)RENDER_INFOS.get(mc.renderGlobal)) {
                    if (infoChunk == null) infoChunk = ReflectionHelper.findField(info.getClass(), "renderChunk", "field_178036_a");
                    RenderChunk rendered = (RenderChunk)infoChunk.get(info);
                    if (rendered.getPosition().equals(section)) return false;
                }
                return true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Module Rack completed-handoff visibility check failed", failure);
            }
        }
        void publish(Minecraft mc) {
            int occupied = tile.getOccupiedMask(), mask = occupied;
            for (int slot = 0; slot < 4; slot++) if (poses[slot] != null && !poses[slot].seated) mask &= ~(1 << slot);
            if (publication == null || publishedMask != mask || publishedOccupied != occupied) {
                // Capture the old upload identity BEFORE publishing the new
                // mask. A worker may finish before the next tick/TESR probe.
                Publication next = new Publication(mc, section, mask);
                tile.setRenderMask(mask);
                publishedMask = mask; publishedOccupied = occupied;
                publication = next;
                publication.requestRebuild(mc);
            } else tile.setRenderMask(mask);
        }
        void acknowledge(Minecraft mc) {
            if (publication == null || !publication.uploaded(mc)) return;
            int mask = publication.mask;
            for (int slot = 0; slot < 4; slot++) {
                Pose pose = poses[slot]; if (pose == null) continue;
                if (pose.waiting && (mask & (1 << slot)) == 0) { pose.waiting = false; pose.started = frameTime; }
                if (pose.seated && (mask & (1 << slot)) != 0) poses[slot] = null;
            }
            // Keep the acknowledged publication as a stable token until its
            // desired mask changes; do not rebuild again on every active tick.
        }
    }

    /** Same non-blocking upload acknowledgement used by segmented doors. A
     * getQuads call proves compilation only; it does not prove GPU publication.
     */
    private static final class Publication {
        final BlockPos position; final int mask;
        RenderChunk chunk; CompiledChunk before;
        boolean ready;
        Publication(Minecraft mc, BlockPos position, int mask) {
            this.position = position; this.mask = mask;
            try {
                ViewFrustum view = (ViewFrustum)FRUSTUM.get(mc.renderGlobal);
                RenderChunk current = view == null ? null : (RenderChunk)LOOKUP.invoke(view, position);
                if (current != null && current.getPosition().equals(position)) {
                    chunk = current; before = current.getCompiledChunk();
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Module Rack publication snapshot failed", failure);
            }
        }
        void requestRebuild(Minecraft mc) {
            if (chunk != null && !chunk.needsUpdate()) {
                int x = position.getX() + 8, y = position.getY() + 8, z = position.getZ() + 8;
                mc.renderGlobal.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
            }
        }
        boolean outsideView(Minecraft mc) {
            try {
                ViewFrustum view = (ViewFrustum)FRUSTUM.get(mc.renderGlobal);
                if (view == null) return true;
                RenderChunk current = (RenderChunk)LOOKUP.invoke(view, position);
                return current == null || !current.getPosition().equals(position);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Module Rack section lookup failed", failure);
            }
        }
        boolean uploaded(Minecraft mc) {
            if (ready && chunk != null && chunk.getPosition().equals(position)) return true;
            try {
                ViewFrustum view = (ViewFrustum)FRUSTUM.get(mc.renderGlobal);
                if (view == null) return false;
                RenderChunk current = (RenderChunk)LOOKUP.invoke(view, position);
                if (current == null || !current.getPosition().equals(position)) return false;
                if (current != chunk) {
                    chunk = current; before = current.getCompiledChunk(); ready = false;
                    requestRebuild(mc);
                    return false;
                }
                if (current.needsUpdate() || !current.getLockCompileTask().tryLock()) return false;
                try {
                    ChunkCompileTaskGenerator task = (ChunkCompileTaskGenerator)TASK.get(current);
                    if (task == null || !task.getLock().tryLock()) return false;
                    try {
                        ready = !current.needsUpdate() && task.getStatus() == ChunkCompileTaskGenerator.Status.DONE
                                && current.getCompiledChunk() != before && task.getCompiledChunk() == current.getCompiledChunk();
                        return ready;
                    } finally { task.getLock().unlock(); }
                } finally { current.getLockCompileTask().unlock(); }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Module Rack terrain publication failed", failure);
            }
        }
    }

    /** Ephemeral client render proxy; never added to a chunk, saved, or ticked. */
    public static final class AnimatedRackTile extends TileEntity {
        private final Entry entry;
        private AnimatedRackTile(Entry entry) { this.entry = entry; }
        @Override public boolean shouldRenderInPass(int pass) { return pass == 0 && entry.hasPoses(); }
        @Override public AxisAlignedBB getRenderBoundingBox() { return entry.bounds; }
    }

    private static final class Renderer extends TileEntitySpecialRenderer<AnimatedRackTile> {
        private final Frustum frustum = new Frustum();
        @Override public void render(AnimatedRackTile proxy, double x, double y, double z,
                                     float partialTicks, int destroyStage, float alpha) {
            Entry entry = proxy.entry;
            Minecraft mc = Minecraft.getMinecraft();
            if (!entry.valid() || !entry.hasPoses()) return;
            if (!VehicleRenderPass.shadow()) entry.acknowledge(mc);
            if (!entry.hasPoses()) return;
            if (!VehicleRenderPass.shadow() && mc.getRenderViewEntity() != null) {
                net.minecraft.entity.Entity camera = mc.getRenderViewEntity();
                frustum.setPosition(camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks,
                        camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks,
                        camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks);
                if (!frustum.isBoundingBoxInFrustum(entry.bounds)) return;
            }
            if (!VehicleRenderPass.shadow()) entry.lastVisible = frameTime;
            if (!VehicleRenderPass.shadow() && entry.hiddenCompletedSection(mc, frameTime)) {
                Arrays.fill(entry.poses, null);
                return;
            }
            boolean any = false;
            for (Pose pose : entry.poses) if (pose != null && !pose.waiting) { any = true; break; }
            if (!any) return;
            GlStateManager.pushMatrix();
            try (TurntableRenderState saved = new TurntableRenderState()) {
                GlStateManager.translate(x, y, z);
                GlStateManager.enableDepth(); GlStateManager.depthMask(true);
                GlStateManager.disableBlend(); GlStateManager.disableLighting(); GlStateManager.enableCull();
                GlStateManager.color(1, 1, 1, 1);
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.setTranslation(0, 0, 0); buffer.begin(7, DefaultVertexFormats.BLOCK);
                boolean shader = VehicleShaderContext.push(proxy, buffer);
                try {
                    int light = world.getCombinedLight(entry.tile.getPos().offset(entry.facing), 0);
                    for (int slot = 0; slot < 4; slot++) {
                        Pose pose = entry.poses[slot];
                        if (pose == null || pose.waiting) continue;
                        ModuleRackModel.MovingMesh mesh = ModuleRackModel.movingMesh(entry.facing, slot);
                        if (mesh == null) continue;
                        double offset = pose.seated ? 0 : ModuleRackMotion.offset(frameTime - pose.started);
                        mesh.draw(buffer, entry.facing.getFrontOffsetX() * offset, entry.facing.getFrontOffsetY() * offset,
                                entry.facing.getFrontOffsetZ() * offset, light);
                        draws++;
                    }
                } finally { if (shader) VehicleShaderContext.pop(buffer); }
                Tessellator.getInstance().draw();
            } finally { GlStateManager.popMatrix(); }
        }
    }
}
