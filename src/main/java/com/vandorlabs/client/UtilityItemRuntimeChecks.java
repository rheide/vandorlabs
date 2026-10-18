package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Actual inventory rendering, including depth/rotation, with padding on every side. */
final class UtilityItemRuntimeChecks {
    private static final int BACK=0x203040;
    private static final String[] IDS={"programmable_matter_infuser","programmable_matter_block","infuser_core","battery_block","hexagonal_fuel_tank","assembly_press_core","synthesis_tower_core","enclosed_gantry_core","turntable_controller","turntable_surface","landing_plate","landing_ski","gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel","large_landing_wheel"};
    static ItemStack stack(int i){return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+IDS[i])));}
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int i=0;i<IDS.length;i++){
                int x=icons.x(i)*scale,y=icons.y(i)*scale,changed=0;
                for(int a=0;a<size;a++)for(int b=0;b<size;b++){boolean ink=(shot.getRGB(x+a,y+b)&0xffffff)!=BACK;if(ink)changed++;if(ink && (a==0 || b==0 || a==size-1 || b==size-1))throw new IllegalStateException("Inventory icon touches slot edge: "+IDS[i]);}
                if(changed<10 || changed>size*size*.85)throw new IllegalStateException("Inventory icon coverage: "+IDS[i]+" pixels="+changed);
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_utility22_items.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] utility-item-padding PASS all eighteen inventory variants actual rotated pixels");
    }
    static void checkTankRenderState(Minecraft mc){
        net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(146,21,120);
        com.vandorlabs.tiles.TileEntityHexTank tank=(com.vandorlabs.tiles.TileEntityHexTank)mc.world.getTileEntity(pos);
        if(tank==null)throw new IllegalStateException("Tank render fixture has not arrived");
        TEHexTank renderer=new TEHexTank();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        try{
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,80,160);GlStateManager.color(1,1,1,1);renderer.render(tank,0,0,0,0,-1,1);
            if(OpenGlHelper.lastBrightnessX!=80 || OpenGlHelper.lastBrightnessY!=160)throw new IllegalStateException("Tank leaked lightmap state");
        java.nio.FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(16);org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR,color);
            for(int i=0;i<4;i++)if(Math.abs(color.get(i)-1)>.001)throw new IllegalStateException("Tank leaked vertex color");
        }finally{OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.color(1,1,1,1);}
        System.out.println("[vandorlabs][reprolab] utility-render-state PASS color and lightmap restored");
    }
    private static final class Icons extends GuiScreen{
        int x(int i){return width/2+(i%7-3)*30;}int y(int i){return height/2-24+(i/7)*32;}public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int mouseX,int mouseY,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Matter, machinery and modular landing gear",width/2,30,0xffffff);for(int i=0;i<IDS.length;i++){drawRect(x(i)-1,y(i)-1,x(i)+17,y(i)+17,0xff708090);drawRect(x(i),y(i),x(i)+16,y(i)+16,0xff000000|BACK);}RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<IDS.length;i++)itemRender.renderItemAndEffectIntoGUI(stack(i),x(i),y(i));RenderHelper.disableStandardItemLighting();}
    }
}
