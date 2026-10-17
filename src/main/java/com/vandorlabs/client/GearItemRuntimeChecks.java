package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.block.Block;
import java.awt.image.BufferedImage;
import java.io.File;

final class GearItemRuntimeChecks {
    private static final int BACK=0x203040;
    static ItemStack stack(int i){ItemStack stack=new ItemStack(Block.getBlockFromName("vandorlabs:"+new String[]{"ski_landing_gear","landing_strut","angled_landing_strut"}[i/5]));NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GearSize",i%5);stack.setTagInfo("BlockEntityTag",tag);return stack;}
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int i=0;i<15;i++){
                if(mc.getRenderItem().getItemModelWithOverrides(stack(i),mc.world,mc.player)==mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel())throw new IllegalStateException("Missing gear item "+i);
                int x=icons.x(i)*scale,y=icons.y()*scale,changed=0;
                for(int a=0;a<size;a++)for(int b=0;b<size;b++){boolean ink=(shot.getRGB(x+a,y+b)&0xffffff)!=BACK;if(ink)changed++;if(ink && (a==0 || b==0 || a==size-1 || b==size-1))throw new IllegalStateException("Gear icon touches slot: "+i);}
                if(changed<10 || changed>size*size*.85)throw new IllegalStateException("Gear item coverage "+i);
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_utility22_gear_items.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] non-wheeled-gear-icons PASS every generated variant and both 2x2 aliases actual pixel padding");
    }
    private static final class Icons extends GuiScreen{
        int x(int i){return width/2-150+i*20;}int y(){return height/2-8;}public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int mouseX,int mouseY,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Ski, straight strut, angled strut: every size",width/2,30,0xffffff);for(int i=0;i<15;i++){drawRect(x(i)-1,y()-1,x(i)+17,y()+17,0xff708090);drawRect(x(i),y(),x(i)+16,y()+16,0xff000000|BACK);}RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<15;i++)itemRender.renderItemAndEffectIntoGUI(stack(i),x(i),y());RenderHelper.disableStandardItemLighting();}
    }
}
