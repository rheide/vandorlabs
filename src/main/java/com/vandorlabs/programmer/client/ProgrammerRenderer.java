package com.vandorlabs.programmer.client;

import com.vandorlabs.programmer.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/** Cached casing plus the actual retained inventory tokens, using the exported dock poses. */
public final class ProgrammerRenderer extends TileEntitySpecialRenderer<ProgrammerTile> {
    @Override public void render(ProgrammerTile tile,double x,double y,double z,float partial,int stage,float alpha){
        ProgrammerModelCache cache=ProgrammerModelCache.INSTANCE;if(!tile.isProgrammerController()||!cache.isReady())return;
        BlockPos origin=tile.getProgrammerOrigin();x+=origin.getX()-tile.getPos().getX();y+=origin.getY()-tile.getPos().getY();z+=origin.getZ()-tile.getPos().getZ();
        Minecraft mc=Minecraft.getMinecraft();GlStateManager.pushMatrix();try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
            int light=tile.getWorld().getCombinedLight(origin.up(),0);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,cache.format);
            ProgrammerRenderHelper.append(buffer,cache.definition(),tile.getProgrammerStatus(),tile.getProgrammerFacing(),0,x,y,z,light,alpha);Tessellator.getInstance().draw();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
            ProgrammerTransform.Affine a=ProgrammerTransform.of(ProgrammerRenderHelper.facing(tile.getProgrammerFacing()),2,1);
            for(int slot=0;slot<3;slot++){
                ItemStack item=tile.getStackInSlot(slot);if(item.isEmpty())continue;
                boolean chip=item.getItem()==ModItems.DATA_CHIP;
                // The native square chip uses the same socket mouth as the longer sample card.
                double cx=slot==0?(chip?14.54:16.3):slot==1?28:4,cz=slot==0?10.05:chip?10.26:8.75;
                GlStateManager.pushMatrix();try{
                    GlStateManager.translate(x+a.x(cx/16,cz/16),y+16.28/16,z+a.z(cx/16,cz/16));
                    GlStateManager.rotate(-tile.getProgrammerFacing().getHorizontalAngle()+180,0,1,0);GlStateManager.rotate(90,1,0,0);if(slot==0)GlStateManager.rotate(90,0,0,1);
                    double scale=chip?.29:.45;GlStateManager.scale(scale,scale,.45);mc.getRenderItem().renderItem(item,ItemCameraTransforms.TransformType.FIXED);
                }finally{GlStateManager.popMatrix();}
            }
        }finally{GlStateManager.popMatrix();}
    }
}
