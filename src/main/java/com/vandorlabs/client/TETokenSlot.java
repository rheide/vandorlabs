package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** The slot contains a real token model, or a small allowed-company colour panel. */
public final class TETokenSlot extends TileEntitySpecialRenderer<TileEntityTokenSlot> {
    private static final int[] COLORS={0xc4c8cb,0xe9a53b,0x42a9e8,0xb7a6db,0xb53946,0x5bd0ad,0xf0bc50};
    @Override public void render(TileEntityTokenSlot tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!(tile.getBlockType() instanceof BlockTokenSlot))return;
        net.minecraft.block.state.IBlockState state=tile.getBlockType().getActualState(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos());EnumFacing facing=state.getValue(BlockVandorSwitch.FACING);int rotation=state.getValue(BlockVandorSwitch.ROTATION);
        GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableCull();GlStateManager.disableLighting();
            int light=tile.getWorld().getCombinedLight(tile.getPos().offset(facing),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
            if(tile.isEmpty()){
                GlStateManager.disableTexture2D();double half=(tile.chip()?2.5:3.5)/16;
                if(tile.any)panel(facing,rotation,-half,half,-half,half,COLORS[0]);
                else{int count=Integer.bitCount(tile.allowed&126),index=0;boolean plain=(tile.allowed&1)!=0;double bottom=plain&&count>0?-half+.025:-half+.006;if(plain)panel(facing,rotation,-half,half,-half,count>0?bottom-.003:half,COLORS[0]);for(int i=1;i<7;i++)if((tile.allowed&(1<<i))!=0){double a=-half+2*half*index/count,b=-half+2*half*(++index)/count;panel(facing,rotation,a+.003,b-.003,bottom,half-.006,COLORS[i]);}}
                GlStateManager.enableTexture2D();
            }else{
                Vec3d center=TokenSlotGeometry.point(facing,rotation,0,0,.075),u=TokenSlotGeometry.point(facing,rotation,-1,0,.075).subtract(center),v=TokenSlotGeometry.point(facing,rotation,0,1,.075).subtract(center),n=new Vec3d(facing.getDirectionVec());
                java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.put(new float[]{(float)u.x,(float)u.y,(float)u.z,0,(float)v.x,(float)v.y,(float)v.z,0,(float)n.x,(float)n.y,(float)n.z,0,(float)center.x,(float)center.y,(float)center.z,1}).flip();GlStateManager.multMatrix(matrix);double scale=tile.chip()?.35:.5;GlStateManager.scale(scale,scale,scale);GlStateManager.color(1,1,1,1);
                Minecraft.getMinecraft().getRenderItem().renderItem(tile.getStackInSlot(0),ItemCameraTransforms.TransformType.FIXED);
            }
        }finally{GlStateManager.enableTexture2D();GlStateManager.popMatrix();}
    }
    private static void panel(EnumFacing face,int rotation,double left,double right,double bottom,double top,int color){
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_COLOR);
        for(double[] corner:new double[][]{{left,bottom},{right,bottom},{right,top},{left,top}}){Vec3d p=TokenSlotGeometry.point(face,rotation,corner[0],corner[1],.035);buffer.pos(p.x,p.y,p.z).color(color>>16&255,color>>8&255,color&255,255).endVertex();}Tessellator.getInstance().draw();
    }
}
