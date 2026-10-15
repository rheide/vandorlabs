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
        return place(w,b,p,f,0);
    }
    private static TileEntityCanopy place(World w,BlockCanopy b,BlockPos p,EnumFacing f,int motion) {
        IBlockState s = b.getDefaultState().withProperty(BlockCanopy.FACING, f);
        require(b.fits(w, p, f), "fixture fit " + b.kind);
        w.setBlockState(p, s, 2);
        ItemStack stack=new ItemStack(b);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("CanopyMotion",motion);stack.setTagCompound(settings);b.onBlockPlacedBy(w,p,s,null,stack);
        return TileEntityCanopy.at(w, p);
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
                                                TEST.offset(f.getOpposite(), b.length)
                                                        .up(b.kind.contains("hinge") ? 1 : 0);
                                        require(!b.cells(TEST,f).contains(obstacle), "rear bodywork lies outside the owned footprint " + id);
                                        w.setBlockState(obstacle, Blocks.STONE.getDefaultState());
                                        t.toggle();
                                        require(t.targetOpen && !t.removing && TileEntityCanopy.at(w,TEST)==t, "rear bodywork does not block opening " + id);
                                        for (int i = 0; i < 16; i++) t.update();
                                        require(t.progress == 1 && w.getBlockState(obstacle).getBlock()==Blocks.STONE, "opening preserves rear bodywork " + id);
                                        t.toggle();
                                        for (int i = 0; i < 16; i++) t.update();
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
                                    BlockCanopy b = block("canopy");
                                    TileEntityCanopy a = place(w,b,TEST,f,mode.equals("slide")?0:1),
                                            right=place(w,b,TEST.offset(f.rotateY()),f,mode.equals("slide")?0:1),
                                            third=place(w,b,TEST.offset(f.rotateY(),2),f,mode.equals("slide")?0:1);
                                    require(
                                            a.width() == 3
                                                    && right.owner() == a
                                                    && !right.draws()
                                                    && third.owner()==a,
                                            "pair lock " + mode);
                                    require(
                                            a.model().equals("canopy"),
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
                                        player.rotationYaw = facing.getHorizontalAngle();
                                        // Centered vertical windows extend below their clicked center.
                                        // Support them from the rear so the clicked block is outside the shell.
                                        boolean large=com.vandorlabs.canopy.LargeCanopySettings.supports(b.kind);
                                        EnumFacing clickSide=large?b.placementFacing(player).getOpposite():EnumFacing.UP;
                                        BlockPos clicked=large?TEST.offset(b.placementFacing(player)):TEST.down();
                                        w.setBlockState(clicked,Blocks.STONE.getDefaultState());
                                        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(b, 2));
                                        ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
                                        require(
                                                stack.getItem()
                                                                .onItemUse(
                                                                        player,
                                                                        w,
                                                                        clicked,
                                                                        EnumHand.MAIN_HAND,
                                                                        clickSide,
                                                                        .5F,
                                                                        1,
                                                                        .5F)
                                                        == EnumActionResult.SUCCESS,
                                                "survival placement " + id);
                                        require(
                                                stack.getCount() == 1
                                                        && TileEntityCanopy.at(w, TEST).facing()
                                                                == b.placementFacing(player),
                                                "survival count and facing " + id);
                                        if(com.vandorlabs.canopy.CanopyVariants.centered(b.kind) && !com.vandorlabs.canopy.LargeCanopySettings.supports(b.kind)) {
                                            Vec3d center=TileEntityCanopy.at(w,TEST).owner().selectionBounds().getCenter();
                                            require(Math.abs(center.x-TEST.getX()-.5)<1e-6 && Math.abs(center.z-TEST.getZ()-.5)<1e-6,"shell centers on clicked block "+id);
                                        }
                                        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                                        BlockPos follower =
                                                b.cells(b.placementAnchor(TEST,b.placementFacing(player)), b.placementFacing(player))
                                                        .get(b.cells(b.placementAnchor(TEST,b.placementFacing(player)), b.placementFacing(player)).size() - 1);
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
                                        require(w.getBlockState(clicked).getBlock()==Blocks.STONE,"placement and harvest preserve support "+id);
                                        for (net.minecraft.entity.item.EntityItem item : drops)
                                            item.setDead();
                                        for (BlockPos cell : b.cells(b.placementAnchor(TEST,b.placementFacing(player)), b.placementFacing(player)))
                                            require(w.isAirBlock(cell), "survival cleanup " + id);
                                    }
                            } finally {
                                player.setHeldItem(EnumHand.MAIN_HAND, held);
                                player.interactionManager.setGameType(mode);
                                player.capabilities.isFlying = flying;
                                player.sendPlayerAbilities();
                                player.rotationYaw = yaw;
                            }
                            CanopyAssemblyRuntimeChecks.check(w);
                            CanopyReleaseRuntimeChecks.run((EntityPlayerMP)w.playerEntities.get(0));
                            LargeCanopyRuntimeChecks.run(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));
                            clear(w);
                            place(w, block("canopy"), TEST, EnumFacing.NORTH);
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
            if(stage==9){if(CanopyTargetedHarvestClientChecks.tick(mc)){System.out.println("[vandorlabs][reprolab] canopy-live PASS open-close-packets offset-selection side-surfaces screenshots=6 targeted-harvest");stage=10;mc.shutdown();}return;}
            if (++ticks < 20) return;
            if (stage == 0) {
                validate(mc);
                LargeCanopyRuntimeChecks.icons(mc);
                mc.player.noClip = true;
                mc.player.capabilities.isFlying = true;
                mc.player.setPosition(8.5, 100 - mc.player.getEyeHeight() + .6, 5);
                mc.player.rotationYaw = mc.player.prevRotationYaw = 0;
                mc.player.rotationPitch = mc.player.prevRotationPitch = 0;
                synchronizeActor(mc);
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
                requireServerCamera(mc);
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
                synchronizeActor(mc);
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
                requireServerCamera(mc);
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
                TileEntityCanopy t = TileEntityCanopy.at(mc.world, TEST);
                if (t != null && t.progress != 0 && ticks < 120) return;
                require(
                        t != null && t.progress == 0 && !t.targetOpen,
                        "client close synchronization progress="
                                + (t == null ? "missing" : t.progress)
                                + " target="
                                + (t == null ? "missing" : t.targetOpen));
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
                mc.getIntegratedServer().addScheduledTask(()->{World w=mc.getIntegratedServer().getEntityWorld();clear(w);place(w,block("canopy"),TEST,EnumFacing.NORTH);}).get();
                mc.gameSettings.hideGUI=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.player.setPosition(11.5,99.2,7.2);lookAt(mc,new Vec3d(8.5,100.5,9));stage=7;ticks=0;return;
            }
            if(stage==7){shot(mc,output,"canopy_side_trim");mc.player.setPosition(11.4,99.2,7.3);lookAt(mc,new Vec3d(8.5,100.5,9));stage=8;ticks=0;return;}
            if(stage==8){shot(mc,output,"canopy_side_trim_shifted");stage=9;ticks=0;}
        } catch (Exception e) {
            throw new IllegalStateException("canopy live stage=" + stage, e);
        }
    }

    /** Server fixtures can leave a queued teleport; establish the actual packet actor after them. */
    private static void synchronizeActor(Minecraft mc){
        mc.player.setSneaking(false);mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        final double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;final float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
        pending=mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(x,y,z,yaw,pitch);
        });
    }
    private static void requireServerCamera(Minecraft mc)throws Exception{
        final Vec3d eye=mc.player.getPositionEyes(1);final float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            require(player.getHeldItemMainhand().isEmpty() && !player.isSneaking(),"manual canopy click requires server empty hand and standing input");
            require(player.getPositionEyes(1).squareDistanceTo(eye)<.04 && Math.abs(MathHelper.wrapDegrees(player.rotationYaw-yaw))<1 && Math.abs(player.rotationPitch-pitch)<1,
                    "packet actor/camera mismatch server="+player.getPositionEyes(1)+" client="+eye+" serverYaw="+player.rotationYaw+" clientYaw="+yaw);
        }).get();
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
