package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** All fourteen props must have closed, opaque sides and usable held artwork. */
final class DataTokenRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        String[] designs={"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"};
        BufferedImage montage=new BufferedImage(640*7,360*4,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D canvas=montage.createGraphics();
        BufferedImage closeups=new BufferedImage(256*14,256*4,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D views=closeups.createGraphics();
        try{for(int kind=0;kind<2;kind++)for(int i=0;i<7;i++){
            String type=kind==0?"access_card":"data_chip";ItemStack stack=new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+type)),1,i);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);Set<EnumFacing> faces=EnumSet.noneOf(EnumFacing.class);
            if(model.getQuads(null,null,0).size()<6||model.getQuads(null,null,0).size()>1024)throw new IllegalStateException("Token outline mesh missing or overly complex: "+type+designs[i]);
            if(!model.isGui3d())throw new IllegalStateException("Solid token model missing");
            int solidFaces=0;double minZ=1,maxZ=0;
            for(BakedQuad quad:model.getQuads(null,null,0)){
                if(quad.getSprite().getIconName().equals("vandorlabs:items/"+type+"_edge")){
                    solidFaces++;
                    for(int pixel:quad.getSprite().getFrameTextureData(0)[0])if((pixel>>>24)!=255)throw new IllegalStateException("Non-opaque token body "+type+designs[i]);
                }
                int[] vertexData=quad.getVertexData();int stride=vertexData.length/4;for(int v=0;v<4;v++){float z=Float.intBitsToFloat(vertexData[v*stride+2]);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
                faces.add(quad.getFace());
                if(quad.getFace()!=EnumFacing.NORTH&&quad.getFace()!=EnumFacing.SOUTH&&!quad.getSprite().getIconName().equals("vandorlabs:items/"+type+"_edge"))throw new IllegalStateException("Transparent token edge material "+type+designs[i]);
            }
            if(faces.size()!=6||solidFaces!=model.getQuads(null,null,0).size()-6)throw new IllegalStateException("Open token silhouette "+type+designs[i]);
            if(Math.abs(maxZ-minZ-1/16D)>1e-6)throw new IllegalStateException("Token gained block-like thickness");
            BufferedImage front=appearance(mc,stack,model,0),side=appearance(mc,stack,model,90);
            checkRectangle(front,kind==0?8.5:12,kind==0?14:12);checkRectangle(side,1,kind==0?14:12);
            for(int view=0;view<4;view++){BufferedImage shot=view==0?front:view==3?side:appearance(mc,stack,model,view==1?45:180);views.drawImage(shot,(kind*7+i)*256,view*256,null);}
            for(boolean left:new boolean[]{false,true}){
                BufferedImage held=WheelItemRuntimeChecks.held(mc,stack,left);int ink=0;long totalX=0,totalY=0;
                for(int x=0;x<held.getWidth();x++)for(int y=0;y<held.getHeight();y++)if((held.getRGB(x,y)&0xffffff)!=0x203040){ink++;totalX+=x;totalY+=y;}
                // Vanilla held items may extend beyond the lower screen edge.
                // They must remain visible beside the hand, rather than float in the centre.
                if(ink<300||ink>held.getWidth()*held.getHeight()*.18)throw new IllegalStateException("Unusable held token coverage "+type+designs[i]+" left="+left+" pixels="+ink);
                double cx=totalX/(double)ink,cy=totalY/(double)ink;
                if((left?cx>held.getWidth()*.35:cx<held.getWidth()*.65)||cy<held.getHeight()*.5)throw new IllegalStateException("Held token outside hand corner "+type+designs[i]+" left="+left+" centre="+cx+","+cy);
                canvas.drawImage(held,i*640,(kind+(left?2:0))*360,null);
                javax.imageio.ImageIO.write(held,"png",new File(output,"shot_"+type+"_"+designs[i]+(left?"_left":"")+"_held.png"));
            }
        }}finally{canvas.dispose();views.dispose();}
        javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_data_tokens_held.png"));
        javax.imageio.ImageIO.write(closeups,"png",new File(output,"shot_data_tokens_closeups.png"));
        System.out.println("[vandorlabs][reprolab] data-token-models PASS fourteen closed corner-clipped props rectangular fronts edge-on solid coverage one-unit depth opaque side material and both first-person hand corners");
    }
    private static void checkRectangle(BufferedImage image,double width,double height){
        int left=(int)Math.round(128-width*8),right=(int)Math.round(128+width*8),top=(int)Math.round(128-height*8),bottom=(int)Math.round(128+height*8);
        for(int y=2;y<254;y++)for(int x=2;x<254;x++){
            if(Math.abs(x-left)<3||Math.abs(x-right)<3||Math.abs(y-top)<3||Math.abs(y-bottom)<3)continue;
            boolean expected=x>left&&x<right&&y>top&&y<bottom,ink=(image.getRGB(x,y)&0xffffff)!=0x203040;
            if(expected!=ink)throw new IllegalStateException("Closed token rectangle differs at "+x+","+y+" dimensions "+width+"x"+height);
        }
    }
    private static BufferedImage appearance(Minecraft mc,ItemStack stack,IBakedModel model,float yaw){
        Framebuffer target=new Framebuffer(256,256,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.5,.5,-.5,.5,-10,10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{
            target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableCull();GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.rotate(yaw,0,1,0);mc.getRenderItem().renderItem(stack,model);GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,target);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }

}
