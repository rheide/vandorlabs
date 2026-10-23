package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

/** Real container, input and server packet checks for the selected leaf's direction. */
final class DiagonalTrapdoorDirectionLiveChecks {
    private static final BlockPos POS=new BlockPos(300,40,146);
    private static int ticks;
    @SuppressWarnings("unchecked") private static java.util.List<GuiButton> buttons(GuiProgrammableTrapdoor gui)throws Exception{
        java.lang.reflect.Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n");return (java.util.List<GuiButton>)field.get(gui);
    }
    private static GuiButton control(GuiProgrammableTrapdoor gui,int id)throws Exception{return buttons(gui).stream().filter(b->b.id==id).findFirst().orElseThrow(()->new IllegalStateException("Missing trapdoor control "+id));}
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(++ticks==1)mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockState(POS,ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR.getDefaultState(),3);
        }).get();
        if(ticks==20)mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR,player.world,POS.getX(),POS.getY(),POS.getZ());
        }).get();
        if(ticks==50){
            if(!(mc.currentScreen instanceof GuiProgrammableTrapdoor))throw new IllegalStateException("Trapdoor server dialog did not open");
            GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;GuiButton rotation=control(gui,9);gui.mouseClicked(rotation.x+8,rotation.y+8,0);GuiButton hinge=control(gui,10);gui.mouseClicked(hinge.x+8,hinge.y+8,0);
        }
        if(ticks==70){
            check(mc,true,1);GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;
            for(int[] size:new int[][]{{320,240},{460,340}}){
                gui.setWorldAndResolution(mc,size[0],size[1]);GuiButton rotation=control(gui,9),done=control(gui,4);
                if(rotation.y+rotation.height>=done.y)throw new IllegalStateException("Rotation control overlaps Done");
                mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(output,"diagonal-trapdoor-direction-"+size[0]+".png"));
            }
            net.minecraft.client.gui.ScaledResolution resolution=new net.minecraft.client.gui.ScaledResolution(mc);gui.setWorldAndResolution(mc,resolution.getScaledWidth(),resolution.getScaledHeight());
            GuiButton rotation=control(gui,9);gui.mouseClicked(rotation.x+8,rotation.y+8,1);GuiButton hinge=control(gui,10);gui.mouseClicked(hinge.x+8,hinge.y+8,1);
        }
        if(ticks==90){check(mc,false,0);GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;GuiButton hinge=control(gui,10);gui.mouseClicked(hinge.x+8,hinge.y+8,1);}
        if(ticks==110){check(mc,false,2);GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;GuiButton hinge=control(gui,10);gui.mouseClicked(hinge.x+8,hinge.y+8,0);}
        if(ticks==130){check(mc,false,0);mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] diagonal-trapdoor-direction-dialog PASS real container left/right input server packets client synchronization compact and large layouts");return true;}
        return ticks>130;
    }
    private static void check(Minecraft mc,boolean expected,int hinge)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityProgrammableDiagonalTrapdoor tile=(TileEntityProgrammableDiagonalTrapdoor)mc.getIntegratedServer().getWorld(0).getTileEntity(POS);
            if(tile.getHingeSide()!=hinge)throw new IllegalStateException("Trapdoor hinge packet did not apply");
            if(tile.isRotationFlipped()!=expected)throw new IllegalStateException("Trapdoor direction packet did not apply");
        }).get();
        if(((TileEntityProgrammableDiagonalTrapdoor)mc.world.getTileEntity(POS)).getHingeSide()!=hinge)throw new IllegalStateException("Trapdoor hinge did not synchronize");
        if(((TileEntityProgrammableDiagonalTrapdoor)mc.world.getTileEntity(POS)).isRotationFlipped()!=expected)throw new IllegalStateException("Trapdoor direction did not synchronize");
    }
}
