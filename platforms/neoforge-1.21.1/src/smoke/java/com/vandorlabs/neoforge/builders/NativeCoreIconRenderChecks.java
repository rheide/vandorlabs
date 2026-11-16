package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.builders.client.NativeBuilderModelCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Actual native ItemBlock rendering at enlarged and ordinary logical16px inventory size. */
final class NativeCoreIconRenderChecks {
    private static int top;
    static void render(GuiGraphics gui){var mc=Minecraft.getInstance();int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();top=(height-110)/2;com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);gui.fill(0,0,width,height,0xff0d1118);gui.flush();
        for(int scale:new int[]{4,1}){int left=(width-60*scale)/2,y=scale==4?top:top+90;gui.pose().pushPose();try{gui.pose().translate(left,y,350);gui.pose().scale(scale,scale,scale);for(int i=0;i<3;i++)gui.renderItem(new ItemStack(NativeBuilderBindings.CORES.get(NativeBuilderModelCache.IDS.get(i))),i*20+2,2);}finally{gui.pose().popPose();}gui.flush();}
    }
    static void capture()throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/core-icons-native3.png"));double sx=image.getWidth()/(double)mc.getWindow().getGuiScaledWidth(),sy=image.getHeight()/(double)mc.getWindow().getGuiScaledHeight();
        for(int scale:new int[]{4,1})for(int i=0;i<3;i++){int left=(mc.getWindow().getGuiScaledWidth()-60*scale)/2,yTop=scale==4?top:top+90;int x0=(int)Math.round((left+i*20*scale)*sx),y0=(int)Math.round(yTop*sy),w=(int)Math.round(20*scale*sx),h=(int)Math.round(20*scale*sy),count=0,minX=w,maxX=-1,minY=h,maxY=-1;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((image.getPixelRGBA(x0+x,y0+y)&0xffffff)!=0x18110d){count++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            need(count>4*scale*scale*sx*sy,"nonempty Core item "+i);need(minX>=3*scale*sx&&maxX<17*scale*sx&&minY>=3*scale*sy&&maxY<17*scale*sy,"projected icon padding "+i+" scale="+scale+" bounds="+minX+","+minY+".."+maxX+","+maxY);need(maxX-minX+1>11*scale*sx,"recognizable inventory width "+i+" scale="+scale);need(count<(maxX-minX+1)*(maxY-minY+1)*.7,"visible scaffold negative space "+i);
        }need(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"Core item GL state");System.out.println("[vandorlabs][builder-render] core-icons PASS actual3 ItemBlocks enlarged4x/ordinary1x recognizable width projected padding and visible negative space");}
    }
    private static void need(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
