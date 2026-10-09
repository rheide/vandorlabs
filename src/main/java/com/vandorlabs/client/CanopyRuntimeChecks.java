package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.io.File;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

/** Forge-world contracts plus client packet, rendered model and screenshot checks. */
public final class CanopyRuntimeChecks {
    static final String[] IDS =
            com.vandorlabs.blocks.ModBlocks.BLOCKS.stream()
                    .filter(b -> b instanceof BlockCanopy)
                    .map(b -> ((BlockCanopy) b).kind)
                    .toArray(String[]::new);
    private static final BlockPos TEST = new BlockPos(8, 100, 8);
    private static int stage, ticks;
    private static Future<?> pending;

    private static BlockCanopy block(String id) {
        return (BlockCanopy) Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", id));
    }

    private static void require(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("canopy: " + what);
    }

    private static void clear(World w) {
        for (BlockPos p : BlockPos.getAllInBox(TEST.add(-10, -2, -10), TEST.add(12, 6, 12)))
            w.setBlockToAir(p);
    }

    private static TileEntityCanopy place(World w, BlockCanopy b, BlockPos p, EnumFacing f) {
        IBlockState s = b.getDefaultState().withProperty(BlockCanopy.FACING, f);
        require(b.fits(w, p, f), "fixture fit " + b.kind);
        w.setBlockState(p, s, 2);
        b.onBlockPlacedBy(w, p, s, null, new ItemStack(b));
        return TileEntityCanopy.at(w, p);
    }

    private static com.google.gson.JsonArray assemblies() {
        try (java.io.InputStream in =
                CanopyRuntimeChecks.class.getResourceAsStream(
                        "/assets/vandorlabs/data/visor_assemblies.json")) {
            return new com.google.gson.JsonParser()
                    .parse(new java.io.InputStreamReader(in, "UTF-8"))
                    .getAsJsonArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void buildAssembly(
            World world, com.google.gson.JsonObject assembly, EnumFacing facing) {
        java.util.Set<BlockPos> occupied = new java.util.HashSet<>();
        for (com.google.gson.JsonElement entry : assembly.getAsJsonArray("modules")) {
            com.google.gson.JsonObject module = entry.getAsJsonObject();
            com.google.gson.JsonArray offset = module.getAsJsonArray("at");
            BlockPos cell =
                    TEST.offset(facing.rotateY(), offset.get(0).getAsInt())
                            .offset(facing.getOpposite(), offset.get(2).getAsInt())
                            .up(offset.get(1).getAsInt());
            BlockCanopy block = block(module.get("model").getAsString());
            TileEntityCanopy tile = place(world, block, cell, facing);
            require(block.height == assembly.get("height").getAsInt(), "module height");
            for (BlockPos member : block.cells(cell, facing)) {
                require(occupied.add(member), "overlapping module cells");
                require(
                        TileEntityCanopy.at(world, member).owner() == tile,
                        "module follower ownership");
            }
        }
        int sections =
                assembly.get("width").getAsInt() + 2 * (assembly.get("depth").getAsInt() - 1);
        require(
                occupied.size() == sections * assembly.get("height").getAsInt(),
                "assembly occupancy");
    }

    private static void clearBoard(World world) {
        for (BlockPos p : BlockPos.getAllInBox(TEST.add(-1, -1, -1), TEST.add(45, 5, 30)))
            world.setBlockToAir(p);
    }

    private static void lookAt(Minecraft mc, Vec3d target) {
        Vec3d look = target.subtract(mc.player.getPositionEyes(1));
        mc.player.rotationYaw =
                mc.player.prevRotationYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        mc.player.rotationPitch =
                mc.player.prevRotationPitch =
                        (float)
                                -Math.toDegrees(
                                        Math.atan2(
                                                look.y,
                                                Math.sqrt(look.x * look.x + look.z * look.z)));
    }

    public static void validate(Minecraft mc) throws Exception {
        mc.getIntegratedServer()
                .addScheduledTask(
                        () -> {
                            World w = mc.getIntegratedServer().getEntityWorld();
                            for (EnumFacing f : EnumFacing.HORIZONTALS)
                                for (String id : IDS) {
                                    clear(w);
                                    BlockCanopy b = block(id);
                                    TileEntityCanopy t = place(w, b, TEST, f);
                                    for (BlockPos cell : b.cells(TEST, f))
                                        require(
                                                TileEntityCanopy.at(w, cell).owner() == t,
                                                "cell ownership " + id);
                                    require(!t.boxes().isEmpty(), "surface collision " + id);
                                    require(!b.fits(w, TEST, f), "occupied footprint " + id);
                                    if (b.opening()) {
                                        require(t.clearance(), "empty sweep " + id);
                                        BlockPos obstacle =
                                                TEST.offset(f.getOpposite(), 2)
                                                        .up(b.kind.contains("hinge") ? 1 : 0);
                                        w.setBlockState(obstacle, Blocks.STONE.getDefaultState());
                                        t.toggle();
                                        require(!t.targetOpen, "blocked opening " + id);
                                        w.setBlockToAir(obstacle);
                                        t.toggle();
                                        require(t.targetOpen, "clear opening " + id);
                                        for (int i = 0; i < 16; i++) t.update();
                                        require(t.progress == 1, "duration " + id);
                                        require(
                                                t.boxes().stream()
                                                        .noneMatch(
                                                                box ->
                                                                        box.intersects(
                                                                                new AxisAlignedBB(
                                                                                                TEST)
                                                                                        .shrink(
                                                                                                .25))),
                                                "open entrance " + id);
                                        NBTTagCompound saved = t.writeToNBT(new NBTTagCompound());
                                        TileEntityCanopy restored = new TileEntityCanopy();
                                        restored.readFromNBT(saved);
                                        require(
                                                restored.targetOpen && restored.progress == 1,
                                                "saved pose " + id);
                                        t.toggle();
                                        for (int i = 0; i < 16; i++) t.update();
                                        require(t.progress == 0, "close pose " + id);
                                    }
                                    BlockPos last =
                                            b.cells(TEST, f).get(b.cells(TEST, f).size() - 1);
                                    w.setBlockToAir(last);
                                    for (BlockPos cell : b.cells(TEST, f))
                                        require(w.isAirBlock(cell), "assembly removal " + id);
                                }
                            for (EnumFacing f : EnumFacing.HORIZONTALS)
                                for (String mode : new String[] {"slide", "hinge"}) {
                                    clear(w);
                                    BlockCanopy b = block("opening_" + mode + "_1x2");
                                    TileEntityCanopy a = place(w, b, TEST, f),
                                            right = place(w, b, TEST.offset(f.rotateY()), f),
                                            third = place(w, b, TEST.offset(f.rotateY(), 2), f);
                                    require(
                                            a.width() == 2
                                                    && right.owner() == a
                                                    && !right.draws()
                                                    && third.pair == null,
                                            "pair lock " + mode);
                                    require(
                                            a.model().equals("opening_" + mode + "_2x2"),
                                            "wide asset " + mode);
                                    NBTTagCompound saved = right.writeToNBT(new NBTTagCompound());
                                    TileEntityCanopy reloaded = new TileEntityCanopy();
                                    reloaded.setWorld(w);
                                    reloaded.setPos(right.getPos());
                                    reloaded.readFromNBT(saved);
                                    require(reloaded.owner() == a, "saved pair " + mode);
                                    NBTTagCompound relocated = saved.copy();
                                    relocated.setInteger("x", right.getPos().getX() + 16);
                                    TileEntityCanopy copied = new TileEntityCanopy();
                                    copied.readFromNBT(relocated);
                                    require(
                                            copied.anchor.equals(right.getPos().east(16))
                                                    && copied.pair.equals(a.getPos().east(16)),
                                            "relocated membership " + mode);
                                    w.setBlockToAir(right.getPos().offset(f.getOpposite()));
                                    require(
                                            a.pair == null
                                                    && a.width() == 1
                                                    && a.progress == 0
                                                    && a.draws(),
                                            "unpair " + mode);
                                }
                            for (EnumFacing f : EnumFacing.HORIZONTALS)
                                for (int mask = 0; mask < 16; mask++) {
                                    clear(w);
                                    BlockCanopy b = block("regular_canopy_glass");
                                    TileEntityCanopy t = place(w, b, TEST, f);
                                    for (int bit : new int[] {1, 2, 4, 8})
                                        if ((mask & bit) != 0)
                                            place(
                                                    w,
                                                    b,
                                                    TEST.offset(
                                                            bit == 1
                                                                    ? f.rotateYCCW()
                                                                    : bit == 2
                                                                            ? f.rotateY()
                                                                            : bit == 4
                                                                                    ? f
                                                                                    : f
                                                                                            .getOpposite()),
                                                    f);
                                    require(t.mask() == mask, "regular mask " + mask);
                                    for (int bit : new int[] {1, 2, 4, 8})
                                        if ((mask & bit) != 0) {
                                            TileEntityCanopy n =
                                                    TileEntityCanopy.at(
                                                            w,
                                                            TEST.offset(
                                                                    bit == 1
                                                                            ? f.rotateYCCW()
                                                                            : bit == 2
                                                                                    ? f.rotateY()
                                                                                    : bit == 4
                                                                                            ? f
                                                                                            : f
                                                                                                    .getOpposite()));
                                            require(
                                                    (n.mask()
                                                                    & (bit == 1
                                                                            ? 2
                                                                            : bit == 2
                                                                                    ? 1
                                                                                    : bit == 4
                                                                                            ? 8
                                                                                            : 4))
                                                            != 0,
                                                    "reciprocal seam");
                                        }
                                }
                            for (EnumFacing f : EnumFacing.HORIZONTALS)
                                for (String id :
                                        new String[] {"angled_front_1x1", "angled_front_1x2"}) {
                                    clear(w);
                                    TileEntityCanopy front = place(w, block(id), TEST, f),
                                            rear =
                                                    place(
                                                            w,
                                                            block("regular_canopy_glass"),
                                                            TEST.offset(
                                                                    f.getOpposite(),
                                                                    block(id).length),
                                                            f);
                                    require(
                                            (front.mask() & 8) != 0 && (rear.mask() & 4) != 0,
                                            "angled rear join " + id);
                                }
                            for (com.google.gson.JsonElement layout : assemblies())
                                for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                                    clear(w);
                                    buildAssembly(w, layout.getAsJsonObject(), facing);
                                }
                            for (CanopyMesh.Face face :
                                    CanopyMesh.MODELS.get("visor_module_front_h2"))
                                if (face.baseMaterial.equals("metal")) {
                                    boolean low = true, high = true;
                                    for (Vec3d v : face.vertices) {
                                        low &= v.y <= .125 + 1E-8;
                                        high &= v.y >= 1.875 - 1E-8;
                                    }
                                    require(low || high, "tall front has center rail or join post");
                                }
                            System.out.println(
                                    "[vandorlabs][reprolab] canopy-composition PASS layouts=12"
                                            + " facings=4 heights=2 depths=2 widths=3,5,7");
                            EntityPlayerMP player =
                                    mc.getIntegratedServer()
                                            .getPlayerList()
                                            .getPlayerByUUID(mc.player.getUniqueID());
                            ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
                            net.minecraft.world.GameType mode =
                                    player.interactionManager.getGameType();
                            float yaw = player.rotationYaw;
                            boolean flying = player.capabilities.isFlying;
                            try {
                                player.interactionManager.setGameType(
                                        net.minecraft.world.GameType.SURVIVAL);
                                for (EnumFacing facing : EnumFacing.HORIZONTALS)
                                    for (String id : IDS) {
                                        clear(w);
                                        BlockCanopy b = block(id);
                                        w.setBlockState(
                                                TEST.down(), Blocks.STONE.getDefaultState());
                                        player.rotationYaw = facing.getHorizontalAngle();
                                        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(b, 2));
                                        ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
                                        require(
                                                stack.getItem()
                                                                .onItemUse(
                                                                        player,
                                                                        w,
                                                                        TEST.down(),
                                                                        EnumHand.MAIN_HAND,
                                                                        EnumFacing.UP,
                                                                        .5F,
                                                                        1,
                                                                        .5F)
                                                        == EnumActionResult.SUCCESS,
                                                "survival placement " + id);
                                        require(
                                                stack.getCount() == 1
                                                        && TileEntityCanopy.at(w, TEST).facing()
                                                                == facing,
                                                "survival count and facing " + id);
                                        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                                        BlockPos follower =
                                                b.cells(TEST, facing)
                                                        .get(b.cells(TEST, facing).size() - 1);
                                        require(
                                                player.interactionManager.tryHarvestBlock(follower),
                                                "survival harvest " + id);
                                        java.util.List<net.minecraft.entity.item.EntityItem> drops =
                                                w.getEntitiesWithinAABB(
                                                        net.minecraft.entity.item.EntityItem.class,
                                                        new AxisAlignedBB(TEST).grow(5));
                                        drops.removeIf(item -> item.isDead);
                                        require(
                                                drops.size() == 1
                                                        && drops.get(0).getItem().getCount() == 1
                                                        && drops.get(0).getItem().getItem()
                                                                == net.minecraft.item.Item
                                                                        .getItemFromBlock(b),
                                                "one constituent drop " + id);
                                        for (net.minecraft.entity.item.EntityItem item : drops)
                                            item.setDead();
                                        for (BlockPos cell : b.cells(TEST, facing))
                                            require(w.isAirBlock(cell), "survival cleanup " + id);
                                    }
                            } finally {
                                player.setHeldItem(EnumHand.MAIN_HAND, held);
                                player.interactionManager.setGameType(mode);
                                player.capabilities.isFlying = flying;
                                player.sendPlayerAbilities();
                                player.rotationYaw = yaw;
                            }
                            clear(w);
                            place(w, block("opening_slide_1x2"), TEST, EnumFacing.NORTH);
                            System.out.println(
                                    "[vandorlabs][reprolab] canopy-world PASS designs="
                                            + IDS.length
                                            + " facings=4"
                                            + " masks=16 pairing clearance collision persistence"
                                            + " removal survival-placement-drops");
                        })
                .get();
        for (String id : CanopyMesh.MODELS.keySet()) {
            IBakedModel icon = new CanopyModels.Icon(id);
            ItemTransformVec3f gui = icon.getItemCameraTransforms().gui;
            for (BakedQuad q : icon.getQuads(null, null, 0)) {
                int[] v = q.getVertexData();
                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) {
                    Vec3d p =
                            ControlItemPadding.project(
                                            new Vec3d(
                                                    Float.intBitsToFloat(v[i * stride]),
                                                    Float.intBitsToFloat(v[i * stride + 1]),
                                                    Float.intBitsToFloat(v[i * stride + 2])),
                                            gui)
                                    .addVector(.5 + gui.translation.x, .5 + gui.translation.y, 0);
                    require(p.x > .1 && p.x < .9 && p.y > .1 && p.y < .9, "icon bounds " + id);
                }
            }
        }
        System.out.println(
                "[vandorlabs][reprolab] canopy-icons PASS variants=" + CanopyMesh.MODELS.size());
    }

    static void tick(Minecraft mc, File output) {
        mc.player.capabilities.isFlying = true;
        mc.player.motionY = 0;
        try {
            if (pending != null) {
                if (!pending.isDone()) return;
                pending.get();
                pending = null;
            }
            if (++ticks < 20) return;
            if (stage == 0) {
                validate(mc);
                mc.player.noClip = true;
                mc.player.capabilities.isFlying = true;
                mc.player.setPosition(8.5, 100 - mc.player.getEyeHeight() + .6, 5);
                mc.player.rotationYaw = mc.player.prevRotationYaw = 0;
                mc.player.rotationPitch = mc.player.prevRotationPitch = 0;
                stage = 1;
                ticks = 0;
                return;
            }
            if (stage == 1) {
                shot(mc, output, "canopy_closed");
                TileEntityCanopy t = TileEntityCanopy.at(mc.world, TEST);
                require(t != null, "client tile");
                Vec3d start = new Vec3d(8.5, 100.5, 5), end = new Vec3d(8.5, 100.5, 10);
                RayTraceResult hit = t.trace(start, end);
                require(hit != null, "closed trace");
                require(
                        mc.playerController.processRightClickBlock(
                                        mc.player,
                                        mc.world,
                                        hit.getBlockPos(),
                                        hit.sideHit,
                                        hit.hitVec,
                                        EnumHand.MAIN_HAND)
                                == EnumActionResult.SUCCESS,
                        "open packet");
                stage = 2;
                ticks = 0;
                return;
            }
            if (stage == 2) {
                TileEntityCanopy t = TileEntityCanopy.at(mc.world, TEST);
                if (t.progress != 1 && ticks < 120) return;
                require(
                        t.progress == 1,
                        "client open synchronization progress="
                                + t.progress
                                + " target="
                                + t.targetOpen);
                mc.player.setPosition(11, 100, 8.5);
                Vec3d look = new Vec3d(8.5, 100.5, 10.5).subtract(mc.player.getPositionEyes(1));
                mc.player.rotationYaw =
                        mc.player.prevRotationYaw =
                                (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                mc.player.rotationPitch =
                        mc.player.prevRotationPitch =
                                (float)
                                        -Math.toDegrees(
                                                Math.atan2(
                                                        look.y,
                                                        Math.sqrt(
                                                                look.x * look.x
                                                                        + look.z * look.z)));
                stage = 3;
                ticks = 0;
                return;
            }
            if (stage == 3) {
                shot(mc, output, "canopy_open");
                RayTraceResult hit =
                        CanopyInteractions.trace(
                                mc.world, new Vec3d(11, 100.5, 10.5), new Vec3d(6, 100.5, 10.5));
                require(hit != null, "offset trace");
                require(
                        !mc.world
                                .getCollisionBoxes(
                                        mc.player,
                                        new AxisAlignedBB(hit.hitVec, hit.hitVec).grow(.02))
                                .isEmpty(),
                        "offset collision event");
                Vec3d eyes = mc.player.getPositionEyes(1),
                        reach =
                                eyes.add(
                                        mc.player
                                                .getLookVec()
                                                .scale(
                                                        mc.playerController
                                                                .getBlockReachDistance()));
                mc.objectMouseOver = mc.world.rayTraceBlocks(eyes, reach, false, false, true);
                mc.playerController.updateController();
                require(
                        mc.objectMouseOver != null
                                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                                && mc.objectMouseOver.getBlockPos().equals(TEST),
                        "controller offset target");
                hit = mc.objectMouseOver;
                require(
                        mc.playerController.processRightClickBlock(
                                        mc.player,
                                        mc.world,
                                        hit.getBlockPos(),
                                        hit.sideHit,
                                        hit.hitVec,
                                        EnumHand.MAIN_HAND)
                                == EnumActionResult.SUCCESS,
                        "close packet");
                stage = 4;
                ticks = 0;
                return;
            }
            if (stage == 4) {
                require(
                        TileEntityCanopy.at(mc.world, TEST).progress == 0,
                        "client close synchronization");
                pending =
                        mc.getIntegratedServer()
                                .addScheduledTask(
                                        () -> {
                                            World w = mc.getIntegratedServer().getEntityWorld();
                                            clear(w);
                                            for (int i = 0; i < IDS.length; i++)
                                                place(
                                                        w,
                                                        block(IDS[i]),
                                                        TEST.add(i % 6 * 7, 0, i / 6 * 5),
                                                        EnumFacing.NORTH);
                                        });
                mc.player.setPosition(35, 120, -10);
                Vec3d look = new Vec3d(26, 101, 20).subtract(mc.player.getPositionEyes(1));
                mc.player.rotationYaw =
                        mc.player.prevRotationYaw =
                                (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                mc.player.rotationPitch =
                        mc.player.prevRotationPitch =
                                (float)
                                        -Math.toDegrees(
                                                Math.atan2(
                                                        look.y,
                                                        Math.sqrt(
                                                                look.x * look.x
                                                                        + look.z * look.z)));
                stage = 5;
                ticks = 0;
                return;
            }
            if (stage == 5) {
                shot(mc, output, "canopy_designs");
                mc.displayGuiScreen(new Icons());
                stage = 6;
                ticks = 0;
                return;
            }
            if (stage == 6) {
                shot(mc, output, "canopy_icons");
                mc.displayGuiScreen(null);
                pending =
                        mc.getIntegratedServer()
                                .addScheduledTask(
                                        () -> {
                                            World w = mc.getIntegratedServer().getEntityWorld();
                                            clearBoard(w);
                                            for (com.google.gson.JsonElement entry : assemblies()) {
                                                com.google.gson.JsonObject layout =
                                                        entry.getAsJsonObject();
                                                if (layout.get("width").getAsInt() == 7
                                                        && layout.get("height").getAsInt() == 2
                                                        && layout.get("depth").getAsInt() == 3) {
                                                    buildAssembly(w, layout, EnumFacing.NORTH);
                                                    break;
                                                }
                                            }
                                        });
                mc.player.setPosition(17, 106, 3);
                lookAt(mc, new Vec3d(11.5, 101, 10));
                stage = 7;
                ticks = 0;
                return;
            }
            if (stage == 7) {
                shot(mc, output, "canopy_composable");
                System.out.println(
                        "[vandorlabs][reprolab] canopy-live PASS open-close-packets"
                                + " offset-selection screenshots=5");
                stage = 8;
                mc.shutdown();
            }
        } catch (Exception e) {
            throw new IllegalStateException("canopy live stage=" + stage, e);
        }
    }

    private static void shot(Minecraft mc, File output, String name) throws Exception {
        ImageIO.write(
                ScreenShotHelper.createScreenshot(
                        mc.displayWidth, mc.displayHeight, mc.getFramebuffer()),
                "png",
                new File(output, "shot_" + name + ".png"));
    }

    private static final class Icons extends GuiScreen {
        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        @Override
        public void drawScreen(int mx, int my, float partial) {
            drawRect(0, 0, width, height, 0xff253441);
            drawCenteredString(fontRenderer, "Canopy inventory icons", width / 2, 20, 0xffffff);
            RenderHelper.enableGUIStandardItemLighting();
            for (int i = 0; i < IDS.length; i++) {
                int x = width / 2 - 150 + (i % 6) * 60, y = 38 + i / 6 * 38;
                drawRect(x - 1, y - 1, x + 17, y + 17, 0xff607080);
                drawRect(x, y, x + 16, y + 16, 0xff101820);
                itemRender.renderItemAndEffectIntoGUI(new ItemStack(block(IDS[i])), x, y);
                fontRenderer.drawString(
                        fontRenderer.trimStringToWidth(
                                new ItemStack(block(IDS[i])).getDisplayName(), 55),
                        x - 12,
                        y + 20,
                        0xffffff);
            }
            RenderHelper.disableStandardItemLighting();
        }
    }
}
