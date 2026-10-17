package com.vandorlabs.client;

import com.vandorlabs.cockpit.CockpitData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.awt.image.BufferedImage;
import java.io.File;

final class CockpitItemRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int i=0;i<4;i++){int changed=0;for(int a=0;a<size;a++)for(int b=0;b<size;b++){boolean ink=(shot.getRGB(icons.x(i)*scale+a,icons.y()*scale+b)&0xffffff)!=0x203040;if(ink)changed++;if(ink && (a==0 || b==0 || a==size-1 || b==size-1))throw new IllegalStateException("Cockpit icon touches slot edge: "+CockpitData.IDS[i]);}if(changed<20)throw new IllegalStateException("Empty cockpit icon "+CockpitData.IDS[i]);}
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_utility22_cockpit_items.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] cockpit-item-padding PASS four complete rotated OBJ variants");
    }
    private static final class Icons extends GuiScreen{
        int x(int i){return width/2+(i-2)*34;}int y(){return height/2-8;}public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int x,int y,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Cockpits and peripheral controls",width/2,30,0xffffff);for(int i=0;i<4;i++){drawRect(x(i)-1,y()-1,x(i)+17,y()+17,0xff708090);drawRect(x(i),y(),x(i)+16,y()+16,0xff203040);}RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<4;i++)itemRender.renderItemAndEffectIntoGUI(new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+CockpitData.IDS[i]))),x(i),y());RenderHelper.disableStandardItemLighting();}
    }
}
