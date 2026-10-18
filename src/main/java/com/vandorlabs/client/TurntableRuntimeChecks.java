package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTurntable;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityTurntable;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Live integrated-server check for the Turntable. A redstone-powered platform
 * captures its payload, the client receives the snapshot, both turntable
 * renderers draw the turning volume and the spinning platform marker without an
 * exception, and clearing the redstone restores the blocks.
 */
final class TurntableRuntimeChecks {
    private static final int PIXELS = 256;
    private static final double SPEED = 360.0;

    private static int stage, waitTicks, settleTicks;
    private static BlockPos anchor, control;
    private static List<BlockPos> platform, payload;
    private static BufferedImage volumeEarly, volumeLate, markerEarly, markerLate;
    private static double angleEarly, angleLate;
    private static File outputDirectory;

    private TurntableRuntimeChecks() { }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("turntable: " + message);
    }

    private static void server(Minecraft mc, Runnable action) {
        try { mc.getIntegratedServer().addScheduledTask(action).get(); }
        catch (Exception failure) { throw new IllegalStateException("turntable server setup failed", failure); }
    }

    private static boolean wait(String message) {
        require(++waitTicks < 300, message + " timeout");
        return false;
    }

    public static void begin(Minecraft mc) {
        stage = 0; waitTicks = 0; settleTicks = 0;
        anchor = control = null; platform = null; payload = null;
        volumeEarly = volumeLate = markerEarly = markerLate = null;
        angleEarly = angleLate = 0;
    }

    public static boolean tick(Minecraft mc, File output) throws Exception {
        outputDirectory = output;
        switch (stage) {
            case 0: return setup(mc);
            case 1: return engage(mc);
            case 2: return turn(mc);
            default: return finish(mc, output);
        }
    }

    private static boolean setup(Minecraft mc) {
        BlockPos candidate = mc.player.getPosition().up(12).toImmutable();
        if (!mc.world.isBlockLoaded(candidate)) { anchor = null; settleTicks = 0; return wait("fixture chunk"); }
        if (!candidate.equals(anchor)) { anchor = candidate; settleTicks = 0; return wait("fixture anchor settle"); }
        if (++settleTicks < 3) return false;
        control = anchor;
        BlockPos front = control.north();
        platform = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz < 3; dz++) platform.add(front.add(dx, 0, -dz));
        payload = new ArrayList<>();
        for (BlockPos cell : platform) payload.add(cell.up());
        server(mc, () -> build(mc.getIntegratedServer().getWorld(0)));
        stage = 1; waitTicks = 0;
        return false;
    }

    private static void build(World world) {
        for (BlockPos p : BlockPos.getAllInBox(control.add(-4, -2, -6), control.add(4, 6, 2))) world.setBlockToAir(p);
        world.setBlockState(control, ModBlocks.TURNTABLE_CONTROLLER.getDefaultState()
                .withProperty(BlockVandorDirectional.FACING, EnumFacing.NORTH), 3);
        for (BlockPos cell : platform) world.setBlockState(cell, ModBlocks.TURNTABLE.getDefaultState()
                .withProperty(BlockTurntable.FACING, EnumFacing.UP), 3);
        for (BlockPos cell : payload) world.setBlockState(cell, Blocks.STONE.getDefaultState(), 3);
        TileEntity tile = world.getTileEntity(control);
        require(tile instanceof TileEntityTurntableController, "controller tile missing");
        TileEntityTurntableController controller = (TileEntityTurntableController) tile;
        controller.trigger = TileEntityTurntableController.TRIGGER_POWERED;
        controller.height = 1;
        controller.speed = SPEED;
        controller.rotationDirection = TurntableGeometry.CLOCKWISE;
        world.setBlockState(control.up(), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
        controller.updatePower();
        require(controller.isActive() && controller.isCaptured() && controller.payloadSize() == 9,
                "redstone did not engage the turntable: " + controller.status);
        for (BlockPos cell : payload) require(world.isAirBlock(cell), "captured block left at " + cell);
    }

    private static boolean engage(Minecraft mc) {
        TileEntity tile = mc.world.getTileEntity(control);
        if (!(tile instanceof TileEntityTurntableController)) return wait("client controller tile");
        TileEntityTurntableController controller = (TileEntityTurntableController) tile;
        if (!controller.isActive() || controller.clientPayload() == null
                || controller.clientPayload().cells.size() != 9) return wait("client payload");
        server(mc, () -> {
            World world = mc.getIntegratedServer().getWorld(0);
            TileEntity serverTile = world.getTileEntity(control);
            require(serverTile instanceof TileEntityTurntableController, "server controller vanished");
            require(((TileEntityTurntableController) serverTile).isCaptured(), "server stopped capturing");
            for (BlockPos cell : payload) require(world.isAirBlock(cell), "server payload not removed");
        });
        volumeEarly = renderVolume(mc, controller);
        markerEarly = renderMarker(mc);
        angleEarly = controller.angle(0F);
        int volume = occupied(volumeEarly), marker = occupied(markerEarly);
        System.out.println("[vandorlabs][reprolab] turntable-live diagnostics payload="
                + controller.clientPayload().cells.size() + " axis=" + controller.axis()
                + " pivot=" + controller.pivotX() + "," + controller.pivotY() + "," + controller.pivotZ()
                + " angle=" + angleEarly + " volumePixels=" + volume + " markerPixels=" + marker);
        if (volume <= 200 || marker <= 0) dump("debug_engage", volumeEarly, markerEarly);
        require(volume > 200, "turning volume rendered nothing (" + volume + " pixels)");
        require(marker > 0, "platform marker rendered nothing");
        stage = 2; waitTicks = 0;
        return false;
    }

    private static void dump(String name, BufferedImage first, BufferedImage second) {
        try {
            BufferedImage montage = new BufferedImage(PIXELS * 2, PIXELS, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = montage.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.drawImage(first, 0, 0, null);
            graphics.drawImage(second, PIXELS, 0, null);
            graphics.drawString("volume", 5, 14);
            graphics.drawString("marker", PIXELS + 5, 14);
            graphics.dispose();
            write(name, montage);
        } catch (Exception failure) {
            System.out.println("[vandorlabs][reprolab] turntable diagnostic dump failed: " + failure);
        }
    }

    private static void write(String name, BufferedImage image) {
        try {
            javax.imageio.ImageIO.write(image, "png",
                    new File(outputDirectory, "shot_turntable_" + name + ".png"));
        } catch (Exception failure) {
            System.out.println("[vandorlabs][reprolab] turntable diagnostic dump failed: " + failure);
        }
    }

    private static boolean turn(Minecraft mc) {
        if (++waitTicks < 4) return false;
        TileEntity tile = mc.world.getTileEntity(control);
        require(tile instanceof TileEntityTurntableController, "client controller vanished");
        TileEntityTurntableController controller = (TileEntityTurntableController) tile;
        require(controller.isActive() && controller.clientPayload() != null, "client stopped early");
        volumeLate = renderVolume(mc, controller);
        markerLate = renderMarker(mc);
        angleLate = controller.angle(0F);
        require(angleDelta(angleEarly, angleLate) > 30,
                "client angle did not advance: " + angleEarly + " -> " + angleLate);
        require(difference(volumeEarly, volumeLate) > 300,
                "turning volume barely changed: " + difference(volumeEarly, volumeLate));
        require(difference(markerEarly, markerLate) > 0, "platform marker did not spin");
        server(mc, () -> {
            World world = mc.getIntegratedServer().getWorld(0);
            world.setBlockToAir(control.up());
            TileEntity serverTile = world.getTileEntity(control);
            require(serverTile instanceof TileEntityTurntableController, "server controller vanished");
            ((TileEntityTurntableController) serverTile).updatePower();
            require(!((TileEntityTurntableController) serverTile).isActive(), "redstone off kept turning");
            for (BlockPos cell : payload) require(world.getBlockState(cell).getBlock() == Blocks.STONE,
                    "payload not restored at " + cell);
        });
        stage = 3; waitTicks = 0;
        return false;
    }

    private static boolean finish(Minecraft mc, File output) throws Exception {
        TileEntity tile = mc.world.getTileEntity(control);
        if (tile instanceof TileEntityTurntableController
                && ((TileEntityTurntableController) tile).isActive()) return wait("client stop");
        for (BlockPos cell : payload)
            if (mc.world.getBlockState(cell).getBlock() != Blocks.STONE) return wait("client restore");
        server(mc, () -> {
            TileEntity serverTile = mc.getIntegratedServer().getWorld(0).getTileEntity(control);
            require(serverTile instanceof TileEntityTurntableController, "server controller vanished");
            require(!((TileEntityTurntableController) serverTile).isCaptured(), "server stayed captured");
        });
        javax.imageio.ImageIO.write(compose(), "png", new File(output, "shot_turntable_rotation.png"));
        System.out.println("[vandorlabs][reprolab] turntable-live PASS redstone engage, client payload, "
                + "rotating volume and platform marker pixels, redstone restore");
        return true;
    }

    private static BufferedImage renderVolume(Minecraft mc, TileEntityTurntableController controller) {
        double[] center = center(platform);
        return capture(mc, center[0], center[1] + 0.5, center[2], 2.8, () -> {
            TETurntableController renderer = (TETurntableController) (Object) TileEntityRendererDispatcher.instance
                    .getRenderer(controller);
            require(renderer != null, "controller renderer not registered");
            TileEntityRendererDispatcher.instance.world = controller.getWorld();
            renderer.render(controller, controller.getPos().getX(), controller.getPos().getY(),
                    controller.getPos().getZ(), 0F, -1, 1F);
        });
    }

    private static BufferedImage renderMarker(Minecraft mc) {
        BlockPos cell = platform.get(4);
        TileEntity tile = mc.world.getTileEntity(cell);
        require(tile instanceof TileEntityTurntable, "platform tile missing");
        return capture(mc, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 0.9, () -> {
            TETurntable renderer = (TETurntable) (Object) TileEntityRendererDispatcher.instance.getRenderer(tile);
            require(renderer != null, "platform renderer not registered");
            TileEntityRendererDispatcher.instance.world = tile.getWorld();
            renderer.render((TileEntityTurntable) tile, cell.getX(), cell.getY(), cell.getZ(), 0F, -1, 1F);
        });
    }

    private static BufferedImage capture(Minecraft mc, double cx, double cy, double cz, double extent, Runnable draw) {
        Framebuffer target = new Framebuffer(PIXELS, PIXELS, true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.ortho(-extent, extent, -extent, extent, -40, 40);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        double oldX = TileEntityRendererDispatcher.instance.entityX;
        double oldY = TileEntityRendererDispatcher.instance.entityY;
        double oldZ = TileEntityRendererDispatcher.instance.entityZ;
        World oldWorld = TileEntityRendererDispatcher.instance.world;
        boolean lightmap = false;
        try {
            target.bindFramebuffer(true);
            // Earlier offscreen passes pop raw attribute stacks, which can leave
            // the state manager's cache ahead of the driver. Establish the exact
            // raw state this capture assumes.
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true); GL11.glColorMask(true, true, true, true); GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.depthMask(true);
            GlStateManager.clearColor(0, 0, 0, 1);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth(); GlStateManager.disableBlend(); GlStateManager.enableTexture2D();
            GlStateManager.disableLighting(); GlStateManager.disableCull(); GlStateManager.color(1, 1, 1, 1);
            // Block models write a lightmap coordinate per vertex; the lightmap
            // unit must be live or those samples come back undefined/black.
            net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(mc.player, false);
            mc.entityRenderer.enableLightmap();
            lightmap = true;
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                    net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.rotate(20, 1, 0, 0);
            GlStateManager.rotate(45, 0, 1, 0);
            GlStateManager.translate(-cx, -cy, -cz);
            TileEntityRendererDispatcher.instance.entityX = cx;
            TileEntityRendererDispatcher.instance.entityY = cy;
            TileEntityRendererDispatcher.instance.entityZ = cz;
            draw.run();
            GL11.glFinish();
            return net.minecraft.util.ScreenShotHelper.createScreenshot(PIXELS, PIXELS, target);
        } finally {
            if (lightmap) mc.entityRenderer.disableLightmap();
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            TileEntityRendererDispatcher.instance.world = oldWorld;
            TileEntityRendererDispatcher.instance.entityX = oldX;
            TileEntityRendererDispatcher.instance.entityY = oldY;
            TileEntityRendererDispatcher.instance.entityZ = oldZ;
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true); GL11.glColorMask(true, true, true, true); GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.enableDepth(); GlStateManager.depthMask(true); GlStateManager.enableCull();
            GlStateManager.enableLighting(); GlStateManager.disableBlend(); GlStateManager.color(1, 1, 1, 1);
            target.deleteFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    private static double[] center(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minX = Math.min(minX, cell.getX()); maxX = Math.max(maxX, cell.getX());
            minY = Math.min(minY, cell.getY()); maxY = Math.max(maxY, cell.getY());
            minZ = Math.min(minZ, cell.getZ()); maxZ = Math.max(maxZ, cell.getZ());
        }
        return new double[] { (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0 };
    }

    private static double angleDelta(double a, double b) {
        double difference = Math.abs(TurntableGeometry.normalize(a - b));
        return Math.min(difference, 360 - difference);
    }

    private static int difference(BufferedImage a, BufferedImage b) {
        int changed = 0;
        for (int y = 0; y < PIXELS; y++) for (int x = 0; x < PIXELS; x++)
            if ((a.getRGB(x, y) & 0xffffff) != (b.getRGB(x, y) & 0xffffff)) changed++;
        return changed;
    }

    private static int occupied(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < PIXELS; y++) for (int x = 0; x < PIXELS; x++)
            if ((image.getRGB(x, y) & 0xffffff) != 0) count++;
        return count;
    }

    private static BufferedImage compose() {
        BufferedImage montage = new BufferedImage(PIXELS * 2, PIXELS * 2 + 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = montage.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.drawImage(volumeEarly, 0, 0, null);
        graphics.drawImage(volumeLate, PIXELS, 0, null);
        graphics.drawImage(markerEarly, 0, PIXELS + 24, null);
        graphics.drawImage(markerLate, PIXELS, PIXELS + 24, null);
        graphics.drawString("payload early / late", 5, PIXELS + 16);
        graphics.drawString("marker early / late", 5, PIXELS * 2 + 40);
        graphics.dispose();
        return montage;
    }
}
