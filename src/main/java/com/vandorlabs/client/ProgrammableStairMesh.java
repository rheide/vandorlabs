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
        int count=16/(settings&31),roll=settings>>5;List<BakedQuad> result=new ArrayList<>();
        BlockStairs.EnumShape shape=state.getValue(BlockStairs.SHAPE);
        for(int x=0;x<count;x++)for(int z=0;z<count;z++){
            double x0=x/(double)count,x1=(x+1D)/count,z0=z/(double)count,z1=(z+1D)/count,h=ProgrammableStairGeometry.height(x,z,count,shape);
            face(result,state,roll,sprite,new double[][]{{x0,h,z0},{x0,h,z1},{x1,h,z1},{x1,h,z0}});
            face(result,state,roll,sprite,new double[][]{{x0,0,z0},{x1,0,z0},{x1,0,z1},{x0,0,z1}});
            double left=x==0?0:ProgrammableStairGeometry.height(x-1,z,count,shape),right=x==count-1?0:ProgrammableStairGeometry.height(x+1,z,count,shape);
            double north=z==0?0:ProgrammableStairGeometry.height(x,z-1,count,shape),south=z==count-1?0:ProgrammableStairGeometry.height(x,z+1,count,shape);
            if(h>left)face(result,state,roll,sprite,new double[][]{{x0,h,z0},{x0,left,z0},{x0,left,z1},{x0,h,z1}});
            if(h>right)face(result,state,roll,sprite,new double[][]{{x1,h,z0},{x1,h,z1},{x1,right,z1},{x1,right,z0}});
            if(h>north)face(result,state,roll,sprite,new double[][]{{x0,h,z0},{x1,h,z0},{x1,north,z0},{x0,north,z0}});
            if(h>south)face(result,state,roll,sprite,new double[][]{{x0,h,z1},{x0,south,z1},{x1,south,z1},{x1,h,z1}});
        }return result;
    }
    private static void face(List<BakedQuad> result,IBlockState state,int roll,TextureAtlasSprite sprite,double[][] corners){
        Vec3d[] vertices=new Vec3d[4],textureVertices=new Vec3d[4];for(int i=0;i<4;i++){Vec3d local=new Vec3d(corners[i][0],corners[i][1],corners[i][2]);vertices[i]=ProgrammableStairGeometry.transform(local,state,roll);textureVertices[i]=ProgrammableStairGeometry.transform(local,state,0);}
        // Top-half reflection reverses winding.
        if(state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP){Vec3d swap=vertices[1];vertices[1]=vertices[3];vertices[3]=swap;swap=textureVertices[1];textureVertices[1]=textureVertices[3];textureVertices[3]=swap;}
        Vec3d normal=vertices[1].subtract(vertices[0]).crossProduct(vertices[2].subtract(vertices[0])).normalize();EnumFacing face=EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z);
        Vec3d textureNormal=textureVertices[1].subtract(textureVertices[0]).crossProduct(textureVertices[2].subtract(textureVertices[0]));EnumFacing textureFace=EnumFacing.getFacingFromVector((float)textureNormal.x,(float)textureNormal.y,(float)textureNormal.z);
        int[] data=new int[28];for(int i=0;i<4;i++){
            Vec3d p=vertices[i],uv=textureVertices[i];int at=i*7;double u=textureFace.getAxis()==EnumFacing.Axis.X?uv.z:uv.x,v=textureFace.getAxis()==EnumFacing.Axis.Y?uv.z:1-uv.y;
            if(textureFace==EnumFacing.EAST || textureFace==EnumFacing.NORTH)u=1-u;
            if(textureFace==EnumFacing.DOWN)v=1-v;
            data[at]=Float.floatToRawIntBits((float)p.x);data[at+1]=Float.floatToRawIntBits((float)p.y);data[at+2]=Float.floatToRawIntBits((float)p.z);data[at+3]=-1;
            data[at+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(u*16));data[at+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(v*16));
        }
        net.minecraftforge.client.ForgeHooksClient.fillNormal(data,face);
        result.add(new BakedQuad(data,-1,face,sprite,true,DefaultVertexFormats.ITEM));
    }
}
