package com.vandorlabs.client;

import com.vandorlabs.appliance.ApplianceCatalog;
import com.vandorlabs.items.AppliancePresets;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

final class ApplianceVisualChecks {
    private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Appliance visual: "+why);}
    private static void fullFaceUV(BakedQuad quad){
        net.minecraft.client.renderer.vertex.VertexFormat format=quad.getFormat();int uv=-1;for(int i=0;i<format.getElementCount();i++)if(format.getElement(i).getUsage()==net.minecraft.client.renderer.vertex.VertexFormatElement.EnumUsage.UV&&format.getElement(i).getIndex()==0)uv=i;need(uv>=0,"UV field");float[] values=new float[4];double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
        for(int vertex=0;vertex<4;vertex++){net.minecraftforge.client.model.pipeline.LightUtil.unpack(quad.getVertexData(),values,format,vertex,uv);double u=quad.getSprite().getUnInterpolatedU(values[0]),v=quad.getSprite().getUnInterpolatedV(values[1]);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);}need(Math.abs(maxU-minU-16)<.01&&Math.abs(maxV-minV-16)<.01,"whole authored face retained, including slab sides "+(maxU-minU)+"x"+(maxV-minV));
    }
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(46,90,40);
        for(int i=0;i<4;i++){
            Block block=i<2?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK;IBlockState state=block.getDefaultState();mc.world.setBlockToAir(pos);mc.world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.merge(AppliancePresets.stack(i).getSubCompound("BlockEntityTag"));tile.readFromNBT(saved);IBlockState extended=block.getExtendedState(state,mc.world,pos);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);Set<String> sprites=new HashSet<>();
            for(int f=-1;f<6;f++)for(BakedQuad quad:model.getQuads(extended,f<0?null:EnumFacing.getFront(f),0)) {sprites.add(quad.getSprite().getIconName());fullFaceUV(quad); }for(int f=0;f<6;f++)need(sprites.contains("vandorlabs:blocks/"+ApplianceCatalog.texture(i,f)),"actual programmable world face "+i+" "+f+" "+sprites);mc.world.setBlockToAir(pos);
        }
        try(TurntableRenderState ignored=new TurntableRenderState()){
            for(int i=0;i<6;i++)for(int scale:new int[]{1,4}){
                ItemStack stack=i<4?AppliancePresets.stack(i):new ItemStack(Block.getBlockFromName("vandorlabs:"+ApplianceCatalog.IDS[i]));Framebuffer fb=new Framebuffer(20*scale,20*scale,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,20,20,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
                try{fb.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(13/255F,17/255F,24/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableAlpha();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(stack,2,2);GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(20*scale,20*scale,fb);int minX=20*scale,minY=minX,maxX=-1,maxY=-1,count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)!=0x0d1118){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);count++;}need(count>12*scale*scale,"nonempty icon");need(minX>=3*scale&&minY>=3*scale&&maxX<17*scale&&maxY<17*scale,"all-side inventory padding "+i+" "+scale+" "+minX+","+minY+".."+maxX+","+maxY);javax.imageio.ImageIO.write(image,"png",new File(output,"appliance-icon-"+i+"-"+scale+".png"));}
                finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
            }
        }
        System.out.println("[vandorlabs] appliances-visual PASS actual per-face world meshes all6itemmodels padded1x4xicons");
    }
}
