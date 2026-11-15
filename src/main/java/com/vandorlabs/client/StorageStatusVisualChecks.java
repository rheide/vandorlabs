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
        int[][] sizes={{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}};
        try{for(int index=0;index<2;index++){
            TileEntity tile=mc.world.getTileEntity(new BlockPos(index==0?144:146,21,120));if(tile==null)throw new IllegalStateException("Storage status fixture missing");
            ContainerStorageStatus data=new ContainerStorageStatus(tile);if(data.values[0]<=0||data.values[0]>=data.values[1])throw new IllegalStateException("Storage meter needs partial contents");
            if(index==0){
                com.vandorlabs.GuiHandler routes=new com.vandorlabs.GuiHandler();
                for(int id:new int[]{com.vandorlabs.GuiHandler.GUI_STORAGE_STATUS,com.vandorlabs.GuiHandler.GUI_RESOURCE_PORT})if(!(routes.getClientGuiElement(id,mc.player,mc.world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()) instanceof GuiResourcePort))throw new IllegalStateException("Battery entry points must open the shared Port settings dialog");
            }
            if(index==1){com.vandorlabs.GuiHandler routes=new com.vandorlabs.GuiHandler();for(int id:new int[]{com.vandorlabs.GuiHandler.GUI_STORAGE_STATUS,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR})if(!(routes.getClientGuiElement(id,mc.player,mc.world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()) instanceof GuiStorageStatus))throw new IllegalStateException("Tank entry points must open the same settings dialog");}
            for(int[] size:sizes){
                net.minecraft.client.gui.inventory.GuiContainer gui;
                if(index==0){
                    com.vandorlabs.ports.ResourcePortContainer ports=new com.vandorlabs.ports.ResourcePortContainer((com.vandorlabs.tiles.TileEntityBattery)tile);ports.batteryValues[0]=data.values[0];
                    GuiResourcePort battery=new GuiResourcePort(ports);gui=battery;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
                    GuiButton housing=null;for(GuiButton button:battery.controls())if(button.id==102)housing=button;
                    if(housing==null)throw new IllegalStateException("Battery Housing tab missing");
                    battery.mouseClicked(housing.x+housing.width/2,housing.y+housing.height/2,0);
                    if(!battery.controlsFit()||net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiResourcePort.class,battery,"textures")==null)throw new IllegalStateException("Battery Housing picker/control bounds");
                    if(battery.controls().stream().noneMatch(button->button.id==40&&button.displayString.startsWith("Shape: ")))throw new IllegalStateException("Battery Housing shape control missing");
                }else{gui=new GuiStorageStatus(data);mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);GuiOptionCycleRuntimeChecks.check(mc);}
                mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();
                // The fixture forces logical resolutions; project those coordinates instead of
                // the current GUI scale (Battery may temporarily change that scale to fit).
                net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.loadIdentity();
                net.minecraft.client.renderer.GlStateManager.ortho(0,gui.width,gui.height,0,1000,3000);
                net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.translate(0,0,-2000);
                gui.drawScreen(-1,-1,0);
                int dialogWidth=(Integer)net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,gui,"xSize","field_146999_f");
                int dialogHeight=(Integer)net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,gui,"ySize","field_147000_g");
                if(index==1&&(dialogWidth>354||dialogHeight>238))throw new IllegalStateException("Tank dialog is not compact");
                if(index==1&&net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiStorageStatus.class,(GuiStorageStatus)gui,"textures")==null)throw new IllegalStateException("Storage casing picker missing");
                java.util.List<?> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                java.util.List<java.awt.Rectangle> occupied=new java.util.ArrayList<>();boolean layout=false,shape=false;
                for(Object raw:buttons){GuiButton b=(GuiButton)raw;java.awt.Rectangle r=new java.awt.Rectangle(b.x,b.y,b.width,b.height);if(r.x<0||r.y<0||r.x+r.width>gui.width||r.y+r.height>gui.height)throw new IllegalStateException("Storage button outside screen");for(java.awt.Rectangle prior:occupied)if(prior.intersects(r))throw new IllegalStateException("Storage buttons overlap");occupied.add(r);layout|=b.id==2&&b.displayString.startsWith("Texture: ");}
                for(Object raw:buttons){GuiButton b=(GuiButton)raw;shape|=b.id==1&&b.displayString.startsWith("Shape: ");}
                if(index==1&&!shape)throw new IllegalStateException("Storage shape control missing");
                if(index==1&&!layout)throw new IllegalStateException("Tank Tile/Fit control missing");
                java.awt.image.BufferedImage capture=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int glyphPixels=0;
                for(int y=0;y<capture.getHeight();y++)for(int x=0;x<capture.getWidth();x++){int pixel=capture.getRGB(x,y),r=pixel>>16&255,g=pixel>>8&255,b=pixel&255;if(Math.min(r,Math.min(g,b))>170&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<25)glyphPixels++;}
                javax.imageio.ImageIO.write(capture,"png",new File(output,"shot_storage_meter_"+index+"_"+size[0]+".png"));
                if(glyphPixels<100)throw new IllegalStateException("Storage capture has no visible dialog text: "+index+" "+size[0]+"x"+size[1]+" pixels="+glyphPixels);
                System.out.println("[vandorlabs][reprolab] storage-meter-capture PASS kind="+index+" requested="+size[0]+"x"+size[1]+" logical="+gui.width+"x"+gui.height+" glyph-pixels="+glyphPixels);
                mc.displayGuiScreen(null); // Restore a temporary Battery GUI scale before the next case.
                mc.entityRenderer.setupOverlayRendering();
            }
        }}finally{mc.displayGuiScreen(old);mc.entityRenderer.setupOverlayRendering();}
        for(String texture:new String[]{"port","tank_empty"})if(mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/matter/"+texture)==mc.getTextureMapBlocks().getMissingSprite())throw new IllegalStateException("Missing tank sprite "+texture);
        for(boolean block:new boolean[]{false,true})for(int amount:new int[]{1,4000,8000,12000,16000}){
            double area=TEHexTank.area(TEHexTank.fluidOutline(block,amount)),full=TEHexTank.area(TEHexTank.fluidOutline(block,16000));if(Math.abs(area/full-amount/16000D)>1e-8)throw new IllegalStateException("Horizontal tank fill volume must match stored amount");
            for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.HORIZONTALS)for(double[] p:TEHexTank.fluidOutline(block,amount)){double[] oriented=TEHexTank.orient(new double[]{p[0],.2,p[1]},facing);if(Math.abs(oriented[1]-p[1])>1e-8)throw new IllegalStateException("Horizontal tank fluid must remain level under gravity");}
        }
        for(boolean block:new boolean[]{false,true}){
            java.lang.reflect.Field field=TEHexTank.class.getDeclaredField(block?"BLOCK":"OUTLINE");field.setAccessible(true);double[][] outline=(double[][])field.get(null);
            for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())for(int edge=0;edge<outline.length;edge++){
                double[] w=TEHexTank.window(block,edge,face);if(w==null)continue;double[] a=outline[edge],b=outline[(edge+1)%outline.length];
                double width=Math.hypot(b[0]-a[0],b[1]-a[1])*(w[1]-w[0]),height=w[3]-w[2];
                if(Math.abs(width-height)>1e-9||Math.abs(width-.25)>1e-9)throw new IllegalStateException("Tank windows must be physical squares");
            }
            double[] fit=TEHexTank.casingUV(outline,0,1,0,false,1),tile=TEHexTank.casingUV(outline,0,1,0,true,1);
            if(fit[0]!=16||Math.abs(tile[0]-Math.hypot(outline[1][0]-outline[0][0],outline[1][1]-outline[0][1])*16)>1e-9)throw new IllegalStateException("Tank Tile/Fit UV scale");
        }
        for(net.minecraft.util.EnumFacing cap:net.minecraft.util.EnumFacing.values()){
            double[] center={.5,.5,.5},right=center.clone(),up=center.clone();
            if(cap==net.minecraft.util.EnumFacing.NORTH)right[0]+=-.25;else if(cap==net.minecraft.util.EnumFacing.SOUTH||cap.getAxis()==net.minecraft.util.EnumFacing.Axis.Y)right[0]+=.25;else right[2]+=cap==net.minecraft.util.EnumFacing.EAST?-.25:.25;
            if(cap==net.minecraft.util.EnumFacing.UP)up[2]-=.25;else if(cap==net.minecraft.util.EnumFacing.DOWN)up[2]+=.25;else up[1]+=.25;
            double[] c=TEBattery.portUV(center,cap),r=TEBattery.portUV(right,cap),u=TEBattery.portUV(up,cap);
            if(c[0]!=8||c[1]!=8||r[0]!=12||r[1]!=8||u[0]!=8||u[1]!=4)throw new IllegalStateException("Battery port lettering rotated or mirrored on "+cap);
        }
        System.out.println("[vandorlabs][reprolab] storage-meters PASS partial RF in Battery Housing and pooled fluid, three resolutions, shared entry points, registered windows and ports");
    }
}
