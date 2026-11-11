package com.vandorlabs.client;

import com.vandorlabs.blocks.ProgrammableStairGeometry;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Emit only the exposed rectangles of the same height field used for collision. */
final class ProgrammableStairMesh {
    static List<BakedQuad> quads(IBlockState state,int settings,TextureAtlasSprite sprite){
        return quads(state,settings,sprite,new VertexFormat(DefaultVertexFormats.ITEM));
    }
    static List<BakedQuad> quads(IBlockState state,int settings,TextureAtlasSprite sprite,VertexFormat format){
        return com.vandorlabs.render.StairGeometry.mesh(state.getValue(BlockStairs.FACING).getIndex(),state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP,ProgrammableStairGeometry.shape(state.getValue(BlockStairs.SHAPE)),settings,ProgrammableStairGeometry.POINTS,(a,b,c,d,ta,tb,tc,td,side,textureSide)->face(sprite,format,a,b,c,d,ta,tb,tc,td,EnumFacing.getFront(side),EnumFacing.getFront(textureSide)));
    }
    private static BakedQuad face(TextureAtlasSprite sprite,VertexFormat format,Vec3d a,Vec3d b,Vec3d c,Vec3d d,Vec3d ta,Vec3d tb,Vec3d tc,Vec3d td,EnumFacing face,EnumFacing textureFace){
        int[] data=new int[28];for(int i=0;i<4;i++){
            Vec3d p=i==0?a:i==1?b:i==2?c:d,uv=i==0?ta:i==1?tb:i==2?tc:td;int at=i*7;double u=textureFace.getAxis()==EnumFacing.Axis.X?uv.z:uv.x,v=textureFace.getAxis()==EnumFacing.Axis.Y?uv.z:1-uv.y;
            if(textureFace==EnumFacing.EAST || textureFace==EnumFacing.NORTH)u=1-u;
            if(textureFace==EnumFacing.DOWN)v=1-v;
            data[at]=Float.floatToRawIntBits((float)p.x);data[at+1]=Float.floatToRawIntBits((float)p.y);data[at+2]=Float.floatToRawIntBits((float)p.z);data[at+3]=-1;
            data[at+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(u*16));data[at+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(v*16));
        }
        // Keep the released float arithmetic/winding normal exactly, then place those
        // fields into the generation's immutable format (including shader extensions).
        net.minecraftforge.client.ForgeHooksClient.fillNormal(data,face);
        int position=0;for(int element=0;element<format.getElementCount();element++)if(format.getElement(element).getUsage()==VertexFormatElement.EnumUsage.POSITION){position=format.getOffset(element)/4;break;}
        int stride=format.getIntegerSize(),color=format.getColorOffset()/4,uv=format.getUvOffsetById(0)/4,normal=format.getNormalOffset()/4;
        if(stride!=7||position!=0||color!=3||uv!=4||!format.hasNormal()||normal!=6){
            int[] expanded=new int[4*stride];for(int vertex=0;vertex<4;vertex++){
                System.arraycopy(data,vertex*7,expanded,vertex*stride+position,3);
                if(format.hasColor())expanded[vertex*stride+color]=data[vertex*7+3];
                expanded[vertex*stride+uv]=data[vertex*7+4];expanded[vertex*stride+uv+1]=data[vertex*7+5];
                if(format.hasNormal())expanded[vertex*stride+normal]=data[vertex*7+6];
            }
            data=expanded;
        }
        return retainedQuad(data,-1,face,sprite,true,format);
    }
    /** Prevent OptiFine's global shader toggle from resizing a retired generation's records. */
    static BakedQuad retainedQuad(int[] data,int tint,EnumFacing face,TextureAtlasSprite sprite,boolean diffuse,VertexFormat format){
        if(data.length!=4*format.getIntegerSize())throw new IllegalArgumentException("Stair quad format and record width differ");
        return new BakedQuad(data,tint,face,sprite,diffuse,format){@Override public int[] getVertexData(){return data;}};
    }
}
