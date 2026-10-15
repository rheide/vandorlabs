package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import java.io.File;
import java.util.*;

/** Actual atlas pixels, transformed model bounds and framebuffer padding in ordinary item slots. */
final class VehicleControllerItemChecks {
    private static final String[] IDS={"autopilot","waypoint","vehicle_controller"};
    private static void require(boolean ok,String what){if(!ok)throw new IllegalStateException("controller item icons: "+what);}
    static void run(Minecraft mc,File output)throws Exception{
        Set<String> sprites=new HashSet<>();java.util.List<int[]> pixels=new ArrayList<>();
        TextureAtlasSprite ramp=mc.getBlockRendererDispatcher().getModelForState(ModBlocks.PROGRAMMABLE_RAMP.getDefaultState()).getParticleTexture();
        for(String id:IDS){
            Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null,"registered "+id);ItemStack stack=new ItemStack(block);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);TextureAtlasSprite primary=model.getParticleTexture();
            require(!primary.getIconName().contains("missingno") && !primary.getIconName().equals(ramp.getIconName()),"dedicated nonmissing sprite "+id+" actual="+primary.getIconName());
            require(sprites.add(primary.getIconName()) && primary.getFrameCount()>0,"three distinct loaded sprites "+id);
            int[] current=primary.getFrameTextureData(0)[0];for(int[] old:pixels)require(!Arrays.equals(old,current),"distinct actual primary texture pixels "+id);pixels.add(current.clone());
            ItemTransformVec3f gui=model.getItemCameraTransforms().gui;int quads=0;
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,side,0)){
                require(!quad.getSprite().getIconName().contains("missingno") && !quad.getSprite().getIconName().equals(ramp.getIconName()),"all rendered faces use controller assets "+id);quads++;
                int[] data=quad.getVertexData();int stride=data.length/4;
                for(int vertex=0;vertex<4;vertex++){Vec3d point=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[vertex*stride]),Float.intBitsToFloat(data[vertex*stride+1]),Float.intBitsToFloat(data[vertex*stride+2])),gui).addVector(gui.translation.x,gui.translation.y,0);require(Math.abs(point.x)<.49 && Math.abs(point.y)<.49,"projected icon padding "+id+" point="+point);}
            }
            require(quads>=6,"actual cube model faces "+id);
        }
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();
        try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            java.awt.image.BufferedImage screenshot=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            int scale=new ScaledResolution(mc).getScaleFactor();
            for(int icon=0;icon<3;icon++){
                int x=icons.x(icon)*scale,y=icons.y()*scale,size=16*scale,changed=0;
                for(int offset=0;offset<size;offset++){
                    require((screenshot.getRGB(x+offset,y)&0xffffff)==Icons.SLOT && (screenshot.getRGB(x+offset,y+size-1)&0xffffff)==Icons.SLOT,"actual top/bottom slot padding "+IDS[icon]+" offset="+offset);
                    require((screenshot.getRGB(x,y+offset)&0xffffff)==Icons.SLOT && (screenshot.getRGB(x+size-1,y+offset)&0xffffff)==Icons.SLOT,"actual left/right slot padding "+IDS[icon]+" offset="+offset);
                }
                for(int a=0;a<size;a++)for(int b=0;b<size;b++)if((screenshot.getRGB(x+a,y+b)&0xffffff)!=Icons.SLOT)changed++;
                require(changed>8*scale*scale,"item actually rendered inside slot "+IDS[icon]);
            }
            javax.imageio.ImageIO.write(screenshot,"png",new File(output,"shot_vehicle_controller_item_icons.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] controller-item-icons PASS Autopilot Waypoint VehicleController dedicated-atlas distinct-primary-pixels transformed-model actual16px-slot every-edge-padding screenshot");
    }
    private static final class Icons extends GuiScreen {
        static final int SLOT=0x203040;
        int x(int index){return width/2+(index-1)*92-8;}int y(){return height/2-8;}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int mouseX,int mouseY,float partial){
            GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Vehicle control items: standard 16px slots",width/2,30,0xffffff);
            for(int i=0;i<3;i++){int x=x(i),y=y();drawRect(x-1,y-1,x+17,y+17,0xff708090);drawRect(x,y,x+16,y+16,0xff000000|SLOT);}
            RenderHelper.enableGUIStandardItemLighting();
            for(int i=0;i<3;i++){GlStateManager.color(1,1,1,1);itemRender.renderItemAndEffectIntoGUI(new ItemStack(Block.getBlockFromName("vandorlabs:"+IDS[i])),x(i),y());}
            RenderHelper.disableStandardItemLighting();String[] labels={"Autopilot","Waypoint","Vehicle Controller"};for(int i=0;i<3;i++)drawCenteredString(fontRenderer,labels[i],x(i)+8,y()+24,0xffffff);
        }
    }
    private VehicleControllerItemChecks(){}
}
