package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;

/** All three wheel items through real GUI and first-person model transforms. */
final class WheelItemRuntimeChecks {
    private static final String[] SIZES={"small","medium","large"};private static final int BACK=0x203040;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("wheel items: "+text);}
    private static ItemStack stack(int kind){return new ItemStack(Block.getBlockFromName("vandorlabs:wheel_"+SIZES[kind]));}
    static void run(Minecraft mc,File output)throws Exception{
        double previous=0;
        for(int kind=0;kind<3;kind++){
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack(kind),mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(EnumFacing face:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,face,0)){
                int[] data=quad.getVertexData();int stride=data.length/4;
                for(int vertex=0;vertex<4;vertex++){Vec3d point=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[vertex*stride]),Float.intBitsToFloat(data[vertex*stride+1]),Float.intBitsToFloat(data[vertex*stride+2])),gui).addVector(gui.translation.x,gui.translation.y,0);minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);}
            }
            require(Math.abs(minX)<.44&&Math.abs(maxX)<.44&&Math.abs(minY)<.44&&Math.abs(maxY)<.44,"every projected edge padded "+SIZES[kind]);
            double span=Math.max(maxX-minX,maxY-minY);require(span>previous+.08,"Small < Medium < Large projected icon size "+SIZES[kind]);previous=span;
            for(ItemTransformVec3f held:new ItemTransformVec3f[]{model.getItemCameraTransforms().firstperson_right,model.getItemCameraTransforms().firstperson_left,model.getItemCameraTransforms().thirdperson_right,model.getItemCameraTransforms().thirdperson_left})require(held.scale.x<=.33&&held.scale.y<=.33&&held.scale.z<=.33,"explicit reduced held transform "+SIZES[kind]);
            if(kind==2)require(model.getItemCameraTransforms().firstperson_right.scale.x<=.19,"Large held geometry compensates for two-block diameter");
            BufferedImage held=held(mc,stack(kind));int changed=0;for(int y=0;y<held.getHeight();y++)for(int x=0;x<held.getWidth();x++)if((held.getRGB(x,y)&0xffffff)!=BACK)changed++;
            require(changed>100&&changed<held.getWidth()*held.getHeight()*.18,"first-person wheel visible without filling screen "+SIZES[kind]+" pixels="+changed);
            javax.imageio.ImageIO.write(held,"png",new File(output,"shot_wheel_held_"+SIZES[kind]+".png"));
        }
        GuiScreen previousGui=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale,previousSpan=0;
            for(int kind=0;kind<3;kind++){
                int x=icons.x(kind)*scale,y=icons.y()*scale,minX=size,minY=size,maxX=-1,maxY=-1;
                for(int offset=0;offset<size;offset++){require((shot.getRGB(x+offset,y)&0xffffff)==BACK&&(shot.getRGB(x+offset,y+size-1)&0xffffff)==BACK,"top and bottom padding "+SIZES[kind]);require((shot.getRGB(x,y+offset)&0xffffff)==BACK&&(shot.getRGB(x+size-1,y+offset)&0xffffff)==BACK,"left and right padding "+SIZES[kind]);}
                for(int a=0;a<size;a++)for(int b=0;b<size;b++)if((shot.getRGB(x+a,y+b)&0xffffff)!=BACK){minX=Math.min(minX,a);maxX=Math.max(maxX,a);minY=Math.min(minY,b);maxY=Math.max(maxY,b);}
                int span=Math.max(maxX-minX+1,maxY-minY+1);require(span>=previousSpan+scale,"actual rendered wheel icon sizes increase "+SIZES[kind]+" previous="+previousSpan+" span="+span);previousSpan=span;
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_wheel_item_sizes.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previousGui);}
        System.out.println("[vandorlabs][reprolab] wheel-item-sizes PASS small-medium-large projected-size actual-pixel-size every-edge-padding reduced-first-third-person held-screenshots");
    }
    static BufferedImage held(Minecraft mc,ItemStack stack){return held(mc,stack,false);}
    static BufferedImage held(Minecraft mc,ItemStack stack,boolean left){
        Framebuffer target=new Framebuffer(640,360,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();org.lwjgl.util.glu.Project.gluPerspective(70,640F/360,.05F,100);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{
            target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);RenderHelper.enableStandardItemLighting();
            // Vanilla's resting right-hand placement before the item's authored transform.
            GlStateManager.translate(left?-.56F:.56F,-.52F,-.72F);mc.getRenderItem().renderItem(stack,mc.player,left?ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND:ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,left);
            GL11.glFinish();return ScreenShotHelper.createScreenshot(640,360,target);
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static final class Icons extends GuiScreen{
        int x(int kind){return width/2+(kind-1)*80-8;}int y(){return height/2-8;}public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int x,int y,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Wheels: standard 16px inventory slots",width/2,30,0xffffff);for(int kind=0;kind<3;kind++){drawRect(x(kind)-1,y()-1,x(kind)+17,y()+17,0xff708090);drawRect(x(kind),y(),x(kind)+16,y()+16,0xff000000|BACK);}RenderHelper.enableGUIStandardItemLighting();for(int kind=0;kind<3;kind++){GlStateManager.color(1,1,1,1);itemRender.renderItemAndEffectIntoGUI(stack(kind),x(kind),y());}RenderHelper.disableStandardItemLighting();for(int kind=0;kind<3;kind++)drawCenteredString(fontRenderer,SIZES[kind],x(kind)+8,y()+24,0xffffff);}
    }
    private WheelItemRuntimeChecks(){}
}
