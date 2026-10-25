package com.vandorlabs.client;

import com.vandorlabs.render.StairGeometry;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** Native atlas/buffer boundary for the shared exposed stair mesh and unrolled UVs. */
final class ProgrammableStairMesh {
    static List<BakedQuad> quads(IBlockState state,int settings,TextureAtlasSprite sprite){
        List<BakedQuad> result=new ArrayList<>();
        for(StairGeometry.Face face:StairGeometry.mesh(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,StairGeometry.Shape.valueOf(state.getValue(BlockStairs.SHAPE).name()),settings)){
            int[] data=new int[28];for(int i=0;i<4;i++){int at=i*7;com.vandorlabs.render.ControlMount.Point p=face.vertices[i];data[at]=Float.floatToRawIntBits((float)p.x);data[at+1]=Float.floatToRawIntBits((float)p.y);data[at+2]=Float.floatToRawIntBits((float)p.z);data[at+3]=-1;data[at+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(face.u[i]));data[at+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(face.v[i]));}
            EnumFacing side=EnumFacing.getFront(face.face);net.minecraftforge.client.ForgeHooksClient.fillNormal(data,side);result.add(new BakedQuad(data,-1,side,sprite,true,DefaultVertexFormats.ITEM));
        }return result;
    }
}
