package com.vandorlabs.client;

import com.vandorlabs.blocks.ProgrammableStairGeometry;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Emit only the exposed rectangles of the same height field used for collision. */
final class ProgrammableStairMesh {
    static List<BakedQuad> quads(IBlockState state,int settings,TextureAtlasSprite sprite){
        return com.vandorlabs.render.StairGeometry.mesh(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,ProgrammableStairGeometry.shape(state.getValue(BlockStairs.SHAPE)),settings,ProgrammableStairGeometry.POINTS,(a,b,c,d,ta,tb,tc,td,side,textureSide)->face(sprite,a,b,c,d,ta,tb,tc,td,EnumFacing.getFront(side),EnumFacing.getFront(textureSide)));
    }
    private static BakedQuad face(TextureAtlasSprite sprite,Vec3d a,Vec3d b,Vec3d c,Vec3d d,Vec3d ta,Vec3d tb,Vec3d tc,Vec3d td,EnumFacing face,EnumFacing textureFace){
        int[] data=new int[28];for(int i=0;i<4;i++){
            Vec3d p=i==0?a:i==1?b:i==2?c:d,uv=i==0?ta:i==1?tb:i==2?tc:td;int at=i*7;double u=textureFace.getAxis()==EnumFacing.Axis.X?uv.z:uv.x,v=textureFace.getAxis()==EnumFacing.Axis.Y?uv.z:1-uv.y;
            if(textureFace==EnumFacing.EAST || textureFace==EnumFacing.NORTH)u=1-u;
            if(textureFace==EnumFacing.DOWN)v=1-v;
            data[at]=Float.floatToRawIntBits((float)p.x);data[at+1]=Float.floatToRawIntBits((float)p.y);data[at+2]=Float.floatToRawIntBits((float)p.z);data[at+3]=-1;
            data[at+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(u*16));data[at+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(v*16));
        }
        net.minecraftforge.client.ForgeHooksClient.fillNormal(data,face);
        return new BakedQuad(data,-1,face,sprite,true,DefaultVertexFormats.ITEM);
    }
}
