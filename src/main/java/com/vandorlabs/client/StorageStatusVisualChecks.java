package com.vandorlabs.client;

import com.vandorlabs.container.ContainerStorageStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.io.File;

final class StorageStatusVisualChecks {
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen old=mc.currentScreen;
        try{for(int index=0;index<2;index++){
            TileEntity tile=mc.world.getTileEntity(new BlockPos(index==0?144:146,21,120));if(tile==null)throw new IllegalStateException("Storage status fixture missing");
            ContainerStorageStatus data=new ContainerStorageStatus(tile);if(data.values[0]<=0||data.values[0]>=data.values[1])throw new IllegalStateException("Storage meter needs partial contents");
            if(index==1){com.vandorlabs.GuiHandler routes=new com.vandorlabs.GuiHandler();for(int id:new int[]{com.vandorlabs.GuiHandler.GUI_STORAGE_STATUS,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR})if(!(routes.getClientGuiElement(id,mc.player,mc.world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()) instanceof GuiStorageStatus))throw new IllegalStateException("Tank entry points must open the same settings dialog");}
            for(int[] size:new int[][]{{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}}){
                GuiStorageStatus gui=new GuiStorageStatus(data);mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_storage_meter_"+index+"_"+size[0]+".png"));
            }
        }}finally{mc.displayGuiScreen(old);}
        for(String texture:new String[]{"port","tank_empty"})if(mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/matter/"+texture)==mc.getTextureMapBlocks().getMissingSprite())throw new IllegalStateException("Missing tank sprite "+texture);
        for(boolean block:new boolean[]{false,true})for(int amount:new int[]{1,4000,8000,12000,16000}){
            double area=TEHexTank.area(TEHexTank.fluidOutline(block,amount)),full=TEHexTank.area(TEHexTank.fluidOutline(block,16000));if(Math.abs(area/full-amount/16000D)>1e-8)throw new IllegalStateException("Horizontal tank fill volume must match stored amount");
            for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.HORIZONTALS)for(double[] p:TEHexTank.fluidOutline(block,amount)){double[] oriented=TEHexTank.orient(new double[]{p[0],.2,p[1]},facing);if(Math.abs(oriented[1]-p[1])>1e-8)throw new IllegalStateException("Horizontal tank fluid must remain level under gravity");}
        }
        System.out.println("[vandorlabs][reprolab] storage-meters PASS partial RF and pooled fluid, three resolutions, registered windows and ports");
    }
}
