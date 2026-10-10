package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.File;
import javax.imageio.ImageIO;

/** Real-world seating contracts and baked OBJ/icon checks. */
public final class PilotSeatRuntimeChecks {
    private static final BlockPos POS = new BlockPos(8, 100, 8);
    private static int ticks;
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("pilot-seat: " + message);
    }
    public static void check(Minecraft mc) {
        try { mc.getIntegratedServer().addScheduledTask(() -> {
            World w = mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            BlockPilotSeat b = (BlockPilotSeat)ModBlocks.PILOT_SEAT;
            Vec3d old = p.getPositionVector();
            p.dismountRidingEntity();
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                IBlockState s = b.getDefaultState().withProperty(BlockPilotSeat.FACING, f);
                w.setBlockState(POS.up(), Blocks.GLASS.getDefaultState(), 3);
                require(!b.canPlaceBlockAt(w, POS), "backrest clearance");
                w.setBlockToAir(POS.up());
                require(b.canPlaceBlockAt(w, POS), "clear placement");
                w.setBlockState(POS, s, 3);
                b.onBlockPlacedBy(w, POS, s, p, new ItemStack(b));
                require(w.getBlockState(POS.up()).getBlock() == b && w.getBlockState(POS.up()).getValue(BlockPilotSeat.UPPER), "upper reservation");
                require(b.getStateFromMeta(b.getMetaFromState(w.getBlockState(POS.up()))) == w.getBlockState(POS.up()), "upper state persistence");
                AxisAlignedBB lowerBounds = s.getBoundingBox(w, POS);
                AxisAlignedBB upperBounds = w.getBlockState(POS.up()).getBoundingBox(w, POS.up());
                require(lowerBounds.maxY == 1 && upperBounds.maxY == .75, "full-size collision height");
                p.setSneaking(false);
                b.onBlockActivated(w, POS, s, p, EnumHand.MAIN_HAND, f, .5F, .3F, .5F);
                require(p.getRidingEntity() instanceof EntityChairSeat, "mount " + f);
                EntityChairSeat seat = (EntityChairSeat)p.getRidingEntity();
                require(POS.equals(seat.getChairPos()), "mount owner");
                require(Math.abs(seat.posY - (POS.getY() + BlockPilotSeat.SEAT_HEIGHT + .35 - EntityChairSeat.RIDER_PELVIS_OFFSET)) < 1e-6, "cushion height");
                seat.onUpdate();
                require(!seat.isDead, "seat survives mount tick");
                b.onBlockActivated(w, POS, s, p, EnumHand.MAIN_HAND, f, .5F, .3F, .5F);
                require(p.getRidingEntity() == seat, "repeat click keeps mount");
                b.onBlockActivated(w, POS.up(), w.getBlockState(POS.up()), p, EnumHand.MAIN_HAND, f, .5F, .3F, .5F);
                require(p.getRidingEntity() == seat, "upper click uses same mount");
                p.dismountRidingEntity();
                seat.ticksExisted = 6; seat.onUpdate();
                require(seat.isDead, "empty mount cleanup");
                b.onBlockActivated(w, POS, s, p, EnumHand.MAIN_HAND, f, .5F, .3F, .5F);
                seat = (EntityChairSeat)p.getRidingEntity();
                w.setBlockToAir(POS.up());
                require(w.isAirBlock(POS), "breaking upper removes lower");
                require(seat.isDead && !p.isRiding(), "breaking occupied seat cleans up");
                w.setBlockToAir(POS.up());
            }
            p.setPositionAndUpdate(old.x, old.y, old.z);
            System.out.println("[vandorlabs][reprolab] pilot-seat-world PASS four-facings mount dismount break upper-cell");
        }).get(); } catch(Exception e) { throw new IllegalStateException(e); }
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
            IBakedModel m = mc.getBlockRendererDispatcher().getModelForState(ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING, f));
            require(m != mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(), "world OBJ " + f);
            require(m.getQuads(null, null, 0).size() > 100, "OBJ geometry " + f);
            IBakedModel upper = mc.getBlockRendererDispatcher().getModelForState(ModBlocks.PILOT_SEAT.getDefaultState()
                    .withProperty(BlockPilotSeat.FACING, f).withProperty(BlockPilotSeat.UPPER, true));
            require(upper != mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(), "upper model " + f);
            require(upper.getQuads(null, null, 0).isEmpty(), "upper must not duplicate seat mesh");
        }
        IBakedModel icon = mc.getRenderItem().getItemModelWithOverrides(new ItemStack(ModBlocks.PILOT_SEAT), mc.world, mc.player);
        require(icon.getQuads(null, null, 0).size() > 100, "inventory geometry");
        for (BakedQuad q : icon.getQuads(null, null, 0))
            require(!q.getSprite().getIconName().contains("missingno"), "material " + q.getSprite().getIconName());
        require(icon.getParticleTexture() != mc.getTextureMapBlocks().getMissingSprite(), "particle texture");
        System.out.println("[vandorlabs][reprolab] pilot-seat-model PASS four-facings inventory textures");
    }
    public static void tick(Minecraft mc, File output) {
        try {
            if (++ticks == 1) { check(mc); mc.displayGuiScreen(new Icons()); }
            if (ticks == 20) mc.displayGuiScreen(new Icons());
            if (ticks == 30) {
                require(mc.currentScreen instanceof Icons, "icon screen remains open");
                shot(mc, output, "icons");
                mc.displayGuiScreen(null);
                mc.getIntegratedServer().addScheduledTask(() -> {
                    World w = mc.getIntegratedServer().getWorld(0);
                    w.setWorldTime(6000);
                    for (BlockPos p : BlockPos.getAllInBox(POS.add(-4,-1,-4), POS.add(10,-1,5)))
                        w.setBlockState(p, Blocks.STONE.getDefaultState(), 3);
                    for (int i=0; i<4; i++) {
                        IBlockState s = ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING, EnumFacing.HORIZONTALS[i]);
                        w.setBlockState(POS.east(i*2), s, 3);
                        w.setBlockState(POS.east(i*2).up(), s.withProperty(BlockPilotSeat.UPPER, true), 3);
                    }
                    EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    p.capabilities.isFlying=true;
                    p.sendPlayerAbilities();
                    p.connection.setPlayerLocation(11.5,101.5,3,0,20);
                }).get();
                mc.player.setPositionAndRotation(11.5,101.5,3,0,20);
                mc.gameSettings.hideGUI=true;
            }
            if (ticks == 150) {
                require(mc.world.getBlockState(POS).getBlock()==ModBlocks.PILOT_SEAT,"visible fixture received");
                shot(mc, output, "facings");
                mc.getIntegratedServer().addScheduledTask(() -> {
                    World w=mc.getIntegratedServer().getWorld(0);
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    w.setBlockState(POS, ModBlocks.PILOT_SEAT.getDefaultState(), 3);
                    ModBlocks.PILOT_SEAT.onBlockActivated(w,POS,w.getBlockState(POS),p,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.3F,.5F);
                    p.connection.setPlayerLocation(p.posX,p.posY,p.posZ,180,0);
                }).get();
                mc.gameSettings.thirdPersonView=2;
            }
            if (ticks == 210) {
                require(mc.player.isRiding(), "client receives mounted passenger");
                shot(mc, output, "mounted");
                System.out.println("[vandorlabs][reprolab] pilot-seat-live PASS");
                mc.shutdown();
            }
        } catch(Exception e) { throw new IllegalStateException("pilot seat live", e); }
    }
    private static void shot(Minecraft mc, File output, String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer()),
                "png", new File(output, "shot_pilot_seat_"+name+".png"));
    }
    private static final class Icons extends GuiScreen {
        @Override public boolean doesGuiPauseGame() { return false; }
        @Override public void drawScreen(int x, int y, float partial) {
            drawRect(0, 0, width, height, 0xff253441);
            drawCenteredString(fontRenderer, "Pilot Seat", width/2, height/2-35, 0xffffff);
            int px=width/2-8, py=height/2;
            drawRect(px-1, py-1, px+17, py+17, 0xff607080);
            drawRect(px, py, px+16, py+16, 0xff101820);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(new ItemStack(ModBlocks.PILOT_SEAT), px, py);
            RenderHelper.disableStandardItemLighting();
        }
    }
}
