package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Opt-in submission microbenchmark; run only in the disposable ReproLab world. */
final class ProgrammableRenderBenchmark {
    private static final BlockPos ORIGIN = new BlockPos(-8, 80, -8);
    private static File outputDirectory;
    private static final int WARMUP = 15, SAMPLES = 31;
    private ProgrammableRenderBenchmark() { }

    static void run(File output) {
        if (!Boolean.getBoolean("vandorlabs.renderBenchmark")) return;
        outputDirectory = output;
        Minecraft mc = Minecraft.getMinecraft();
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : Block.REGISTRY.getKeys()) {
            if (id.getResourceDomain().equals("vandorlabs")
                    && (id.getResourcePath().startsWith("programmable_")
                    || Arrays.asList("space_door", "rocket_thruster", "ion_drive",
                            "plasma_vent", "impulse_engine").contains(id.getResourcePath())))
                ids.add(id.toString());
        }
        Collections.sort(ids);
        ids.addAll(0, Arrays.asList("minecraft:stone", "minecraft:stone_slab",
                "minecraft:glass", "minecraft:glass_pane", "minecraft:oak_stairs",
                "minecraft:redstone_lamp", "minecraft:chest", "minecraft:iron_door"));
        try (PrintWriter csv = new PrintWriter(new File(output, "render-benchmark.csv"));
                PrintWriter info = new PrintWriter(new File(output, "render-benchmark.txt"))) {
            ProgrammableRedstoneBenchmark.run(mc, output);
            info.println("GL renderer: " + GL11.glGetString(GL11.GL_RENDERER));
            info.println("GL version: " + GL11.glGetString(GL11.GL_VERSION));
            info.println("Java: " + System.getProperty("java.version"));
            info.println("Backend: cached VBOs for baked geometry plus actual Forge-batched TESRs");
            info.println("15 warmups, 31 samples; glFinish before/after each batch; no FPS claim");
            csv.println("block,variant,count,baked_vertices,tile_renderers,build_ms,submit_p50_ms,submit_p95_ms,complete_p50_ms,complete_p95_ms,allocated_bytes_p50");
            mc.getFramebuffer().bindFramebuffer(true);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(-2, 20, -2, 20, -100, 100);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(16, 0, 0);
            GlStateManager.rotate(180, 0, 1, 0);
            GlStateManager.rotate(15, 1, 0, 0);
            GlStateManager.rotate(-20, 0, 1, 0);
            mc.entityRenderer.enableLightmap();
            GlStateManager.enableDepth();
            GlStateManager.clearColor(0, 0, 0, 1);
            try {
                for (String id : ids) {
                    measure(mc, csv, id, "default", 16);
                    measure(mc, csv, id, "default", 64);
                    if (Arrays.asList("minecraft:stone", "minecraft:stone_slab",
                            "vandorlabs:programmable_block", "vandorlabs:programmable_slab")
                            .contains(id)) {
                        measure(mc, csv, id, "floor_8x8", 64);
                        measure(mc, csv, id, "solid_4x4x4", 64);
                    }
                    if (id.equals("minecraft:iron_door")
                            || id.equals("vandorlabs:programmable_door"))
                        measure(mc, csv, id, "door_open", 64);
                    if (id.equals("vandorlabs:programmable_door"))
                        for (String doorVariant : Arrays.asList("door_glass", "door_sliding",
                                "door_hingeless", "door_center", "door_paired"))
                            measure(mc,csv,id,doorVariant,64);
                    if (Arrays.asList("vandorlabs:rocket_thruster", "vandorlabs:ion_drive",
                            "vandorlabs:plasma_vent", "vandorlabs:impulse_engine").contains(id)) {
                        measure(mc, csv, id + "_hexagonal", "default", 64);
                        measure(mc, csv, id + "_wedge", "default", 64);
                        measure(mc, csv, id, "engine_joined", 64);
                    }
                    if (Arrays.asList("vandorlabs:programmable_block", "vandorlabs:programmable_slab",
                            "vandorlabs:programmable_light").contains(id)) {
                        for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.values())
                            measure(mc, csv, id, "facing_" + facing.getName(), 16);
                    }
                    if (id.endsWith("programmable_slab")) measure(mc, csv, id, "upper_tiled", 16);
                    if (id.endsWith("programmable_light")) measure(mc, csv, id, "light_joined", 64);
                    if (id.contains("porthole")) {
                        measure(mc, csv, id, "round_joined", 64);
                        measure(mc, csv, id, "round_unjoined", 64);
                    }
                }
            } finally {
                mc.entityRenderer.disableLightmap();
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GL11.glPopAttrib();
                mc.getFramebuffer().unbindFramebuffer();
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            }
        } catch (Exception e) {
            throw new IllegalStateException("render benchmark failed", e);
        }
        System.out.println("[vandorlabs][reprolab] render-benchmark PASS");
    }

    private static void measure(Minecraft mc, PrintWriter csv, String id,
            String variant, int count) {
        Block block = Block.REGISTRY.getObject(new ResourceLocation(id));
        if (block == null || block == net.minecraft.init.Blocks.AIR)
            throw new IllegalStateException("missing benchmark block " + id);
        List<BlockPos> positions = new ArrayList<>();
        List<TileEntity> tiles = new ArrayList<>();
        List<BlockPos> extraPositions = new ArrayList<>();
        int side = count == 16 ? 4 : 8;
        int spacing = (variant.endsWith("_joined") && !variant.endsWith("unjoined"))
                || variant.equals("floor_8x8") || variant.equals("solid_4x4x4") ? 1 : 2;
        List<net.minecraft.client.renderer.vertex.VertexBuffer> baked = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                BlockPos pos = variant.equals("solid_4x4x4")
                        ? ORIGIN.add(i % 4, (i / 16) % 4, (i / 4) % 4)
                        : variant.equals("floor_8x8") ? ORIGIN.add(i % 8, 0, i / 8)
                        : variant.equals("door_paired")
                                ? ORIGIN.add((i % 8 / 2) * 3 + i % 2, (i / 8) * 2, 0)
                        : ORIGIN.add((i % side) * spacing, (i / side) * spacing, 0);
                if (!mc.world.isBlockLoaded(pos)) throw new IllegalStateException("fixture chunk not loaded");
                positions.add(pos);
                IBlockState fixture = block.getDefaultState();
                if (variant.equals("door_open")) {
                    if (block instanceof com.vandorlabs.blocks.BlockVandorDoor)
                        fixture = fixture.withProperty(com.vandorlabs.blocks.BlockVandorDoor.OPEN,true);
                    else if (block instanceof net.minecraft.block.BlockDoor)
                        fixture = fixture.withProperty(net.minecraft.block.BlockDoor.OPEN,true);
                }
                if (variant.equals("door_paired")) fixture = fixture.withProperty(
                        com.vandorlabs.blocks.BlockVandorDoor.HINGE,
                        i % 2 == 0 ? net.minecraft.block.BlockDoor.EnumHingePosition.LEFT
                                : net.minecraft.block.BlockDoor.EnumHingePosition.RIGHT);
                if (variant.startsWith("facing_")) fixture = fixture.withProperty(
                        com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING,
                        net.minecraft.util.EnumFacing.byName(variant.substring(7)));
                if (variant.equals("upper_tiled")) fixture = fixture.withProperty(
                        com.vandorlabs.blocks.BlockProgrammableSlab.HALF,
                        net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
                mc.world.setBlockState(pos, fixture, 2);
                if (block instanceof com.vandorlabs.blocks.BlockVandorDoor
                        || block instanceof net.minecraft.block.BlockDoor) {
                    BlockPos upper = pos.up();
                    IBlockState upperState = block.getDefaultState().withProperty(
                            block instanceof com.vandorlabs.blocks.BlockVandorDoor
                                    ? com.vandorlabs.blocks.BlockVandorDoor.HALF : net.minecraft.block.BlockDoor.HALF,
                            net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER);
                    if (variant.equals("door_open")) {
                        if (block instanceof com.vandorlabs.blocks.BlockVandorDoor)
                            upperState = upperState.withProperty(com.vandorlabs.blocks.BlockVandorDoor.OPEN,true);
                        else upperState = upperState.withProperty(net.minecraft.block.BlockDoor.OPEN,true);
                    }
                    mc.world.setBlockState(upper, upperState, 2);
                    extraPositions.add(upper);
                } else if (block instanceof com.vandorlabs.blocks.BlockBridgeChair) {
                    BlockPos upper = pos.up();
                    mc.world.setBlockState(upper, block.getDefaultState().withProperty(
                            com.vandorlabs.blocks.BlockBridgeChair.UPPER, true), 2);
                    extraPositions.add(upper);
                }
                TileEntity tile = mc.world.getTileEntity(pos);
                if (tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor) {
                    com.vandorlabs.tiles.TileEntitySpaceDoor door =
                            (com.vandorlabs.tiles.TileEntitySpaceDoor)tile;
                    if (variant.equals("door_glass"))
                        door.configure(0,1,true,0,false,false,true,0,false);
                    else if (variant.equals("door_sliding"))
                        door.configure(2,1,true,1,true,true,true,0,false);
                    else if (variant.equals("door_hingeless"))
                        door.configure(2,1,true,0,false,false,false,0,false);
                    else if (variant.equals("door_center"))
                        door.configure(2,1,true,0,true,false,true,0,false);
                }
                if (variant.startsWith("facing_") && tile instanceof TileEntityAnimatedScreenSelector)
                    ((TileEntityAnimatedScreenSelector) tile).setHousingTexture(3);
                if (variant.equals("light_joined"))
                    ((com.vandorlabs.tiles.TileEntityProgrammableLight) tile).configure(0, 15, true, 0);
                if (variant.equals("upper_tiled")) ((TileEntityAnimatedScreenSelector) tile).setSlabTileSides(true);
                if (tile instanceof TileEntityAnimatedScreenSelector && variant.startsWith("round")) {
                    ((TileEntityAnimatedScreenSelector) tile).setPortholeShape(3);
                    ((TileEntityAnimatedScreenSelector) tile).setJoinPortholes(variant.equals("round_joined"));
                }
                if (tile != null && TileEntityRendererDispatcher.instance.getRenderer(tile) != null)
                    tiles.add(tile);
            }
            positions.addAll(extraPositions);
            long start = System.nanoTime();
            int vertices = 0;

            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(layer);
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                buffer.setTranslation(-ORIGIN.getX(), -ORIGIN.getY(), -ORIGIN.getZ());
                for (BlockPos pos : positions) {
                    IBlockState state = mc.world.getBlockState(pos);
                    if (state.getRenderType() == EnumBlockRenderType.MODEL && block.canRenderInLayer(state, layer))
                        mc.getBlockRendererDispatcher().renderBlock(state, pos, mc.world, buffer);
                }
                int countInLayer = buffer.getVertexCount();
                vertices += countInLayer;
                buffer.finishDrawing();
                if (countInLayer > 0) {
                    net.minecraft.client.renderer.vertex.VertexBuffer vbo =
                            new net.minecraft.client.renderer.vertex.VertexBuffer(DefaultVertexFormats.BLOCK);
                    vbo.bufferData(buffer.getByteBuffer());
                    baked.add(vbo);
                }
                buffer.reset();
                buffer.setTranslation(0, 0, 0);
            }
            ForgeHooksClient.setRenderLayer(null);

            double build = (System.nanoTime() - start) / 1E6;
            double[] submit = new double[SAMPLES], complete = new double[SAMPLES];
            long[] allocated = new long[SAMPLES];
            for (int i = -WARMUP; i < SAMPLES; i++) {
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                // Normal terrain starts with fixed-function lighting off and culling on.
                // Reset between cases so a legacy renderer cannot tint the next case.
                GlStateManager.disableLighting();
                GlStateManager.enableCull();
                GlStateManager.color(1F,1F,1F,1F);
                GL11.glFinish();
                long bytesBefore = BenchmarkAllocations.currentThreadBytes();
                long before = System.nanoTime();
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                drawBaked(baked);
                TileEntityRendererDispatcher.instance.preDrawBatch();
                for (TileEntity tile : tiles) {
                    GlStateManager.color(1F, 1F, 1F, 1F);
                    BlockPos pos = tile.getPos();
                    TileEntityRendererDispatcher.instance.render(tile, pos.getX()-ORIGIN.getX(),
                            pos.getY()-ORIGIN.getY(), pos.getZ()-ORIGIN.getZ(), .5F);
                }
                TileEntityRendererDispatcher.instance.drawBatch(0);
                long submitted = System.nanoTime();
                long bytesAfter = BenchmarkAllocations.currentThreadBytes();
                GL11.glFinish();
                if (i >= 0) {
                    allocated[i] = bytesBefore < 0 ? -1 : bytesAfter - bytesBefore;
                    submit[i] = (submitted - before) / 1E6;
                    complete[i] = (System.nanoTime() - before) / 1E6;
                }
            }
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("GL error " + error + " in " + id);
            if (count == 64 || !variant.equals("default"))
                net.minecraft.util.ScreenShotHelper.saveScreenshot(outputDirectory, "benchmark-"
                        + id.replace(':', '-') + "-" + variant + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            Arrays.sort(submit);
            Arrays.sort(complete);
            Arrays.sort(allocated);
            csv.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                    id, variant, count, vertices, tiles.size(), build,
                    submit[15], submit[29], complete[15], complete[29], allocated[15]);
            csv.flush();
        } finally {
            for (net.minecraft.client.renderer.vertex.VertexBuffer vbo : baked) vbo.deleteGlBuffers();
            for (BlockPos pos : positions) mc.world.setBlockToAir(pos);
        }
    }
    /** Same BLOCK vertex layout/client arrays used by the 1.12 VBO chunk renderer. */
    private static void drawBaked(List<net.minecraft.client.renderer.vertex.VertexBuffer> baked) {
        if (baked.isEmpty()) return;
        net.minecraft.client.renderer.OpenGlHelper.setClientActiveTexture(
                net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        net.minecraft.client.renderer.OpenGlHelper.setClientActiveTexture(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        for (net.minecraft.client.renderer.vertex.VertexBuffer vbo : baked) {
            vbo.bindBuffer();
            GlStateManager.glVertexPointer(3,GL11.GL_FLOAT,28,0);
            GlStateManager.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,28,12);
            net.minecraft.client.renderer.OpenGlHelper.setClientActiveTexture(
                    net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            GlStateManager.glTexCoordPointer(2,GL11.GL_FLOAT,28,16);
            net.minecraft.client.renderer.OpenGlHelper.setClientActiveTexture(
                    net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
            GlStateManager.glTexCoordPointer(2,GL11.GL_SHORT,28,24);
            vbo.drawArrays(GL11.GL_QUADS);
            vbo.unbindBuffer();
        }
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        net.minecraft.client.renderer.OpenGlHelper.setClientActiveTexture(
                net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.resetColor();
    }

}
