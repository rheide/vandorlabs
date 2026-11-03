package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import org.lwjgl.opengl.GL11;

final class FurnitureModelChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("chair model: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        BlockBridgeChair chair=(BlockBridgeChair)ModBlocks.PROGRAMMABLE_CHAIR;int cases=0;
        for(int style=0;style<5;style++)for(int height=0;height<3;height++)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            TileEntityProgrammableChair template=new TileEntityProgrammableChair();template.setStyle(style);template.setHeight(height);IBlockState state=chair.getDefaultState().withProperty(BlockBridgeChair.FACING,facing);
            VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,template.writeToNBT(new NBTTagCompound())))));TileEntityProgrammableChair tile=(TileEntityProgrammableChair)view.getTileEntity(BlockPos.ORIGIN);
            IBlockState actual=chair.getActualState(state,view,BlockPos.ORIGIN);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(actual);require(model instanceof ChairAngleModel,"chair model bake wrapper missing");List<BakedQuad> original=all(model,chair.getExtendedState(actual,view,BlockPos.ORIGIN));require(!original.isEmpty(),"authored chair invisible");
            for(int angle=0;angle<8;angle++){tile.setAngle(angle);IBlockState selected=chair.getExtendedState(actual,view,BlockPos.ORIGIN);List<BakedQuad> quads=all(model,selected);require(quads.size()==original.size(),"rotated chair lost faces");double a=Math.toRadians(angle*45),c=Math.cos(a),s=Math.sin(a);
                for(int q=0;q<quads.size();q++){int[] before=original.get(q).getVertexData(),after=quads.get(q).getVertexData();int stride=before.length/4;for(int v=0;v<4;v++){int i=v*stride;double x=Float.intBitsToFloat(before[i])-.5,z=Float.intBitsToFloat(before[i+2])-.5;require(Math.abs(Float.intBitsToFloat(after[i])-(.5+c*x-s*z))<2e-6&&Math.abs(Float.intBitsToFloat(after[i+2])-(.5+s*x+c*z))<2e-6&&before[i+1]==after[i+1],"actual emitted vertices do not match saved angle");}}
                if(angle!=0)require(model.getQuads(selected,null,0)==model.getQuads(selected,null,0),"per-frame chair rotation rebuilt mesh");
                if(facing==EnumFacing.SOUTH){ItemStack icon=new ItemStack(chair);NBTTagCompound data=template.writeToNBT(new NBTTagCompound());data.setInteger("ChairAngle",angle);icon.setTagInfo("BlockEntityTag",data);BufferedImage image=icon(mc,icon);int visible=0;for(int y=0;y<40;y++)for(int x=0;x<40;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040){visible++;require(x>=13&&x<=26&&y>=13&&y<=26,"configured chair icon lost slot padding style="+style+" height="+height+" angle="+angle+" x="+x+" y="+y);}require(visible>10,"configured chair icon invisible");if(style==0&&height==1)javax.imageio.ImageIO.write(image,"png",new File(output,"chair-angle-icon-"+angle+".png"));}
                cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] chair-angle-model PASS cases="+cases+" exact-emitted-45degree-vertices legacy0 cached-quads all5styles/3heights/4facings item-padding120");
    }
    private static List<BakedQuad> all(IBakedModel model,IBlockState state){List<BakedQuad> result=new ArrayList<>();result.addAll(model.getQuads(state,null,0));for(EnumFacing side:EnumFacing.values())result.addAll(model.getQuads(state,side,0));return result;}
    private static BufferedImage icon(Minecraft mc,ItemStack stack){Framebuffer frame=new Framebuffer(40,40,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,40,40,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
        frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(stack,12,12);RenderHelper.disableStandardItemLighting();GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(40,40,frame);
    }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}}
    private FurnitureModelChecks(){}
}
